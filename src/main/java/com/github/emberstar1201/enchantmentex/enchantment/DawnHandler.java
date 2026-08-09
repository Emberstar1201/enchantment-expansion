package com.github.emberstar1201.enchantmentex.enchantment;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.UUID;

import static com.github.emberstar1201.enchantmentex.EnchantmentExpansion.MODID;

// ========================================================================
// 【拂晓】附魔事件处理器
//
// 双事件驱动：
//   1. LivingHurtEvent：根据缓存的时段，对伤害值乘以对应倍率
//   2. PlayerTickEvent.END：根据缓存的时段，动态增删 ATTACK_SPEED AttributeModifier
//      实现冷却缩减（午夜时 modifier 极大，等价无冷却）
//
// 维度限制（v2 修复）：
//   拂晓"随昼夜变化"的设计意图仅适用于主世界（Overworld）。
//   下界（Nether）和末地（The End）没有昼夜循环，主世界时间继续流动会导致：
//     - 玩家在下界仍享受"夜晚加成"（不合理）
//     - 主世界时间从夜晚流到白天时，玩家在下界中突然失去加成（诡异）
//   → 修复方案：只在玩家处于主世界时应用加成，非主世界移除 modifier 且不修改伤害
//
// 修饰符管理策略（避免每 tick 叠加）：
//   - 使用固定 UUID，addTransientModifier 会自动覆盖同 UUID 的旧 modifier
//   - 但为减少频繁增删开销，用 PersistentData 记录"上一 tick 的时段"
//   - 仅当时段变化（或武器状态变化、维度变化）时才增删 modifier
//   - 白天（DAY）、脱下武器、非主世界 → 移除 modifier
// ========================================================================
@Mod.EventBusSubscriber(modid = MODID)
public class DawnHandler {

    // 固定 UUID：拂晓攻速修饰符
    // 每次增删都用同一 UUID，确保不叠加
    private static final UUID DAWN_ATTACK_SPEED_UUID =
            UUID.fromString("d4a5f6b7-8c9d-4e1f-9a2b-3c4d5e6f7a8b");

    // PersistentData 中存储"上一 tick 所在维度是否为主世界"的键名
    // 用于检测维度切换：主世界→下界时应立即移除 modifier
    public static final String LAST_IN_OVERWORLD_KEY = "dawn_last_in_overworld";

    // ========================================================================
    // 【伤害倍率】LivingHurtEvent - 攻击造成伤害时触发
    // 读取攻击者 PersistentData 中缓存的时段，应用对应伤害倍率
    // ========================================================================
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        // 检查攻击者是否是玩家
        if (event.getSource() == null || !(event.getSource().getEntity() instanceof Player attacker)) {
            return;
        }

        // 【维度限制】拂晓只在主世界生效
        // 下界/末地没有昼夜循环，不应用任何加成
        if (attacker.level().dimension() != Level.OVERWORLD) {
            return;
        }

        // 检查玩家主手武器是否附有"拂晓"
        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        if (level <= 0) {
            return;
        }

        // 读取缓存的时段（由 DawnTimeTracker 每 20 tick 写入）
        // 若缓存不存在（如首 tick），用静态字段降级
        int periodCode = attacker.getPersistentData().getInt(DawnEnchantment.PERIOD_KEY);
        DawnTimePeriod period = DawnTimePeriod.fromCode(periodCode);

        // 应用伤害倍率
        float damageMultiplier = period.getDamageMultiplier();
        if (damageMultiplier > 1.0f) {
            // 原伤害 × 倍率
            event.setAmount(event.getAmount() * damageMultiplier);
        }
    }

    // ========================================================================
    // 【冷却缩减】PlayerTickEvent.END - 每帧检查玩家状态
    // 根据时段动态调整 ATTACK_SPEED AttributeModifier
    // ========================================================================
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // 仅在 END 阶段执行（玩家 tick 完成后修改属性）
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Player player = event.player;

        // 仅服务端执行（AttributeModifier 修改是服务端逻辑）
        if (player.level().isClientSide()) {
            return;
        }

        // 【维度限制】拂晓只在主世界生效
        // 非主世界时，移除 modifier（如有），不进行任何加成
        boolean isInOverworld = player.level().dimension() == Level.OVERWORLD;

        // 检查玩家主手武器是否附有"拂晓"
        ItemStack weapon = player.getMainHandItem();
        int level = EnchantmentHelper.getTagEnchantmentLevel(
                ModEnchantments.DAWN.get(), weapon);
        boolean hasEnchant = level > 0;

        // 读取当前时段
        int periodCode = player.getPersistentData().getInt(DawnEnchantment.PERIOD_KEY);
        DawnTimePeriod currentPeriod = DawnTimePeriod.fromCode(periodCode);

        // 读取上一 tick 的时段（用于判断是否需要更新 modifier）
        int lastPeriodCode = player.getPersistentData().getInt(DawnEnchantment.LAST_PERIOD_KEY);

        // 读取上一 tick 是否在主世界（用于检测维度切换）
        boolean wasInOverworld = player.getPersistentData()
                .getBoolean(LAST_IN_OVERWORLD_KEY);

        // 获取 ATTACK_SPEED 属性实例
        AttributeInstance attackSpeedAttr = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeedAttr == null) {
            return;
        }

        // 判断当前是否需要 modifier
        //   三重条件全部满足才需要：
        //   ① 有附魔（hasEnchant）
        //   ② 时段不是白天（有冷却缩减）
        //   ③ 玩家在主世界（isInOverworld）
        //   任一不满足 → 不需要 modifier（已有则移除）
        boolean needsModifier = hasEnchant
                && currentPeriod != DawnTimePeriod.DAY
                && isInOverworld;

        // 判断当前 modifier 是否已存在
        boolean hasModifierNow = attackSpeedAttr.getModifier(DAWN_ATTACK_SPEED_UUID) != null;

        // 计算当前时段的攻速加成值
        float attackSpeedBonus = currentPeriod.getAttackSpeedBonus();

        // ================================================================
        // 状态切换决策（仅在状态变化时操作 modifier，避免每 tick 重复增删）
        // ================================================================
        // 情况 1：不需要 modifier 但当前有 → 移除
        //   触发场景：脱下武器 / 时段进入白天 / 维度切换到下界或末地
        if (!needsModifier && hasModifierNow) {
            attackSpeedAttr.removeModifier(DAWN_ATTACK_SPEED_UUID);
        }
        // 情况 2：需要 modifier 但当前没有 → 添加
        //   触发场景：刚装备附魔武器 / 时段从白天进入夜晚 / 从下界返回主世界
        else if (needsModifier && !hasModifierNow) {
            AttributeModifier modifier = new AttributeModifier(
                    DAWN_ATTACK_SPEED_UUID,
                    "Dawn attack speed bonus",
                    attackSpeedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            attackSpeedAttr.addTransientModifier(modifier);
        }
        // 情况 3：需要 modifier 且已有，但状态变化（时段或维度）→ 移除后重新添加
        //   触发场景：夜晚 → 午夜（攻速加成值变化）/ 主世界内时段切换
        else if (needsModifier && hasModifierNow
                && (currentPeriod.getCode() != lastPeriodCode
                    || isInOverworld != wasInOverworld)) {
            attackSpeedAttr.removeModifier(DAWN_ATTACK_SPEED_UUID);
            AttributeModifier modifier = new AttributeModifier(
                    DAWN_ATTACK_SPEED_UUID,
                    "Dawn attack speed bonus",
                    attackSpeedBonus,
                    AttributeModifier.Operation.MULTIPLY_BASE
            );
            attackSpeedAttr.addTransientModifier(modifier);
        }
        // 情况 4：状态未变化 → 什么都不做（每 tick 0 开销）

        // 更新"上一 tick 时段"记录
        if (currentPeriod.getCode() != lastPeriodCode) {
            player.getPersistentData().putInt(DawnEnchantment.LAST_PERIOD_KEY, currentPeriod.getCode());
        }
        // 更新"上一 tick 维度状态"记录
        if (isInOverworld != wasInOverworld) {
            player.getPersistentData().putBoolean(LAST_IN_OVERWORLD_KEY, isInOverworld);
        }
    }
}
