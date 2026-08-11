package com.github.emberstar1201.enchantmentex.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;

// ========================================================================
// 终界之星配置类
//
// 在 Common 配置中注册所有终界之星相关的可调参数。
// 生成的配置文件路径：config/enchantment_expansion-common.toml
// ========================================================================
public class EndStarConfig {

    // ForgeConfigSpec.Builder：构建配置规范
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // 公共访问的 ForgeConfigSpec（通过 register 注册时传入）
    public static final ForgeConfigSpec SPEC;

    // ================================================================
    // 配置项
    // ================================================================

    // 1. 是否允许终界之星赋予玩家飞行能力
    //    默认 true（开启）
    public static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_FLIGHT;

    // 2. 是否允许终界之星赋予玩家减伤能力
    //    默认 true（开启）
    public static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_DAMAGE_REDUCTION;

    // 3. 减伤百分比（0.0 = 无减伤，1.0 = 无敌）
    //    默认 0.8 即 80%（用户要求）
    //    范围：0.0 到 1.0
    //    覆盖所有伤害类型，包括监守者音波伤害 SONIC_BOOM
    public static final ForgeConfigSpec.DoubleValue END_STAR_DAMAGE_REDUCTION_PERCENT;

    // 4. 是否允许终界之星根据经验等级提供伤害加成
    //    默认 true（开启）
    public static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_DAMAGE_BONUS;

    // 5. 经验等级伤害加成上限（百分比）
    //    默认 1000（即 1000% 加成，100 级达到上限）
    //    每 10 级提供 100% 加成
    public static final ForgeConfigSpec.IntValue END_STAR_MAX_BONUS_PERCENT;

    // 6. 终界之星生成后多少 tick 可拾取
    //    默认 100 tick = 5 秒（用户要求）
    public static final ForgeConfigSpec.IntValue END_STAR_DROP_DELAY;

    // 7. 终界之星生成后多少 tick 自动消失
    //    默认 12000 tick = 10 分钟（20 tick × 60秒 × 10分）
    public static final ForgeConfigSpec.IntValue END_STAR_DESPAWN_TIME;

    // 静态初始化块：构建配置规范
    static {
        // 配置分组：End Star（终界之星）
        BUILDER.push("End Star");

        ENABLE_END_STAR_FLIGHT = BUILDER
                .comment("是否允许手持终界之星时获得创造模式飞行能力（默认 true）")
                .define("enable_end_star_flight", true);

        ENABLE_END_STAR_DAMAGE_REDUCTION = BUILDER
                .comment("是否允许手持终界之星时获得伤害减免（默认 true）")
                .define("enable_end_star_damage_reduction", true);

        END_STAR_DAMAGE_REDUCTION_PERCENT = BUILDER
                .comment("终界之星减伤百分比（0.0=无减伤，1.0=无敌，默认 0.8 即 80%）",
                        "覆盖所有伤害类型，包括监守者音波伤害 SONIC_BOOM")
                .defineInRange("end_star_damage_reduction_percent", 0.8, 0.0, 1.0);

        ENABLE_END_STAR_DAMAGE_BONUS = BUILDER
                .comment("是否允许手持终界之星时根据经验等级获得伤害加成（默认 true）",
                        "加成公式：每 10 级 +100% 伤害，上限 1000%（100 级达到上限）")
                .define("enable_end_star_damage_bonus", true);

        END_STAR_MAX_BONUS_PERCENT = BUILDER
                .comment("经验等级伤害加成上限（百分比，默认 1000 即 1000% 加成）",
                        "100 级玩家达到上限：floor(100/10)*100 = 1000")
                .defineInRange("end_star_max_bonus_percent", 1000, 0, 100000);

        END_STAR_DROP_DELAY = BUILDER
                .comment("终界之星生成后多少 tick 可拾取（默认 100 = 5秒）")
                .defineInRange("end_star_drop_delay", 100, 0, 72000);

        END_STAR_DESPAWN_TIME = BUILDER
                .comment("终界之星生成后多少 tick 自动消失（默认 12000 = 10分钟）")
                .defineInRange("end_star_despawn_time", 12000, 20, 1728000);

        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    // ========================================================================
    // 注册方法：在主类构造函数中调用
    // 将本配置（COMMON 类型）绑定到模组加载上下文
    // ========================================================================
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
