package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「迅捷之弩」附魔独立配置文件
//
// 为迅捷之弩附魔的魔法伤害附加与装填加速提供独立数值，
// 服务器管理员可单独调整，不污染主 Config.java。
//
// 配置路径：config/enchantment_expansion-swift_crossbow.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SwiftCrossbowConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 一、弩箭附加魔法伤害（I:+2 / II:+4 / III:+6 / IV:+8，无视护甲）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue BONUS_DAMAGE_L1 = BUILDER
            .comment("迅捷之弩：I级弩箭附加魔法伤害（默认 2.0，无视护甲）")
            .defineInRange("swiftCrossbow.bonusDamageLevel1", 2.0, 0.0, 10000.0);

    private static final ForgeConfigSpec.DoubleValue BONUS_DAMAGE_L2 = BUILDER
            .comment("迅捷之弩：II级弩箭附加魔法伤害（默认 4.0，无视护甲）")
            .defineInRange("swiftCrossbow.bonusDamageLevel2", 4.0, 0.0, 10000.0);

    private static final ForgeConfigSpec.DoubleValue BONUS_DAMAGE_L3 = BUILDER
            .comment("迅捷之弩：III级弩箭附加魔法伤害（默认 6.0，无视护甲）")
            .defineInRange("swiftCrossbow.bonusDamageLevel3", 6.0, 0.0, 10000.0);

    private static final ForgeConfigSpec.DoubleValue BONUS_DAMAGE_L4 = BUILDER
            .comment("迅捷之弩：IV级弩箭附加魔法伤害（默认 8.0，无视护甲）")
            .defineInRange("swiftCrossbow.bonusDamageLevel4", 8.0, 0.0, 10000.0);

    // ================================================================
    // 二、装填时间减少量（tick，20 tick = 1 秒）
    // 原版弩装填 = 25 tick（1.25 秒），装填时间 = max(25 - 减少量, 下限)
    //   I: 减 5 tick（-0.25s）→ 20 tick（1.0s）
    //   II: 减 10 tick（-0.5s）→ 15 tick（0.75s）
    //   III: 减 20 tick（-1.0s）→ 5 tick（0.25s）
    //   IV: 减 25 tick（-1.25s）→ 近乎瞬间装填
    // ================================================================
    private static final ForgeConfigSpec.IntValue CHARGE_REDUCE_TICKS_L1 = BUILDER
            .comment("迅捷之弩：I级装填时间减少量（tick，默认 5 = 减少0.25秒）")
            .defineInRange("swiftCrossbow.chargeReduceTicksLevel1", 5, 0, 25);

    private static final ForgeConfigSpec.IntValue CHARGE_REDUCE_TICKS_L2 = BUILDER
            .comment("迅捷之弩：II级装填时间减少量（tick，默认 10 = 减少0.5秒）")
            .defineInRange("swiftCrossbow.chargeReduceTicksLevel2", 10, 0, 25);

    private static final ForgeConfigSpec.IntValue CHARGE_REDUCE_TICKS_L3 = BUILDER
            .comment("迅捷之弩：III级装填时间减少量（tick，默认 20 = 减少1.0秒）")
            .defineInRange("swiftCrossbow.chargeReduceTicksLevel3", 20, 0, 25);

    private static final ForgeConfigSpec.IntValue CHARGE_REDUCE_TICKS_L4 = BUILDER
            .comment("迅捷之弩：IV级装填时间减少量（tick，默认 25 = 减少1.25秒，立即装填）")
            .defineInRange("swiftCrossbow.chargeReduceTicksLevel4", 25, 0, 25);

    private static final ForgeConfigSpec.IntValue MIN_CHARGE_TICKS = BUILDER
            .comment("迅捷之弩：装填时间下限（tick，默认 1，禁止出现 0 导致弩逻辑异常）")
            .defineInRange("swiftCrossbow.minChargeTicks", 1, 1, 25);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值（运行时读取这些字段）
    // ================================================================
    public static double bonusDamageLevel1;
    public static double bonusDamageLevel2;
    public static double bonusDamageLevel3;
    public static double bonusDamageLevel4;
    public static int chargeReduceTicksLevel1;
    public static int chargeReduceTicksLevel2;
    public static int chargeReduceTicksLevel3;
    public static int chargeReduceTicksLevel4;
    public static int minChargeTicks;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：本类会被自动注册到所有 MOD 配置的加载事件上，
        //   只有当事件对应的就是「迅捷之弩」这份配置时才读取数值。
        //   否则在其他配置加载时，尚未加载的 ConfigValue 直接 .get()
        //   会抛 "Cannot get config value before config is loaded"。
        if (event.getConfig() == null || event.getConfig().getSpec() != SwiftCrossbowConfig.SPEC) {
            return;
        }

        bonusDamageLevel1 = BONUS_DAMAGE_L1.get();
        bonusDamageLevel2 = BONUS_DAMAGE_L2.get();
        bonusDamageLevel3 = BONUS_DAMAGE_L3.get();
        bonusDamageLevel4 = BONUS_DAMAGE_L4.get();
        chargeReduceTicksLevel1 = CHARGE_REDUCE_TICKS_L1.get();
        chargeReduceTicksLevel2 = CHARGE_REDUCE_TICKS_L2.get();
        chargeReduceTicksLevel3 = CHARGE_REDUCE_TICKS_L3.get();
        chargeReduceTicksLevel4 = CHARGE_REDUCE_TICKS_L4.get();
        minChargeTicks = MIN_CHARGE_TICKS.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 给定附魔等级，返回弩箭附加魔法伤害（未附魔=0，等级超界钳制到 IV） */
    public static double getBonusDamage(int level) {
        return switch (level) {
            case 1 -> bonusDamageLevel1;
            case 2 -> bonusDamageLevel2;
            case 3 -> bonusDamageLevel3;
            case 4 -> bonusDamageLevel4;
            default -> level > 4 ? bonusDamageLevel4 : 0.0;
        };
    }

    /** 给定附魔等级，返回装填时间减少量（tick） */
    public static int getChargeReduceTicks(int level) {
        return switch (level) {
            case 1 -> chargeReduceTicksLevel1;
            case 2 -> chargeReduceTicksLevel2;
            case 3 -> chargeReduceTicksLevel3;
            case 4 -> chargeReduceTicksLevel4;
            default -> level > 4 ? chargeReduceTicksLevel4 : 0;
        };
    }

    /** 给定附魔等级与基础装填时间（tick），计算最终装填时间（不低于下限） */
    public static int computeFinalChargeTicks(int baseTicks, int level) {
        int result = baseTicks - getChargeReduceTicks(level);
        return Math.max(minChargeTicks, result);
    }
}