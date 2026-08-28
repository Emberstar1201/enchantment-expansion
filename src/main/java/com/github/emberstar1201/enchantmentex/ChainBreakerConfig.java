package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「连锁挖掘」（chain_breaker）附魔独立配置文件
//
// 数值：
//   - 各等级连锁范围（立方体边长，格）
//   - 每个额外方块的耐久消耗
//   - 单次挖掘最大耐久消耗（低耐久保护，默认 ≤ 10）
//   - 附魔总开关
//
// 配置路径：config/enchantment_expansion-chain_breaker.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChainBreakerConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 一、总开关
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue CHAIN_BREAKER_ENABLED = BUILDER
            .comment("连锁挖掘：是否启用（默认 true）")
            .define("chainBreaker.enabled", true);

    // ================================================================
    // 二、各等级连锁范围（立方体边长，格）
    // ================================================================
    private static final ForgeConfigSpec.IntValue RADIUS_LEVEL1 = BUILDER
            .comment("连锁挖掘：I级连锁范围边长（默认 3 = 3×3×3，即半径 1）")
            .defineInRange("chainBreaker.sizeLevel1", 3, 3, 33);

    private static final ForgeConfigSpec.IntValue RADIUS_LEVEL2 = BUILDER
            .comment("连锁挖掘：II级连锁范围边长（默认 5 = 5×5×5，即半径 2）")
            .defineInRange("chainBreaker.sizeLevel2", 5, 3, 33);

    private static final ForgeConfigSpec.IntValue RADIUS_LEVEL3 = BUILDER
            .comment("连锁挖掘：III级连锁范围边长（默认 9 = 9×9×9，即半径 4）")
            .defineInRange("chainBreaker.sizeLevel3", 9, 3, 33);

    // ================================================================
    // 三、耐久消耗
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue DURABILITY_PER_BLOCK = BUILDER
            .comment("连锁挖掘：每个额外连锁破坏的方块消耗的耐久（默认 0.5）",
                    "总消耗 = 额外方块数 × 该值（向上取整，最低 1 点）")
            .defineInRange("chainBreaker.durabilityPerBlock", 0.5, 0.1, 5.0);

    private static final ForgeConfigSpec.IntValue MAX_DURABILITY_COST = BUILDER
            .comment("连锁挖掘：单次挖掘最大耐久消耗上限（默认 10，低耐久保护）",
                    "超出部分不会消耗，避免工具一次就被挖爆")
            .defineInRange("chainBreaker.maxDurabilityCost", 10, 1, 100);

    // 配置 SPEC 实例
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值
    // ================================================================
    public static boolean chainBreakerEnabled;
    public static int chainBreakerSizeLevel1;
    public static int chainBreakerSizeLevel2;
    public static int chainBreakerSizeLevel3;
    public static double chainBreakerDurabilityPerBlock;
    public static int chainBreakerMaxDurabilityCost;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：只响应本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != ChainBreakerConfig.SPEC) {
            return;
        }

        chainBreakerEnabled = CHAIN_BREAKER_ENABLED.get();
        chainBreakerSizeLevel1 = RADIUS_LEVEL1.get();
        chainBreakerSizeLevel2 = RADIUS_LEVEL2.get();
        chainBreakerSizeLevel3 = RADIUS_LEVEL3.get();
        chainBreakerDurabilityPerBlock = DURABILITY_PER_BLOCK.get();
        chainBreakerMaxDurabilityCost = MAX_DURABILITY_COST.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /**
     * 根据附魔等级返回连锁范围边长（格）
     */
    public static int getSize(int level) {
        return switch (level) {
            case 1 -> chainBreakerSizeLevel1;
            case 2 -> chainBreakerSizeLevel2;
            case 3 -> chainBreakerSizeLevel3;
            default -> level > 3 ? chainBreakerSizeLevel3 : 0;
        };
    }

    /**
     * 根据附魔等级返回连锁半径（从目标方块向外的格数）
     * 边长 3 → 半径 1，边长 5 → 半径 2，边长 9 → 半径 4
     */
    public static int getRadius(int level) {
        int size = getSize(level);
        if (size <= 0) return 0;
        return (size - 1) / 2;
    }

    /** 是否启用连锁挖掘附魔 */
    public static boolean isEnabled() {
        return chainBreakerEnabled;
    }
}