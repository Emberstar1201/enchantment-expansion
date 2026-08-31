package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 【深海的涟漪】独立配置文件
// 文件路径：config/enchantment_expansion-deep_sea_ripple.toml
//
// 提供：
//   - 总开关 enabled
//   - 游泳速度上限 maxSwimMultiplier（默认 3.0）
//   - 滚轮每格灵敏度 scrollSensitivity（默认 0.25 倍/格）
//   - 每 tick 基础加速度 baseAcceleration（默认 0.030，越大提速越明显）
// ========================================================================
@Mod.EventBusSubscriber(
        modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD
)
public class DeepSeaRippleConfig {

    private static final ForgeConfigSpec.Builder BUILDER =
            new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec SPEC;

    // ---------- 配置项声明 ----------

    private static final ForgeConfigSpec.BooleanValue ENABLED;

    private static final ForgeConfigSpec.DoubleValue MAX_SWIM_MULTIPLIER;

    private static final ForgeConfigSpec.DoubleValue SCROLL_SENSITIVITY;

    private static final ForgeConfigSpec.DoubleValue BASE_ACCELERATION;

    static {
        BUILDER.push("deepSeaRipple");

        ENABLED = BUILDER
                .comment("总开关：是否启用深海的涟漪附魔（水中游泳速度调节）",
                        "true = 启用；false = 该附魔所有行为停止")
                .define("enabled", true);

        MAX_SWIM_MULTIPLIER = BUILDER
                .comment("水中游泳速度倍率上限（默认 3.0，达到上限后再滚轮加速不会继续增加）",
                        "1.0 = 原版水下速度；3.0 = 3 倍原版水下速度")
                .defineInRange("maxSwimMultiplier", 3.0, 1.0, 10.0);

        SCROLL_SENSITIVITY = BUILDER
                .comment("鼠标滚轮灵敏度（每一格滚轮改变多少游泳速度倍率）",
                        "默认 0.25：向上滚一格 = 倍率 + 0.25；向下滚一格 = 倍率 - 0.25")
                .defineInRange("scrollSensitivity", 0.25, 0.01, 5.0);

        BASE_ACCELERATION = BUILDER
                .comment("每 tick 加速度系数（影响“加速有多猛”）。",
                        "每 tick 沿玩家视线水平方向加速：Δv = (当前倍率 - 1.0) × baseAcceleration",
                        "默认 0.030；建议范围 0.010 ~ 0.080")
                .defineInRange("baseAcceleration", 0.030, 0.005, 0.500);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // ---------- 运行时缓存 ----------

    private static boolean enabled = true;
    private static double maxSwimMultiplier = 3.0;
    private static double scrollSensitivity = 0.25;
    private static double baseAcceleration = 0.030;

    // ========================================================================
    // ModConfigEvent：加载/重载配置时刷新缓存字段
    // 仅对本配置对应的 SPEC 生效，避免被其他配置事件误触发
    // ========================================================================
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig() == null
                || event.getConfig().getSpec() != DeepSeaRippleConfig.SPEC) {
            return;
        }
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        enabled = ENABLED.get();
        maxSwimMultiplier = MAX_SWIM_MULTIPLIER.get();
        scrollSensitivity = SCROLL_SENSITIVITY.get();
        baseAcceleration = BASE_ACCELERATION.get();
    }

    // ---------- 对外 API（仅读取缓存，无性能开销） ----------

    public static boolean isEnabled() {
        return enabled;
    }

    public static double getMaxSwimMultiplier() {
        return maxSwimMultiplier;
    }

    public static double getScrollSensitivity() {
        return scrollSensitivity;
    }

    public static double getBaseAcceleration() {
        return baseAcceleration;
    }
}
