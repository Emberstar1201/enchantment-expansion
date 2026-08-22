package com.github.emberstar1201.enchantmentex;

import net.minecraft.util.RandomSource;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「强夺」附魔独立配置文件
//
// 为强夺附魔的稀有战利品保底与怪物装备掉落提供独立开关/数值，
// 服务器管理员可单独调整，不污染主 Config.java。
//
// 配置路径：config/enchantment_expansion-plunder.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlunderConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 一、稀有战利品保底（概率 + 数量范围）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue RARE_LOOT_CHANCE_L1 = BUILDER
            .comment("强夺：I级稀有战利品保底概率（默认 0.75 = 75%）")
            .defineInRange("plunder.rareLootChanceLevel1", 0.75, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue RARE_LOOT_CHANCE_L2 = BUILDER
            .comment("强夺：II级稀有战利品保底概率（默认 1.0 = 100%）")
            .defineInRange("plunder.rareLootChanceLevel2", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue RARE_LOOT_CHANCE_L3 = BUILDER
            .comment("强夺：III级稀有战利品保底概率（默认 1.0 = 100%）")
            .defineInRange("plunder.rareLootChanceLevel3", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MIN_L1 = BUILDER
            .comment("强夺：I级稀有战利品掉落数量下限（默认 1）")
            .defineInRange("plunder.rareLootMinCountLevel1", 1, 1, 100);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MAX_L1 = BUILDER
            .comment("强夺：I级稀有战利品掉落数量上限（默认 2）")
            .defineInRange("plunder.rareLootMaxCountLevel1", 2, 1, 100);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MIN_L2 = BUILDER
            .comment("强夺：II级稀有战利品掉落数量下限（默认 2）")
            .defineInRange("plunder.rareLootMinCountLevel2", 2, 1, 100);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MAX_L2 = BUILDER
            .comment("强夺：II级稀有战利品掉落数量上限（默认 3）")
            .defineInRange("plunder.rareLootMaxCountLevel2", 3, 1, 100);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MIN_L3 = BUILDER
            .comment("强夺：III级稀有战利品掉落数量下限（默认 3）")
            .defineInRange("plunder.rareLootMinCountLevel3", 3, 1, 100);

    private static final ForgeConfigSpec.IntValue RARE_COUNT_MAX_L3 = BUILDER
            .comment("强夺：III级稀有战利品掉落数量上限（默认 4）")
            .defineInRange("plunder.rareLootMaxCountLevel3", 4, 1, 100);

    // ================================================================
    // 二、怪物武器/盔甲掉落（掉落率 + 耐久度范围）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue EQUIP_DROP_CHANCE_L1 = BUILDER
            .comment("强夺：I级怪物武器/盔甲掉落率（默认 0.6 = 60%，大概率掉落）")
            .defineInRange("plunder.equipDropChanceLevel1", 0.6, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DROP_CHANCE_L2 = BUILDER
            .comment("强夺：II级怪物武器/盔甲掉落率（默认 1.0 = 100%）")
            .defineInRange("plunder.equipDropChanceLevel2", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DROP_CHANCE_L3 = BUILDER
            .comment("强夺：III级怪物武器/盔甲掉落率（默认 1.0 = 100%）")
            .defineInRange("plunder.equipDropChanceLevel3", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MIN_L1 = BUILDER
            .comment("强夺：I级掉落耐久度下限（剩余耐久比例，默认 0.2 = 20%）")
            .defineInRange("plunder.equipDurabilityMinLevel1", 0.2, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MAX_L1 = BUILDER
            .comment("强夺：I级掉落耐久度上限（剩余耐久比例，默认 0.3 = 30%，通常约 25%）")
            .defineInRange("plunder.equipDurabilityMaxLevel1", 0.3, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_FULL_DUR_CHANCE_L1 = BUILDER
            .comment("强夺：I级掉落满耐久概率（默认 0.02 = 2%，极低概率满耐久）")
            .defineInRange("plunder.equipFullDurabilityChanceLevel1", 0.02, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MIN_L2 = BUILDER
            .comment("强夺：II级掉落耐久度下限（剩余耐久比例，默认 0.5 = 50%）")
            .defineInRange("plunder.equipDurabilityMinLevel2", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MAX_L2 = BUILDER
            .comment("强夺：II级掉落耐久度上限（剩余耐久比例，默认 0.75 = 75%）")
            .defineInRange("plunder.equipDurabilityMaxLevel2", 0.75, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MIN_L3 = BUILDER
            .comment("强夺：III级掉落耐久度下限（剩余耐久比例，默认 1.0 = 满耐久）")
            .defineInRange("plunder.equipDurabilityMinLevel3", 1.0, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EQUIP_DUR_MAX_L3 = BUILDER
            .comment("强夺：III级掉落耐久度上限（剩余耐久比例，默认 1.0 = 满耐久）")
            .defineInRange("plunder.equipDurabilityMaxLevel3", 1.0, 0.0, 1.0);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值（运行时读取这些字段）
    // ================================================================
    public static double rareLootChanceLevel1;
    public static double rareLootChanceLevel2;
    public static double rareLootChanceLevel3;
    public static int rareLootMinCountLevel1;
    public static int rareLootMaxCountLevel1;
    public static int rareLootMinCountLevel2;
    public static int rareLootMaxCountLevel2;
    public static int rareLootMinCountLevel3;
    public static int rareLootMaxCountLevel3;
    public static double equipDropChanceLevel1;
    public static double equipDropChanceLevel2;
    public static double equipDropChanceLevel3;
    public static double equipDurabilityMinLevel1;
    public static double equipDurabilityMaxLevel1;
    public static double equipFullDurabilityChanceLevel1;
    public static double equipDurabilityMinLevel2;
    public static double equipDurabilityMaxLevel2;
    public static double equipDurabilityMinLevel3;
    public static double equipDurabilityMaxLevel3;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：本类会被自动注册到所有 MOD 配置的加载事件上，
        //   只有当事件对应的就是「强夺」这份配置时才读取数值。
        //   否则在其他配置加载时，本类尚未加载的 ConfigValue 直接 .get()
        //   会抛 "Cannot get config value before config is loaded"。
        if (event.getConfig() == null || event.getConfig().getSpec() != PlunderConfig.SPEC) {
            return;
        }

        rareLootChanceLevel1 = RARE_LOOT_CHANCE_L1.get();
        rareLootChanceLevel2 = RARE_LOOT_CHANCE_L2.get();
        rareLootChanceLevel3 = RARE_LOOT_CHANCE_L3.get();
        rareLootMinCountLevel1 = RARE_COUNT_MIN_L1.get();
        rareLootMaxCountLevel1 = RARE_COUNT_MAX_L1.get();
        rareLootMinCountLevel2 = RARE_COUNT_MIN_L2.get();
        rareLootMaxCountLevel2 = RARE_COUNT_MAX_L2.get();
        rareLootMinCountLevel3 = RARE_COUNT_MIN_L3.get();
        rareLootMaxCountLevel3 = RARE_COUNT_MAX_L3.get();
        equipDropChanceLevel1 = EQUIP_DROP_CHANCE_L1.get();
        equipDropChanceLevel2 = EQUIP_DROP_CHANCE_L2.get();
        equipDropChanceLevel3 = EQUIP_DROP_CHANCE_L3.get();
        equipDurabilityMinLevel1 = EQUIP_DUR_MIN_L1.get();
        equipDurabilityMaxLevel1 = EQUIP_DUR_MAX_L1.get();
        equipFullDurabilityChanceLevel1 = EQUIP_FULL_DUR_CHANCE_L1.get();
        equipDurabilityMinLevel2 = EQUIP_DUR_MIN_L2.get();
        equipDurabilityMaxLevel2 = EQUIP_DUR_MAX_L2.get();
        equipDurabilityMinLevel3 = EQUIP_DUR_MIN_L3.get();
        equipDurabilityMaxLevel3 = EQUIP_DUR_MAX_L3.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 给定附魔等级，返回稀有战利品保底概率（未附魔=0，等级超界钳制） */
    public static double getRareLootChance(int level) {
        return switch (level) {
            case 1 -> rareLootChanceLevel1;
            case 2 -> rareLootChanceLevel2;
            case 3 -> rareLootChanceLevel3;
            default -> level > 3 ? rareLootChanceLevel3 : 0.0;
        };
    }

    /** 给定附魔等级，随机掷出稀有战利品掉落数量（闭区间 [min, max]） */
    public static int rollRareLootCount(int level, RandomSource random) {
        int min, max;
        switch (level) {
            case 1 -> { min = rareLootMinCountLevel1; max = rareLootMaxCountLevel1; }
            case 2 -> { min = rareLootMinCountLevel2; max = rareLootMaxCountLevel2; }
            case 3 -> { min = rareLootMinCountLevel3; max = rareLootMaxCountLevel3; }
            default -> {
                if (level > 3) { min = rareLootMinCountLevel3; max = rareLootMaxCountLevel3; }
                else return 0;
            }
        }
        if (max < min) max = min;
        return min + random.nextInt(max - min + 1);
    }

    /** 给定附魔等级，返回怪物武器/盔甲掉落率（未附魔=0，等级超界钳制） */
    public static double getEquipDropChance(int level) {
        return switch (level) {
            case 1 -> equipDropChanceLevel1;
            case 2 -> equipDropChanceLevel2;
            case 3 -> equipDropChanceLevel3;
            default -> level > 3 ? equipDropChanceLevel3 : 0.0;
        };
    }

    /** 给定附魔等级，随机掷出掉落装备的剩余耐久比例（闭区间） */
    public static double rollEquipDurability(int level, RandomSource random) {
        if (level == 1) {
            // I级：通常 25% 左右，极低概率满耐久
            if (random.nextFloat() < equipFullDurabilityChanceLevel1) {
                return 1.0;
            }
            double min = equipDurabilityMinLevel1, max = equipDurabilityMaxLevel1;
            if (max < min) max = min;
            if (max <= min) return min;
            return min + random.nextDouble() * (max - min);
        }
        double min, max;
        switch (level) {
            case 2 -> { min = equipDurabilityMinLevel2; max = equipDurabilityMaxLevel2; }
            case 3 -> { min = equipDurabilityMinLevel3; max = equipDurabilityMaxLevel3; }
            default -> { return 1.0; }
        }
        if (max < min) max = min;
        if (max <= min) return min;
        return min + random.nextDouble() * (max - min);
    }
}