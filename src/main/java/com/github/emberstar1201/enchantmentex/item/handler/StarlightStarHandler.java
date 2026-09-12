package com.github.emberstar1201.enchantmentex.item.handler;

import com.github.emberstar1201.enchantmentex.item.ModItems;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【星辉之星】事件处理器
//
// 核心效果（手持 + 盔甲嵌入均生效）：
//   1. 夜间移动速度 +20% —— PlayerTickEvent 增删 MOVEMENT_SPEED 修饰符
//   2. 夜间夜视         —— PlayerTickEvent 刷新 NIGHT_VISION 状态效果
//   3. 击杀经验 ×2      —— LivingExperienceDropEvent 把掉落经验翻倍
//
// 【仅服务端处理】
//   属性修饰符由服务端计算后通过 ClientboundUpdateAttributesPacket 同步给客户端。
//   若客户端也执行 tick 逻辑，会走进"移除修饰符"分支把同步过来的加成删掉，
//   表现为"移速加成看起来没生效"，因此必须加 level().isClientSide() 判断。
//
// 【夜间判定】
//   下界/末地 hasFixedTime() = true（无昼夜循环），不享受加成。
//   主世界取 dayTime % 24000，13000 ~ 23000 为夜晚（与原版睡眠判定区间一致）。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class StarlightStarHandler {

    /** 夜间移速修饰符固定 UUID（瞬态修饰符，随昼夜增删） */
    private static final UUID NIGHT_SPEED_UUID =
            UUID.fromString("b2d4f6a8-1c3e-4d5f-9a7b-2e4c6f8a0b1d");

    /** 夜间移速加成：MULTIPLY_TOTAL 0.2 = 最终移速 ×1.2 */
    private static final double NIGHT_SPEED_BONUS = 0.20D;

    /** 夜视持续时间（tick），配合下面"快到期才续杯"的逻辑避免每 tick 发包 */
    private static final int NIGHT_VISION_DURATION = 300;
    private static final int NIGHT_VISION_REFRESH_THRESHOLD = 220;

    // ========================================================================
    // 【PlayerTickEvent】夜间移速 + 夜视
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        if (player.level().isClientSide()) return;

        boolean active = isHoldingOrWearingStarlightStar(player) && isNightTime(player.level());

        // ---------- 效果 1：夜间移速 +20% ----------
        AttributeInstance speedAttr = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) {
            if (active) {
                if (speedAttr.getModifier(NIGHT_SPEED_UUID) == null) {
                    speedAttr.addTransientModifier(new AttributeModifier(
                            NIGHT_SPEED_UUID, "Starlight Night Speed", NIGHT_SPEED_BONUS,
                            AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
            } else if (speedAttr.getModifier(NIGHT_SPEED_UUID) != null) {
                // 天亮了 / 放下了星辉之星 → 立即移除加成
                speedAttr.removeModifier(NIGHT_SPEED_UUID);
            }
        }

        // ---------- 效果 2：夜间夜视 ----------
        // 只在"没有夜视"或"剩余时间不足"时补一次，避免每 tick 都发状态包。
        if (active) {
            MobEffectInstance current = player.getEffect(MobEffects.NIGHT_VISION);
            if (current == null || current.getDuration() <= NIGHT_VISION_REFRESH_THRESHOLD) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION, NIGHT_VISION_DURATION, 0,
                        false,  // ambient：非信标/潮涌核心来源
                        false,  // visible：不显示粒子
                        true));  // showIcon：保留 HUD 图标
            }
        }
    }

    // ========================================================================
    // 【LivingExperienceDropEvent】击杀生物经验掉落 ×2
    //
    // 说明：本事件只覆盖"击杀生物"掉落的经验球，不包含挖矿/钓鱼/烧炼的经验。
    //       与「经验馈赠」附魔（+50%）可叠加，最终约为原版的 3 倍。
    // ========================================================================
    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player killer = event.getAttackingPlayer();
        if (killer == null) return;
        if (killer.level().isClientSide()) return;
        if (!isHoldingOrWearingStarlightStar(killer)) return;

        int originalXp = event.getDroppedExperience();
        if (originalXp <= 0) return;

        event.setDroppedExperience(originalXp * 2);
    }

    // ========================================================================
    // 工具方法：夜间判定（下界/末地无昼夜，恒为白天）
    // ========================================================================
    private static boolean isNightTime(Level level) {
        if (level.dimensionType().hasFixedTime()) {
            return false;
        }
        long time = level.getDayTime() % 24000L;
        return time >= 13000L && time < 23000L;
    }

    // ========================================================================
    // 工具方法：检查玩家是否手持或穿戴星辉之星
    // ========================================================================
    public static boolean isHoldingOrWearingStarlightStar(Player player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        if (mainHand.is(ModItems.STARLIGHT_STAR.get()) || offHand.is(ModItems.STARLIGHT_STAR.get())) {
            return true;
        }

        for (ItemStack armorPiece : player.getArmorSlots()) {
            if (!armorPiece.isEmpty() && armorPiece.hasTag()
                    && "starlight_star".equals(armorPiece.getTag().getString("EmbeddedStar"))) {
                return true;
            }
        }

        return false;
    }
}
