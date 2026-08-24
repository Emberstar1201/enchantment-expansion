package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「贯穿链条」附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-chain_arrow.toml
//
// 配置项：
//   1. chainArrow.enabled          ：附魔总开关（默认 true）
//   2. chainArrow.bounceCountL1/2/3：弹射次数（默认 1/2/3）
//   3. chainArrow.damageRatio      ：每次弹射伤害占箭基础伤害的比例（默认 0.8）
//   4. chainArrow.searchRange      ：寻找下一目标的搜索半径（格，默认 10）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChainArrowConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("贯穿链条：附魔总开关（默认 true）。设为 false 可整体失效。")
            .define("chainArrow.enabled", true);

    private static final ForgeConfigSpec.IntValue BOUNCE_L1 = BUILDER
            .comment("贯穿链条：I 级弹射次数（命中后继续弹射 1 次，共命中 2 个目标）")
            .defineInRange("chainArrow.bounceCountLevel1", 1, 0, 10);

    private static final ForgeConfigSpec.IntValue BOUNCE_L2 = BUILDER
            .comment("贯穿链条：II 级弹射次数（弹射 2 次，共命中 3 个目标）")
            .defineInRange("chainArrow.bounceCountLevel2", 2, 0, 10);

    private static final ForgeConfigSpec.IntValue BOUNCE_L3 = BUILDER
            .comment("贯穿链条：III 级弹射次数（弹射 3 次，共命中 4 个目标）")
            .defineInRange("chainArrow.bounceCountLevel3", 3, 0, 10);

    private static final ForgeConfigSpec.DoubleValue DAMAGE_RATIO = BUILDER
            .comment("贯穿链条：每次弹射伤害占箭基础伤害的比例（默认 0.8 = 80%）")
            .defineInRange("chainArrow.damageRatio", 0.8, 0.1, 2.0);

    private static final ForgeConfigSpec.DoubleValue SEARCH_RANGE = BUILDER
            .comment("贯穿链条：寻找下一弹射目标的搜索半径（格，默认 10.0）")
            .defineInRange("chainArrow.searchRange", 10.0, 1.0, 64.0);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 运行时缓存字段
    public static boolean enabled;
    public static int bounceCountLevel1;
    public static int bounceCountLevel2;
    public static int bounceCountLevel3;
    public static double damageRatio;
    public static double searchRange;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 必须过滤：仅处理本配置的加载事件
        if (event.getConfig() == null || event.getConfig().getSpec() != ChainArrowConfig.SPEC) {
            return;
        }
        enabled = ENABLED.get();
        bounceCountLevel1 = BOUNCE_L1.get();
        bounceCountLevel2 = BOUNCE_L2.get();
        bounceCountLevel3 = BOUNCE_L3.get();
        damageRatio = DAMAGE_RATIO.get();
        searchRange = SEARCH_RANGE.get();
    }

    /** 给定等级返回弹射次数（即额外命中目标数），超界钳制到 III */
    public static int getBounceCount(int level) {
        return switch (level) {
            case 1 -> bounceCountLevel1;
            case 2 -> bounceCountLevel2;
            default -> bounceCountLevel3;
        };
    }
}