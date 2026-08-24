package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「温度恒定」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-temperature_constant.toml
//
// 配置项：
//   1. temperatureConstant.enabled      ：附魔总开关（默认 true）
//   2. temperatureConstant.preventFreeze：是否免疫冰冻伤害（细雪，默认 true）
//   3. temperatureConstant.preventHeat  ：是否免疫高温伤害（火焰/岩浆，默认 true）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TemperatureConstantConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("温度恒定：附魔总开关（默认 true）。设为 false 可整体失效。")
            .define("temperatureConstant.enabled", true);

    private static final ForgeConfigSpec.BooleanValue PREVENT_FREEZE = BUILDER
            .comment("温度恒定：是否免疫冰冻伤害（细雪冻伤等，默认 true）")
            .define("temperatureConstant.preventFreeze", true);

    private static final ForgeConfigSpec.BooleanValue PREVENT_HEAT = BUILDER
            .comment("温度恒定：是否免疫高温伤害（火焰、岩浆、高温地面等，默认 true）")
            .define("temperatureConstant.preventHeat", true);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存字段
    public static boolean enabled;
    public static boolean preventFreeze;
    public static boolean preventHeat;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 必须过滤：仅处理本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != TemperatureConstantConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        preventFreeze = PREVENT_FREEZE.get();
        preventHeat = PREVENT_HEAT.get();
    }
}