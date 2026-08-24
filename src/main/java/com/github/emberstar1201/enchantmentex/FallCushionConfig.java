package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「坠落缓冲」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-fall_cushion.toml
//
// 配置项：
//   1. fallCushion.enabled        ：附魔总开关（默认 true）
//   2. fallCushion.damageMulL1/L2 ：摔落伤害倍率（默认 0.5 / 0.25）
//   3. fallCushion.knockbackL1/L2 ：冲击波击退强度（默认 1.0 / 2.0）
//   4. fallCushion.radiusL1/L2    ：冲击波半径（格，默认 3 / 5）
//   5. fallCushion.minFallDist    ：触发冲击波的最小摔落距离（格，默认 3.0）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FallCushionConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("坠落缓冲：附魔总开关（默认 true）。设为 false 可整体失效。")
            .define("fallCushion.enabled", true);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_MUL_L1 = BUILDER
            .comment("坠落缓冲：I 级摔落伤害倍率（0.5 = 减伤 50%）")
            .defineInRange("fallCushion.damageMultiplierLevel1", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_MUL_L2 = BUILDER
            .comment("坠落缓冲：II 级摔落伤害倍率（0.25 = 减伤 75%）")
            .defineInRange("fallCushion.damageMultiplierLevel2", 0.25, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue KNOCKBACK_L1 = BUILDER
            .comment("坠落缓冲：I 级冲击波击退强度（默认 1.0）")
            .defineInRange("fallCushion.knockbackLevel1", 1.0, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue KNOCKBACK_L2 = BUILDER
            .comment("坠落缓冲：II 级冲击波击退强度（默认 2.0）")
            .defineInRange("fallCushion.knockbackLevel2", 2.0, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue RADIUS_L1 = BUILDER
            .comment("坠落缓冲：I 级冲击波半径（格，默认 3.0）")
            .defineInRange("fallCushion.radiusLevel1", 3.0, 1.0, 16.0);

    private static final ForgeConfigSpec.DoubleValue RADIUS_L2 = BUILDER
            .comment("坠落缓冲：II 级冲击波半径（格，默认 5.0）")
            .defineInRange("fallCushion.radiusLevel2", 5.0, 1.0, 16.0);

    private static final ForgeConfigSpec.DoubleValue MIN_FALL_DIST = BUILDER
            .comment("坠落缓冲：触发冲击波的最小摔落距离（格，低于此值不产生击退，默认 3.0）")
            .defineInRange("fallCushion.minFallDistance", 3.0, 1.0, 64.0);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存字段
    public static boolean enabled;
    public static double damageMultiplierLevel1;
    public static double damageMultiplierLevel2;
    public static double knockbackLevel1;
    public static double knockbackLevel2;
    public static double radiusLevel1;
    public static double radiusLevel2;
    public static double minFallDistance;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 必须过滤：仅处理本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != FallCushionConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        damageMultiplierLevel1 = DAMAGE_MUL_L1.get();
        damageMultiplierLevel2 = DAMAGE_MUL_L2.get();
        knockbackLevel1 = KNOCKBACK_L1.get();
        knockbackLevel2 = KNOCKBACK_L2.get();
        radiusLevel1 = RADIUS_L1.get();
        radiusLevel2 = RADIUS_L2.get();
        minFallDistance = MIN_FALL_DIST.get();
    }

    /** 给定等级返回摔落伤害倍率 */
    public static double getDamageMultiplier(int level) {
        return level >= 2 ? damageMultiplierLevel2 : damageMultiplierLevel1;
    }

    /** 给定等级返回冲击波击退强度 */
    public static double getKnockback(int level) {
        return level >= 2 ? knockbackLevel2 : knockbackLevel1;
    }

    /** 给定等级返回冲击波半径 */
    public static double getRadius(int level) {
        return level >= 2 ? radiusLevel2 : radiusLevel1;
    }
}