package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.LootTableLoadEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.github.emberstar1201.enchantmentex.Config;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【终末将至】附魔事件处理器
//
// 效果（配置值见 Config.java）：
//   1. 伤害提升：LivingHurtEvent 中按配置倍率提升伤害
//   2. 攻击冷却缩短：PlayerTickEvent 中动态增删 ATTACK_SPEED AttributeModifier
//      使用 MULTIPLY_TOTAL 操作，攻速加成由配置决定
//   3. 末影人不以持有者为目标
//
// 修饰符管理（参考 DawnHandler 模式）：
//   - 固定 UUID，addTransientModifier 自动覆盖同 UUID 旧值
//   - 仅在武器切换时增删，避免每 tick 开销
//
// 战利品注入：
//   通过 LootTableLoadEvent 在要塞图书馆（minecraft:chests/stronghold_library）
//   战利品表中追加一个保底池，按配置概率生成"终末将至"附魔书。
//   SetEnchantmentsFunction 对 Items.BOOK 会自动转换为 ENCHANTED_BOOK 并存储附魔。
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class EndApproachesHandler {

    // 固定 UUID：终末将至攻速修饰符
    private static final UUID END_ATTACK_SPEED_UUID =
            UUID.fromString("e8b1c7d2-3f4a-5b6c-8d9e-0f1a2b3c4d5e");

    // 要塞图书馆战利品表 ID
    private static final ResourceLocation STRONGHOLD_LIBRARY =
            ResourceLocation.parse("minecraft:chests/stronghold_library");

    // 已提示过的箱子位置（防止重复提示，服务端存活期间有效）
    private static final Set<BlockPos> ANNOUNCED_CHESTS = new HashSet<>();

    // 清除末影人对玩家的全部仇恨（目标 + 持久愤怒计时 + 愤怒目标 UUID + 停止移动）
    private static void clearEndermanAnger(EnderMan enderman) {
        enderman.setTarget(null);
        enderman.setRemainingPersistentAngerTime(0);
        enderman.setPersistentAngerTarget(null);
        enderman.getNavigation().stop();
    }

    // ========================================================================
    // 【战利品注入】LootTableLoadEvent - 要塞图书馆必出"终末将至"附魔书
    // ========================================================================
    @SubscribeEvent
    public static void onLootTableLoad(LootTableLoadEvent event) {
        if (!STRONGHOLD_LIBRARY.equals(event.getName())) {
            return;
        }
        Enchantment enchantment = ModEnchantments.END_APPROACHES.get();

        // 构建保底池：1 roll，按配置概率生成一本附魔书
        float rolls = (float) Config.endApproachesLootRolls;
        LootPool pool = LootPool.lootPool()
                .setRolls(ConstantValue.exactly(rolls))
                .add(LootItem.lootTableItem(Items.BOOK)
                        .apply(new SetEnchantmentsFunction.Builder()
                                .withEnchantment(enchantment, ConstantValue.exactly(1.0f))))
                .build();

        event.getTable().addPool(pool);
    }

    // ========================================================================
    // 【获取提示】玩家打开包含"终末将至"的箱子时发送聊天消息
    // ========================================================================
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // 仅主手触发，避免双手各触发一次
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Level level = event.getEntity().level();
        if (level.isClientSide()) return;

        BlockPos pos = event.getPos().immutable();
        if (ANNOUNCED_CHESTS.contains(pos)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof Container container)) return;

        // 延迟到下一 tick 检查（战利品在箱子打开瞬间才生成）
        var server = level.getServer();
        if (server == null) return;
        server.execute(() -> {
            if (ANNOUNCED_CHESTS.contains(pos)) return;

            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack stack = container.getItem(i);
                if (stack.isEmpty()) continue;

                // 检查 Enchantments 标签（适用于 BOOK 及普通物品）
                int lvl = EnchantmentHelper.getTagEnchantmentLevel(
                        ModEnchantments.END_APPROACHES.get(), stack);
                // 若为附魔书，再检查 StoredEnchantments 标签
                if (lvl <= 0 && stack.is(Items.ENCHANTED_BOOK)) {
                    ListTag enchantments = EnchantedBookItem.getEnchantments(stack);
                    ResourceLocation targetId = ForgeRegistries.ENCHANTMENTS.getKey(
                            ModEnchantments.END_APPROACHES.get());
                    if (targetId != null) {
                        for (int j = 0; j < enchantments.size(); j++) {
                            CompoundTag tag = enchantments.getCompound(j);
                            if (targetId.toString().equals(tag.getString("id"))) {
                                lvl = tag.getShort("lvl");
                                break;
                            }
                        }
                    }
                }
                if (lvl > 0) {
                    ANNOUNCED_CHESTS.add(pos);
                    if (Config.endApproachesAnnounce) {
                        event.getEntity().sendSystemMessage(
                                Component.literal("§5你感受到了一股来自末地的回响……"));
                    }
                    break;
                }
            }
        });
    }

    // ========================================================================
    // 【末影人不以持有者为目标】LivingChangeTargetEvent
    // ========================================================================
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof EnderMan enderman)) return;
        if (!(event.getNewTarget() instanceof Player player)) return;

        // 检查主手武器是否附有"终末将至"
        ItemStack weapon = player.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.END_APPROACHES.get(), weapon);
        if (level > 0) {
            enderman.setTarget(null);
            event.setNewTarget(null);
        }
    }

    // ========================================================================
    // 【伤害提升 + 强制暴击】LivingHurtEvent
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }

        // 检查主手武器是否附有"终末将至"
        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.END_APPROACHES.get(), weapon);
        if (level <= 0) {
            return;
        }

        float amount = event.getAmount();

        // 【伤害提升】
        amount *= (float) Config.endApproachesDamageMultiplier;

        event.setAmount(amount);
    }

    // ========================================================================
    // 【冷却缩短 90%】PlayerTickEvent.END - 动态管理攻速修饰符
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        // 仅服务端执行
        if (player.level().isClientSide()) {
            return;
        }

        // 检查主手武器是否附有"终末将至"
        ItemStack weapon = player.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.END_APPROACHES.get(), weapon);
        boolean hasEnchant = level > 0;

        // 【末影人停止攻击】持有附魔武器时，主动清除附近已锁定玩家的末影人仇恨
        // 每 tick 检查，确保切换武器时立即生效
        if (hasEnchant) {
            int range = Config.endApproachesEndermanRange;
            AABB box = AABB.ofSize(player.position(), range, range, range);
            List<EnderMan> endermen = player.level().getEntitiesOfClass(EnderMan.class, box);
            for (EnderMan enderman : endermen) {
                if (enderman.getTarget() == player) {
                    clearEndermanAnger(enderman);
                }
            }
        }

        AttributeInstance attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr == null) {
            return;
        }

        boolean hasModifier = attackSpeedAttr.getModifier(END_ATTACK_SPEED_UUID) != null;

        if (hasEnchant && !hasModifier) {
            // 装备附魔武器 → 添加攻速修饰符
            AttributeModifier modifier = new AttributeModifier(
                    END_ATTACK_SPEED_UUID,
                    "End Approaches attack speed",
                    Config.endApproachesAttackSpeedBonus,
                    AttributeModifier.Operation.MULTIPLY_TOTAL
            );
            attackSpeedAttr.addTransientModifier(modifier);
        } else if (!hasEnchant && hasModifier) {
            // 脱下附魔武器 → 移除攻速修饰符
            attackSpeedAttr.removeModifier(END_ATTACK_SPEED_UUID);
        }
    }
}
