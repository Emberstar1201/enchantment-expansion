package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「疾跑节能」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-sprint_endurance.toml
//
// 配置项：
//   1. sprintEndurance.enabled             ：附魔总开关（默认 true）
//   2. sprintEndurance.baseOffsetPerTick  ：每 tick 抵消的"疾跑额外疲劳值"
//
// 作用原理：原版疾跑每 tick 会在基础能耗之外额外累积 exhaustion（疲劳值），
//          累积满 4.0 时扣 1 点饥饿/饱和度。本配置定义每 tick 要抵消的量。
//          - I 级：baseOffsetPerTick × 0.5
//          - II 级：baseOffsetPerTick × 1.0（完全抵消疾跑额外疲劳）
//          默认值 0.03 约对应当前版本原版疾跑的额外能耗，可自行微调。
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SprintEnduranceConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========================================================================
    // 一、附魔总开关
    // ========================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("疾跑节能：附魔总开关（默认 true）。设为 false 可整附魔失效。")
            .define("sprintEndurance.enabled", true);

    // ========================================================================
    // 二、每 tick 抵消的"疾跑额外疲劳值"
    //    - I 级：本值 × 0.5
    //    - II 级：本值 × 1.0（完全抵消疾跑额外疲劳，疾跑能耗≈走路）
    //    默认 0.03（约对应当前版本原版疾跑额外的每 tick 疲劳值）
    // ========================================================================
    private static final ForgeConfigSpec.DoubleValue BASE_OFFSET_PER_TICK = BUILDER
            .comment("疾跑节能：每 tick 抵消的疾跑额外疲劳值（exhaustion）。",
                    "I 级抵消本值 × 0.5，II 级抵消本值 × 1.0（完全抵消）。",
                    "默认 0.03（约对应当前版本原版疾跑额外的每 tick 疲劳值），可自行微调。")
            .defineInRange("sprintEndurance.baseOffsetPerTick", 0.03, 0.0, 1.0);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ========================================================================
    // 运行时缓存字段（配置加载后才有效）
    // ========================================================================
    public static boolean enabled;
    public static double baseOffsetPerTick;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：必须确认是本配置，否则其他配置加载时 get() 会抛异常
        if (event.getConfig() == null || event.getConfig().getSpec() != SprintEnduranceConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        baseOffsetPerTick = BASE_OFFSET_PER_TICK.get();
    }

    // ========================================================================
    // 工具查询方法
    // ========================================================================

    /** 附魔是否整体启用 */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 指定附魔等级下，每 tick 应抵消的疾跑额外疲劳值。
     * @param level 附魔等级（1 或 2）
     * @return 需抵消的疲劳值；等级无效时为 0
     */
    public static float getReductionPerTick(int level) {
        double v = baseOffsetPerTick;
        if (level == 1) {
            return (float) (v * 0.5);
        }
        if (level == 2) {
            return (float) v;
        }
        return 0f;
    }
}
