package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【生命之星】事件处理器
//
// 核心效果（手持 + 盔甲嵌入均生效）：
//   1. 生命值上限：20 → 50 (增加 30 点)
//   2. 回血速度：×2 倍（自然恢复加速）
//   3. 饥饿值与饱和度下降速度 −50%
//      · 移动/跳跃/挖掘/攻击等消耗 → 由 PlayerFoodExhaustionMixin 把参数减半
//      · 原版自然回血消耗的饱和度 → 由 FoodDataLifeStarMixin 把参数减半
//      · 本类追加的 ×2 回血所消耗的饱和度 → 在 applyRegenerationBoost 中直接 ×0.5
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LifeStarHandler {

    // 属性修饰符 UUID
    private static final UUID MAX_HEALTH_UUID = 
            UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
    private static final UUID REGENERATION_UUID = 
            UUID.fromString("f1e2d3c4-b5a6-7890-abcd-ef9876543210");

    // 回血加成的独立计时器（每玩家一个，复刻原版 FoodData.tickTimer 的节奏）
    private static final Map<UUID, Integer> REGEN_TIMER = new HashMap<>();

    // ========================================================================
    // 【PlayerTickEvent】检查手持/穿戴状态，应用属性修饰符
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;

        // 【仅服务端处理】
        //   属性修饰符由服务端计算后自动同步给客户端（ClientboundUpdateAttributesPacket）。
        //   若不加这个判断，客户端玩家 tick 会走进下面的 else 分支，
        //   把服务端同步过来的 MAX_HEALTH 修饰符在本地删掉，
        //   导致客户端血条仍显示 20 上限（10 颗心），看起来像"效果没生效"。
        if (player.level().isClientSide()) return;

        boolean hasLifeStar = isHoldingOrWearingLifeStar(player);

        if (hasLifeStar) {
            // 应用生命值上限修饰符（+30）
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null) {
                if (maxHealthAttr.getModifier(MAX_HEALTH_UUID) == null) {
                    maxHealthAttr.addPermanentModifier(new AttributeModifier(
                            MAX_HEALTH_UUID, "LifeStar Max Health", 30.0,
                            AttributeModifier.Operation.ADDITION));
                    // 治疗满血（同步新上限）
                    player.setHealth((float) Math.min(player.getHealth() + 30, maxHealthAttr.getValue()));
                }
            }

            // 应用回血速度修饰符（×2 倍）
            // 原版通过 RegenrationModifier 实现，这里通过属性叠加
            // 由于原版回血机制是内置的，我们在 PlayerTickEvent 中直接治疗
            applyRegenerationBoost(player);
        } else {
            // 移除修饰符（放下生命之星）
            AttributeInstance maxHealthAttr = player.getAttribute(Attributes.MAX_HEALTH);
            if (maxHealthAttr != null && maxHealthAttr.getModifier(MAX_HEALTH_UUID) != null) {
                maxHealthAttr.removeModifier(MAX_HEALTH_UUID);
                // 如果当前生命值超过新上限，缩减至新上限
                if (player.getHealth() > maxHealthAttr.getValue()) {
                    player.setHealth((float) maxHealthAttr.getValue());
                }
            }
        }
    }

    // ========================================================================
    // 工具方法：回血速度加成（×2）
    //
    // 原理：原版自然恢复写在 FoodData.tick(Player) 里，共两条分支：
    //   ① 饱食度 ≥20 且饱和度 >0 → 每 10 tick 回复 min(饱和度, 6) / 6 点
    //   ② 饱食度 ≥18             → 每 80 tick 回复 1.0 点
    // 这里用独立计时器复刻同一节奏，在同样的时机"再补一次"等量治疗，
    // 于是总回复量恰好是原版的 2 倍。
    //
    // 说明：不直接用 player.tickCount % N 来判断，因为原版计时器在条件不满足时
    //       会归零；若用全局 tick 取模，玩家刚满足条件的那个 tick 就可能立刻回血，
    //       节奏会比 ×2 更快。
    // ========================================================================
    private static void applyRegenerationBoost(Player player) {
        // 自然恢复被游戏规则关闭时加成同样不生效（不能绕过 doNaturalRegeneration）
        if (!player.level().getGameRules().getBoolean(GameRules.RULE_NATURAL_REGENERATION)) {
            return;
        }

        // 满血时原版不会回复，加成也应跳过（否则白耗饥饿值）
        if (player.getHealth() >= player.getMaxHealth()) {
            return;
        }

        FoodData food = player.getFoodData();
        int foodLevel = food.getFoodLevel();
        float saturation = food.getSaturationLevel();

        UUID id = player.getUUID();
        int timer = REGEN_TIMER.getOrDefault(id, 0) + 1;

        if (foodLevel >= 20 && saturation > 0.0F) {
            // 对应原版分支 ①：每 10 tick 追加一次等量治疗
            if (timer >= 10) {
                float satCost = Math.min(saturation, 6.0F);  // 原版公式上限为 6
                player.heal(satCost / 6.0F);
                // 【平衡】与原版同等消耗饱和度。若不消耗，加成部分等于"免费回血"，
                //        实际强度会远超 ×2，属于严重超模。
                // 【饥饿减缓】回血所消耗的饱和度同样享受 −50%
                food.addExhaustion(satCost * 0.5F);
                timer = 0;
            }
        } else if (foodLevel >= 18) {
            // 对应原版分支 ②：每 80 tick 追加一次等量治疗
            if (timer >= 80) {
                player.heal(1.0F);
                // 【饥饿减缓】回血所消耗的饱和度同样享受 −50%（原版 6.0 → 3.0）
                food.addExhaustion(3.0F);
                timer = 0;
            }
        } else {
            // 不满足自然恢复条件 → 计时器归零（与原版 FoodData 行为保持一致）
            timer = 0;
        }

        REGEN_TIMER.put(id, timer);
    }

    // ========================================================================
    // 【PlayerLoggedOutEvent】清理回血计时器，避免玩家退出后残留数据
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        REGEN_TIMER.remove(event.getEntity().getUUID());
    }

    // ========================================================================
    // 工具方法：检查玩家是否手持或穿戴生命之星
    //
    // 【注意】除本类的 PlayerTickEvent 外，两个 Mixin
    //   （PlayerFoodExhaustionMixin / FoodDataLifeStarMixin）也会调用它，
    //   因此可见性必须是 public。
    // ========================================================================
    public static boolean isHoldingOrWearingLifeStar(Player player) {
        // 检查手持
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.is(ModItems.LIFE_STAR.get()) || offHand.is(ModItems.LIFE_STAR.get())) {
            return true;
        }

        // 检查盔甲嵌入
        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (!armorPiece.isEmpty() && armorPiece.hasTag()
                    && "life_star".equals(armorPiece.getTag().getString("EmbeddedStar"))) {
                return true;
            }
        }

        return false;
    }
}
