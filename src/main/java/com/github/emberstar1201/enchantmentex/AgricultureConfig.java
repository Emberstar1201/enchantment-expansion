package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「农业生产系」附魔独立配置文件
//
// 为三个农业附魔提供独立数值：
//   - 春华秋实（spring_harvest）：范围开垦与一键收获
//   - 万物回春（all_nature_revive）：范围催熟
//   - 丰饶之息（fertile_bounty）：生长光环 + 收获翻倍
//
// 配置路径：config/enchantment_expansion-agriculture.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AgricultureConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 一、春华秋实（Spring Harvest）
    // ================================================================
    private static final ForgeConfigSpec.IntValue HARVEST_RADIUS_L1 = BUILDER
            .comment("春华秋实：I级操作半径（3×3 网格，半径=1）")
            .defineInRange("springHarvest.radiusLevel1", 1, 1, 5);

    private static final ForgeConfigSpec.IntValue HARVEST_RADIUS_L2 = BUILDER
            .comment("春华秋实：II级操作半径（5×5 网格，半径=2）")
            .defineInRange("springHarvest.radiusLevel2", 2, 1, 8);

    private static final ForgeConfigSpec.IntValue HARVEST_DURABILITY_PER_BLOCK = BUILDER
            .comment("春华秋实：开垦时每块地消耗的锄头耐久（默认 1）")
            .defineInRange("springHarvest.durabilityPerTillBlock", 1, 0, 10);

    private static final ForgeConfigSpec.BooleanValue HARVEST_REPLANT = BUILDER
            .comment("春华秋实：收获后是否原地补种（默认 true）")
            .define("springHarvest.replant", true);

    // ================================================================
    // 二、万物回春（All Nature Revive）
    // ================================================================
    private static final ForgeConfigSpec.IntValue REVIVE_RADIUS_L1 = BUILDER
            .comment("万物回春：I级催熟半径（3×3 网格，半径=1）")
            .defineInRange("allNatureRevive.radiusLevel1", 1, 1, 5);

    private static final ForgeConfigSpec.IntValue REVIVE_RADIUS_L2 = BUILDER
            .comment("万物回春：II级催熟半径（5×5 网格，半径=2）")
            .defineInRange("allNatureRevive.radiusLevel2", 2, 1, 8);

    private static final ForgeConfigSpec.DoubleValue REVIVE_CHANCE_L1 = BUILDER
            .comment("万物回春：I级每个未成熟作物的催熟概率（默认 0.6 = 60%）")
            .defineInRange("allNatureRevive.chanceLevel1", 0.6, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue REVIVE_CHANCE_L2 = BUILDER
            .comment("万物回春：II级每个未成熟作物的催熟概率（默认 0.85 = 85%）")
            .defineInRange("allNatureRevive.chanceLevel2", 0.85, 0.0, 1.0);

    // ================================================================
    // 三、丰饶之息（Fertile Bounty）
    // ================================================================
    private static final ForgeConfigSpec.IntValue BOUNTY_AURA_RADIUS = BUILDER
            .comment("丰饶之息：生长光环半径（格，默认 10，含中心）")
            .defineInRange("fertileBounty.auraRadius", 10, 3, 32);

    private static final ForgeConfigSpec.IntValue BOUNTY_GROWTH_INTERVAL_TICKS = BUILDER
            .comment("丰饶之息：光环每多少 tick 判定一次催熟（默认 20 = 每秒）")
            .defineInRange("fertileBounty.growthIntervalTicks", 20, 4, 200);

    private static final ForgeConfigSpec.DoubleValue BOUNTY_GROWTH_CHANCE_L1 = BUILDER
            .comment("丰饶之息：I级每个未成熟作物的每轮催熟概率（默认 0.12）")
            .defineInRange("fertileBounty.growthChanceLevel1", 0.12, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue BOUNTY_GROWTH_CHANCE_L2 = BUILDER
            .comment("丰饶之息：II级每个未成熟作物的每轮催熟概率（默认 0.22）")
            .defineInRange("fertileBounty.growthChanceLevel2", 0.22, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue BOUNTY_DOUBLE_HARVEST = BUILDER
            .comment("丰饶之息：春华秋实一键收获时掉落翻倍（默认 true）")
            .define("fertileBounty.doubleHarvest", true);

    // 配置 SPEC 实例
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值
    // ================================================================
    public static int springHarvestRadiusLevel1;
    public static int springHarvestRadiusLevel2;
    public static int springHarvestDurabilityPerTillBlock;
    public static boolean springHarvestReplant;
    public static int allNatureReviveRadiusLevel1;
    public static int allNatureReviveRadiusLevel2;
    public static double allNatureReviveChanceLevel1;
    public static double allNatureReviveChanceLevel2;
    public static int fertileBountyAuraRadius;
    public static int fertileBountyGrowthIntervalTicks;
    public static double fertileBountyGrowthChanceLevel1;
    public static double fertileBountyGrowthChanceLevel2;
    public static boolean fertileBountyDoubleHarvest;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：只响应本配置的加载事件，
        //   防止其他配置加载时 `.get()` 抛 "Cannot get config value before config is loaded"
        if (event.getConfig() == null || event.getConfig().getSpec() != AgricultureConfig.SPEC) {
            return;
        }

        springHarvestRadiusLevel1 = HARVEST_RADIUS_L1.get();
        springHarvestRadiusLevel2 = HARVEST_RADIUS_L2.get();
        springHarvestDurabilityPerTillBlock = HARVEST_DURABILITY_PER_BLOCK.get();
        springHarvestReplant = HARVEST_REPLANT.get();
        allNatureReviveRadiusLevel1 = REVIVE_RADIUS_L1.get();
        allNatureReviveRadiusLevel2 = REVIVE_RADIUS_L2.get();
        allNatureReviveChanceLevel1 = REVIVE_CHANCE_L1.get();
        allNatureReviveChanceLevel2 = REVIVE_CHANCE_L2.get();
        fertileBountyAuraRadius = BOUNTY_AURA_RADIUS.get();
        fertileBountyGrowthIntervalTicks = BOUNTY_GROWTH_INTERVAL_TICKS.get();
        fertileBountyGrowthChanceLevel1 = BOUNTY_GROWTH_CHANCE_L1.get();
        fertileBountyGrowthChanceLevel2 = BOUNTY_GROWTH_CHANCE_L2.get();
        fertileBountyDoubleHarvest = BOUNTY_DOUBLE_HARVEST.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 春华秋实：给定等级返回操作半径 */
    public static int getSpringHarvestRadius(int level) {
        return switch (level) {
            case 1 -> springHarvestRadiusLevel1;
            case 2 -> springHarvestRadiusLevel2;
            default -> level > 2 ? springHarvestRadiusLevel2 : 0;
        };
    }

    /** 万物回春：给定等级返回催熟半径 */
    public static int getAllNatureReviveRadius(int level) {
        return switch (level) {
            case 1 -> allNatureReviveRadiusLevel1;
            case 2 -> allNatureReviveRadiusLevel2;
            default -> level > 2 ? allNatureReviveRadiusLevel2 : 0;
        };
    }

    /** 万物回春：给定等级返回催熟概率 */
    public static double getAllNatureReviveChance(int level) {
        return switch (level) {
            case 1 -> allNatureReviveChanceLevel1;
            case 2 -> allNatureReviveChanceLevel2;
            default -> level > 2 ? allNatureReviveChanceLevel2 : 0.0;
        };
    }

    /** 丰饶之息：给定等级返回光环催熟概率 */
    public static double getFertileBountyGrowthChance(int level) {
        return switch (level) {
            case 1 -> fertileBountyGrowthChanceLevel1;
            case 2 -> fertileBountyGrowthChanceLevel2;
            default -> level > 2 ? fertileBountyGrowthChanceLevel2 : 0.0;
        };
    }
}