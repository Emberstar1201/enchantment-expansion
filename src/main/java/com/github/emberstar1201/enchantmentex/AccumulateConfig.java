package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「蓄积」（accumulate）附魔独立配置文件
//
// 数值：
//   - 蓄力时间：长按右键完成一阶蓄力所需的秒数
//   - 各阶级伤害倍率：蓄满 1 / 2 / 3 阶后攻击的伤害倍数（I×1 / II×2 / III×3）
//   - 各阶级击退距离
//   - 遗忘时间：完成蓄力后超过该秒数未发起攻击则重置
//   - 附魔总开关
//
// 配置路径：config/enchantment_expansion-accumulate.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AccumulateConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 一、总开关
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ACCUMULATE_ENABLED = BUILDER
            .comment("蓄积：是否启用（默认 true）")
            .define("accumulate.enabled", true);

    // ================================================================
    // 二、蓄力时间
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue CHARGE_TIME_SECONDS = BUILDER
            .comment("蓄积：长按右键完成一阶蓄力所需秒数（默认 5.0 = 5 秒）")
            .defineInRange("accumulate.chargeTimeSeconds", 5.0, 0.5, 60.0);

    // ================================================================
    // 三、各阶级伤害倍率
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue STAGE1_DAMAGE_MULTIPLIER = BUILDER
            .comment("蓄积：蓄满 1 阶的伤害倍率（默认 1.0 = ×1）")
            .defineInRange("accumulate.stage1DamageMultiplier", 1.0, 0.5, 20.0);

    private static final ForgeConfigSpec.DoubleValue STAGE2_DAMAGE_MULTIPLIER = BUILDER
            .comment("蓄积：蓄满 2 阶的伤害倍率（默认 2.0 = ×2）")
            .defineInRange("accumulate.stage2DamageMultiplier", 2.0, 0.5, 20.0);

    private static final ForgeConfigSpec.DoubleValue STAGE3_DAMAGE_MULTIPLIER = BUILDER
            .comment("蓄积：蓄满 3 阶的伤害倍率（默认 3.0 = ×3）")
            .defineInRange("accumulate.stage3DamageMultiplier", 3.0, 0.5, 20.0);

    // ================================================================
    // 四、各阶级击退距离
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue STAGE1_KNOCKBACK = BUILDER
            .comment("蓄积：蓄满 1 阶的击退强度（默认 0.4）")
            .defineInRange("accumulate.stage1Knockback", 0.4, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue STAGE2_KNOCKBACK = BUILDER
            .comment("蓄积：蓄满 2 阶的击退强度（默认 0.8）")
            .defineInRange("accumulate.stage2Knockback", 0.8, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue STAGE3_KNOCKBACK = BUILDER
            .comment("蓄积：蓄满 3 阶的击退强度（默认 1.2）")
            .defineInRange("accumulate.stage3Knockback", 1.2, 0.0, 10.0);

    // ================================================================
    // 五、遗忘时间（蓄力完成后未攻击则重置）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue FORGET_SECONDS = BUILDER
            .comment("蓄积：蓄力完成后超过该秒数未发起攻击则重置层数（默认 10 = 10 秒）")
            .defineInRange("accumulate.forgetSeconds", 10.0, 1.0, 120.0);

    // 配置 SPEC 实例
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值
    // ================================================================
    public static boolean accumulateEnabled;
    public static double accumulateChargeTimeSeconds;
    public static double accumulateStage1DamageMultiplier;
    public static double accumulateStage2DamageMultiplier;
    public static double accumulateStage3DamageMultiplier;
    public static double accumulateStage1Knockback;
    public static double accumulateStage2Knockback;
    public static double accumulateStage3Knockback;
    public static double accumulateForgetSeconds;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：只响应本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != AccumulateConfig.SPEC) {
            return;
        }

        accumulateEnabled = ACCUMULATE_ENABLED.get();
        accumulateChargeTimeSeconds = CHARGE_TIME_SECONDS.get();
        accumulateStage1DamageMultiplier = STAGE1_DAMAGE_MULTIPLIER.get();
        accumulateStage2DamageMultiplier = STAGE2_DAMAGE_MULTIPLIER.get();
        accumulateStage3DamageMultiplier = STAGE3_DAMAGE_MULTIPLIER.get();
        accumulateStage1Knockback = STAGE1_KNOCKBACK.get();
        accumulateStage2Knockback = STAGE2_KNOCKBACK.get();
        accumulateStage3Knockback = STAGE3_KNOCKBACK.get();
        accumulateForgetSeconds = FORGET_SECONDS.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 是否启用蓄积附魔 */
    public static boolean isEnabled() {
        return accumulateEnabled;
    }

    /** 完成一阶蓄力所需 tick 数（20 tick = 1 秒） */
    public static int getChargeTicks() {
        return (int) Math.round(accumulateChargeTimeSeconds * 20.0);
    }

    /** 根据已蓄阶级返回伤害倍率 */
    public static double getStageDamageMultiplier(int stage) {
        return switch (stage) {
            case 1 -> accumulateStage1DamageMultiplier;
            case 2 -> accumulateStage2DamageMultiplier;
            case 3 -> accumulateStage3DamageMultiplier;
            default -> stage > 3 ? accumulateStage3DamageMultiplier : 1.0;
        };
    }

    /** 根据已蓄阶级返回击退强度 */
    public static double getStageKnockback(int stage) {
        return switch (stage) {
            case 1 -> accumulateStage1Knockback;
            case 2 -> accumulateStage2Knockback;
            case 3 -> accumulateStage3Knockback;
            default -> stage > 3 ? accumulateStage3Knockback : 0.0;
        };
    }

    /** 蓄力完成后未攻击的遗忘 tick 数 */
    public static int getForgetTicks() {
        return (int) Math.round(accumulateForgetSeconds * 20.0);
    }
}