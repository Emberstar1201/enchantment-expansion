package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// 画饼充饥独立配置：config/enchantment_expansion-illusory_feast.toml
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class IllusoryFeastConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("画饼充饥：附魔总开关")
            .define("illusoryFeast.enabled", true);

    private static final ForgeConfigSpec.IntValue LEVEL_1_FOOD = BUILDER
            .comment("画饼充饥 I：每次命中恢复的饥饿值")
            .defineInRange("illusoryFeast.level1Food", 1, 0, 20);
    private static final ForgeConfigSpec.DoubleValue LEVEL_1_SATURATION = BUILDER
            .comment("画饼充饥 I：每次命中恢复的饱食度")
            .defineInRange("illusoryFeast.level1Saturation", 0.5D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.IntValue LEVEL_2_FOOD = BUILDER
            .comment("画饼充饥 II：每次命中恢复的饥饿值")
            .defineInRange("illusoryFeast.level2Food", 2, 0, 20);
    private static final ForgeConfigSpec.DoubleValue LEVEL_2_SATURATION = BUILDER
            .comment("画饼充饥 II：每次命中恢复的饱食度")
            .defineInRange("illusoryFeast.level2Saturation", 1.0D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.IntValue LEVEL_3_FOOD = BUILDER
            .comment("画饼充饥 III：每次命中恢复的饥饿值")
            .defineInRange("illusoryFeast.level3Food", 3, 0, 20);
    private static final ForgeConfigSpec.DoubleValue LEVEL_3_SATURATION = BUILDER
            .comment("画饼充饥 III：每次命中恢复的饱食度")
            .defineInRange("illusoryFeast.level3Saturation", 1.5D, 0.0D, 20.0D);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private static boolean enabled = true;
    private static int level1Food = 1;
    private static float level1Saturation = 0.5F;
    private static int level2Food = 2;
    private static float level2Saturation = 1.0F;
    private static int level3Food = 3;
    private static float level3Saturation = 1.5F;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        // 仅刷新本配置，防止其他独立配置加载时覆盖缓存值。
        if (event.getConfig() == null || event.getConfig().getSpec() != SPEC) {
            return;
        }

        enabled = ENABLED.get();
        level1Food = LEVEL_1_FOOD.get();
        level1Saturation = LEVEL_1_SATURATION.get().floatValue();
        level2Food = LEVEL_2_FOOD.get();
        level2Saturation = LEVEL_2_SATURATION.get().floatValue();
        level3Food = LEVEL_3_FOOD.get();
        level3Saturation = LEVEL_3_SATURATION.get().floatValue();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static int getFood(int level) {
        return switch (level) {
            case 1 -> level1Food;
            case 2 -> level2Food;
            default -> level3Food;
        };
    }

    public static float getSaturation(int level) {
        return switch (level) {
            case 1 -> level1Saturation;
            case 2 -> level2Saturation;
            default -> level3Saturation;
        };
    }
}
