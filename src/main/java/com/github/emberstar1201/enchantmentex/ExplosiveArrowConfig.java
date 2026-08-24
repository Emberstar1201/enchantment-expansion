package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「爆破箭矢」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-explosive_arrow.toml
//
// 配置项：
//   1. explosiveArrow.enabled       ：附魔总开关（默认 true）
//   2. explosiveArrow.radiusL1/2/3  ：爆炸半径（默认 2/3/4 格）
//   3. explosiveArrow.damageL1/2/3  ：中心伤害（默认 6/9/12）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ExplosiveArrowConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("爆破箭矢：附魔总开关（默认 true）。设为 false 可整体失效。")
            .define("explosiveArrow.enabled", true);

    private static final ForgeConfigSpec.DoubleValue RADIUS_L1 = BUILDER
            .comment("爆破箭矢：I 级爆炸半径（格，默认 2.0）")
            .defineInRange("explosiveArrow.radiusLevel1", 2.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue RADIUS_L2 = BUILDER
            .comment("爆破箭矢：II 级爆炸半径（格，默认 3.0）")
            .defineInRange("explosiveArrow.radiusLevel2", 3.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue RADIUS_L3 = BUILDER
            .comment("爆破箭矢：III 级爆炸半径（格，默认 4.0）")
            .defineInRange("explosiveArrow.radiusLevel3", 4.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_L1 = BUILDER
            .comment("爆破箭矢：I 级中心伤害（默认 6.0）")
            .defineInRange("explosiveArrow.damageLevel1", 6.0, 1.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_L2 = BUILDER
            .comment("爆破箭矢：II 级中心伤害（默认 9.0）")
            .defineInRange("explosiveArrow.damageLevel2", 9.0, 1.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_L3 = BUILDER
            .comment("爆破箭矢：III 级中心伤害（默认 12.0）")
            .defineInRange("explosiveArrow.damageLevel3", 12.0, 1.0, 100.0);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存字段
    public static boolean enabled;
    public static double radiusLevel1;
    public static double radiusLevel2;
    public static double radiusLevel3;
    public static double damageLevel1;
    public static double damageLevel2;
    public static double damageLevel3;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 必须过滤：仅处理本配置的加载事件，避免其他配置未加载时 get() 抛异常
        if (event.getConfig() == null || event.getConfig().getSpec() != ExplosiveArrowConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        radiusLevel1 = RADIUS_L1.get();
        radiusLevel2 = RADIUS_L2.get();
        radiusLevel3 = RADIUS_L3.get();
        damageLevel1 = DAMAGE_L1.get();
        damageLevel2 = DAMAGE_L2.get();
        damageLevel3 = DAMAGE_L3.get();
    }

    /** 给定等级返回爆炸半径（格），等级超界钳制到 III */
    public static double getRadius(int level) {
        return switch (level) {
            case 1 -> radiusLevel1;
            case 2 -> radiusLevel2;
            default -> radiusLevel3;
        };
    }

    /** 给定等级返回爆炸中心伤害，等级超界钳制到 III */
    public static double getDamage(int level) {
        return switch (level) {
            case 1 -> damageLevel1;
            case 2 -> damageLevel2;
            default -> damageLevel3;
        };
    }
}