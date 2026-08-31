package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 自动修复（Auto Repair）独立配置文件
// 路径：config/enchantment_expansion-auto_repair.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AutoRepairConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("自动修复：附魔总开关")
            .define("autoRepair.enabled", true);

    private static final ForgeConfigSpec.IntValue LEVEL_1_INTERVAL_TICKS = BUILDER
            .comment("自动修复 I：每次恢复 1 点耐久的间隔（tick，默认 100 = 5秒）")
            .defineInRange("autoRepair.level1IntervalTicks", 100, 1, 72000);

    private static final ForgeConfigSpec.IntValue LEVEL_2_INTERVAL_TICKS = BUILDER
            .comment("自动修复 II：每次恢复 1 点耐久的间隔（tick，默认 80 = 4秒）")
            .defineInRange("autoRepair.level2IntervalTicks", 80, 1, 72000);

    private static final ForgeConfigSpec.IntValue LEVEL_3_INTERVAL_TICKS = BUILDER
            .comment("自动修复 III：每次恢复 1 点耐久的间隔（tick，默认 60 = 3秒）")
            .defineInRange("autoRepair.level3IntervalTicks", 60, 1, 72000);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存值
    private static boolean enabled = true;
    private static int level1IntervalTicks = 100;
    private static int level2IntervalTicks = 80;
    private static int level3IntervalTicks = 60;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        // 仅处理本配置，防止被其他独立配置触发时覆盖缓存
        if (event.getConfig() == null || event.getConfig().getSpec() != SPEC) {
            return;
        }

        enabled = ENABLED.get();
        level1IntervalTicks = LEVEL_1_INTERVAL_TICKS.get();
        level2IntervalTicks = LEVEL_2_INTERVAL_TICKS.get();
        level3IntervalTicks = LEVEL_3_INTERVAL_TICKS.get();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * 根据附魔等级返回恢复间隔（tick）。
     * 等级超出范围时按最近等级的间隔处理。
     */
    public static int getIntervalTicks(int level) {
        return switch (level) {
            case 1 -> level1IntervalTicks;
            case 2 -> level2IntervalTicks;
            default -> level <= 0 ? level1IntervalTicks : level3IntervalTicks;
        };
    }
}
