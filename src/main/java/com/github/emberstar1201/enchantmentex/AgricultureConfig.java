package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「农业生产系」附魔独立配置文件
//
// 为四个农业附魔提供独立数值：
//   - 春华秋实（spring_harvest）：范围开垦与一键收获
//   - 万物回春（all_nature_revive）：范围催熟
//   - 丰饶之息（fertile_bounty）：生长光环 + 收获翻倍
//   - 渔获大师（fishing_master）：咬钩加速 + 战利品提效
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

    // ================================================================
    // 四、渔获大师（Fishing Master）
    // ================================================================
    // ★ 已废弃配置（仅保留键以兼容旧配置文件，不再被代码读取）：
    //   实际咬钩节奏改由下方 biteDelayMinSeconds / biteDelayMaxSeconds 控制
    private static final ForgeConfigSpec.IntValue FISHING_NIBBLE_ACCEL_L1 = BUILDER
            .comment("渔获大师：I级咬钩计时加速（★已废弃，保留仅兼容旧配置；当前节奏由鱼咬钩区间控制）")
            .defineInRange("fishingMaster.nibbleAccelLevel1", 2, 0, 80);

    private static final ForgeConfigSpec.IntValue FISHING_NIBBLE_ACCEL_L2 = BUILDER
            .comment("渔获大师：II级咬钩计时加速（★已废弃，保留仅兼容旧配置）")
            .defineInRange("fishingMaster.nibbleAccelLevel2", 3, 0, 80);

    private static final ForgeConfigSpec.IntValue FISHING_NIBBLE_ACCEL_L3 = BUILDER
            .comment("渔获大师：III级咬钩计时加速（★已废弃，保留仅兼容旧配置）")
            .defineInRange("fishingMaster.nibbleAccelLevel3", 4, 0, 80);

    private static final ForgeConfigSpec.BooleanValue FISHING_AUTO_REEL_ENABLED = BUILDER
            .comment("渔获大师：鱼咬钩后自动收杆（默认 true）",
                    "无需玩家右键，浮漂咬钩瞬间自动钓起，挂机钓鱼可用")
            .define("fishingMaster.autoReelEnabled", true);

    private static final ForgeConfigSpec.IntValue FISHING_BITE_DELAY_MIN_SECONDS = BUILDER
            .comment("渔获大师：咬钩等待区间下限（秒，默认 30）",
                    "抛竿入水后到咬钩的等待时间会被控制在 [min, max] 秒内随机")
            .defineInRange("fishingMaster.biteDelayMinSeconds", 30, 1, 600);

    private static final ForgeConfigSpec.IntValue FISHING_BITE_DELAY_MAX_SECONDS = BUILDER
            .comment("渔获大师：咬钩等待区间上限（秒，默认 60）")
            .defineInRange("fishingMaster.biteDelayMaxSeconds", 60, 1, 600);

    private static final ForgeConfigSpec.DoubleValue FISHING_BOOK_CHANCE_L2 = BUILDER
            .comment("渔获大师：II级钓起时追加附魔书的概率（默认 0.15 = 15%）")
            .defineInRange("fishingMaster.enchantedBookChanceLevel2", 0.15, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FISHING_NAMED_CHANCE_L2 = BUILDER
            .comment("渔获大师：II级钓起时追加命名牌的概率（默认 0.05 = 5%）")
            .defineInRange("fishingMaster.nameTagChanceLevel2", 0.05, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FISHING_BOOK_CHANCE_L3 = BUILDER
            .comment("渔获大师：III级钓起时追加附魔书的概率（默认 0.35 = 35%）")
            .defineInRange("fishingMaster.enchantedBookChanceLevel3", 0.35, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FISHING_NAMED_CHANCE_L3 = BUILDER
            .comment("渔获大师：III级钓起时追加命名牌的概率（默认 0.12 = 12%）")
            .defineInRange("fishingMaster.nameTagChanceLevel3", 0.12, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FISHING_DIAMOND_CHANCE_L3 = BUILDER
            .comment("渔获大师：III级钓起时追加钻石的概率（默认 0.08 = 8%）")
            .defineInRange("fishingMaster.diamondChanceLevel3", 0.08, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue FISHING_LAPIS_COUNT_L3 = BUILDER
            .comment("渔获大师：III级钓起时追加青金石块的期望（按数量随机，默认 2）")
            .defineInRange("fishingMaster.lapisCountLevel3", 2.0, 0.0, 64.0);

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
    public static int fishingMasterNibbleAccelLevel1;
    public static int fishingMasterNibbleAccelLevel2;
    public static int fishingMasterNibbleAccelLevel3;
    public static boolean fishingMasterAutoReelEnabled;
    public static int fishingMasterBiteDelayMinSeconds;
    public static int fishingMasterBiteDelayMaxSeconds;
    public static double fishingMasterBookChanceLevel2;
    public static double fishingMasterNameTagChanceLevel2;
    public static double fishingMasterBookChanceLevel3;
    public static double fishingMasterNameTagChanceLevel3;
    public static double fishingMasterDiamondChanceLevel3;
    public static double fishingMasterLapisCountLevel3;

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
        fishingMasterNibbleAccelLevel1 = FISHING_NIBBLE_ACCEL_L1.get();
        fishingMasterNibbleAccelLevel2 = FISHING_NIBBLE_ACCEL_L2.get();
        fishingMasterNibbleAccelLevel3 = FISHING_NIBBLE_ACCEL_L3.get();
        fishingMasterAutoReelEnabled = FISHING_AUTO_REEL_ENABLED.get();
        fishingMasterBiteDelayMinSeconds = FISHING_BITE_DELAY_MIN_SECONDS.get();
        fishingMasterBiteDelayMaxSeconds = FISHING_BITE_DELAY_MAX_SECONDS.get();
        fishingMasterBookChanceLevel2 = FISHING_BOOK_CHANCE_L2.get();
        fishingMasterNameTagChanceLevel2 = FISHING_NAMED_CHANCE_L2.get();
        fishingMasterBookChanceLevel3 = FISHING_BOOK_CHANCE_L3.get();
        fishingMasterNameTagChanceLevel3 = FISHING_NAMED_CHANCE_L3.get();
        fishingMasterDiamondChanceLevel3 = FISHING_DIAMOND_CHANCE_L3.get();
        fishingMasterLapisCountLevel3 = FISHING_LAPIS_COUNT_L3.get();
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

    /** 渔获大师：给定等级返回咬钩计时加速（每 tick 额外减少的等待，已废弃保留） */
    public static int getFishingNibbleAccel(int level) {
        return switch (level) {
            case 1 -> fishingMasterNibbleAccelLevel1;
            case 2 -> fishingMasterNibbleAccelLevel2;
            case 3 -> fishingMasterNibbleAccelLevel3;
            default -> level > 3 ? fishingMasterNibbleAccelLevel3 : 0;
        };
    }

    /** 渔获大师：是否启用咬钩自动收杆 */
    public static boolean getFishingAutoReelEnabled() {
        return fishingMasterAutoReelEnabled;
    }

    /** 渔获大师：咬钩等待区间下限（tick，20 tick = 1 秒） */
    public static int getFishingBiteDelayMinTicks() {
        return fishingMasterBiteDelayMinSeconds * 20;
    }

    /** 渔获大师：咬钩等待区间上限（tick，20 tick = 1 秒） */
    public static int getFishingBiteDelayMaxTicks() {
        return fishingMasterBiteDelayMaxSeconds * 20;
    }
}