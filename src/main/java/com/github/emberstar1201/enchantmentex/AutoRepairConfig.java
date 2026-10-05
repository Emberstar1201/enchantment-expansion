package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.util.RandomSource;
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

    private static final ForgeConfigSpec.IntValue REPAIR_INTERVAL_TICKS = BUILDER
            .comment("自动修复：所有等级每次修复的间隔（tick，默认 100 = 5秒）")
            .defineInRange("autoRepair.intervalTicks", 100, 1, 72000);

    private static final ForgeConfigSpec.IntValue LEVEL_1_MIN_AMOUNT = BUILDER
            .comment("自动修复 I：每次随机恢复的最小耐久值（默认 1）")
            .defineInRange("autoRepair.level1MinAmount", 1, 1, 100);

    private static final ForgeConfigSpec.IntValue LEVEL_1_MAX_AMOUNT = BUILDER
            .comment("自动修复 I：每次随机恢复的最大耐久值（默认 3）")
            .defineInRange("autoRepair.level1MaxAmount", 3, 1, 100);

    private static final ForgeConfigSpec.IntValue LEVEL_2_MIN_AMOUNT = BUILDER
            .comment("自动修复 II：每次随机恢复的最小耐久值（默认 5）")
            .defineInRange("autoRepair.level2MinAmount", 5, 1, 100);

    private static final ForgeConfigSpec.IntValue LEVEL_2_MAX_AMOUNT = BUILDER
            .comment("自动修复 II：每次随机恢复的最大耐久值（默认 7）")
            .defineInRange("autoRepair.level2MaxAmount", 7, 1, 100);

    private static final ForgeConfigSpec.IntValue LEVEL_3_MIN_AMOUNT = BUILDER
            .comment("自动修复 III：每次随机恢复的最小耐久值（默认 10）")
            .defineInRange("autoRepair.level3MinAmount", 10, 1, 100);

    private static final ForgeConfigSpec.IntValue LEVEL_3_MAX_AMOUNT = BUILDER
            .comment("自动修复 III：每次随机恢复的最大耐久值（默认 12）")
            .defineInRange("autoRepair.level3MaxAmount", 12, 1, 100);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存值
    private static boolean enabled = true;
    private static int repairIntervalTicks = 100;
    private static int level1MinAmount = 1;
    private static int level1MaxAmount = 3;
    private static int level2MinAmount = 5;
    private static int level2MaxAmount = 7;
    private static int level3MinAmount = 10;
    private static int level3MaxAmount = 12;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        // 仅处理本配置，防止被其他独立配置触发时覆盖缓存
        if (event.getConfig() == null || event.getConfig().getSpec() != SPEC) {
            return;
        }

        enabled = ENABLED.get();
        repairIntervalTicks = REPAIR_INTERVAL_TICKS.get();
        level1MinAmount = LEVEL_1_MIN_AMOUNT.get();
        level1MaxAmount = LEVEL_1_MAX_AMOUNT.get();
        level2MinAmount = LEVEL_2_MIN_AMOUNT.get();
        level2MaxAmount = LEVEL_2_MAX_AMOUNT.get();
        level3MinAmount = LEVEL_3_MIN_AMOUNT.get();
        level3MaxAmount = LEVEL_3_MAX_AMOUNT.get();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getIntervalTicks() {
        return repairIntervalTicks;
    }

    public static int getRepairAmount(int level, RandomSource random) {
        int min;
        int max;
        switch (level) {
            case 1 -> {
                min = level1MinAmount;
                max = level1MaxAmount;
            }
            case 2 -> {
                min = level2MinAmount;
                max = level2MaxAmount;
            }
            default -> {
                min = level3MinAmount;
                max = level3MaxAmount;
            }
        }
        return min + random.nextInt(max - min + 1);
    }
}
