package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

// ========================================================================
// 「斯安维斯坦」（Sandevistan）附魔独立配置文件
//
// 配置路径：config/enchantment_expansion-sandevistan.toml
//
// 效果概述：
//   胸甲附魔，按键（默认 X）激活后短时间内：
//     - 范围内世界时间减缓（怪物按 1/N 速度行动、其他玩家被减速、
//       弹射物/掉落物慢动作）
//     - 激活者自身获得移速与攻速加成
//   持续结束后进入冷却，激活一次性消耗少量饥饿值。
//
// 等级数值均为三元素列表，依次对应 I / II / III 级：
//   durationsSeconds       持续秒数          [3.0, 4.0, 5.0]
//   cooldownsSeconds       冷却秒数          [60.0, 45.0, 30.0]
//   timeScales             世界时缓倍率      [0.50, 0.34, 0.25]
//                            （0.25 = 世界以 1/4 速度运转）
//   selfSpeedBonus         激活者移速加成    [0.30, 0.40, 0.50]
//   selfAttackSpeedBonus   激活者攻速加成    [0.20, 0.35, 0.50]
//   hungerCosts            激活饥饿消耗      [2, 2, 1]（单位：鸡腿）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SandevistanConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ========================================================================
    // 一、范围与作用对象开关
    // ========================================================================
    // 注意：时缓影响范围以「区块」为单位，而非格/方块。
    // 以激活者所在区块为中心，向 X / Z 各延伸 radiusChunksX / radiusChunksZ
    // 个区块（1 区块 = 16 格），默认 2×2 = 以玩家为中心 5×5 区块（80×80 格）。
    private static final ForgeConfigSpec.IntValue RADIUS_CHUNKS_X = BUILDER
            .comment("斯安维斯坦：时缓影响范围（X 方向，区块数，默认 2）。",
                    "以激活者所在区块为中心，X 方向各延伸 N 个区块（1 区块 = 16 格）。",
                    "范围 0 ~ 32 区块（0 表示只作用于玩家所在区块）。")
            .defineInRange("sandevistan.radiusChunksX", 2, 0, 32);

    private static final ForgeConfigSpec.IntValue RADIUS_CHUNKS_Z = BUILDER
            .comment("斯安维斯坦：时缓影响范围（Z 方向，区块数，默认 2）。",
                    "与 radiusChunksX 同理，Z 方向各延伸 N 个区块。")
            .defineInRange("sandevistan.radiusChunksZ", 2, 0, 32);

    private static final ForgeConfigSpec.BooleanValue AFFECT_PLAYERS = BUILDER
            .comment("斯安维斯坦：是否减速范围内的其他玩家（默认 true）。",
                    "设为 false 则时缓只对怪物/弹射物生效，不影响其他玩家。")
            .define("sandevistan.affectPlayers", true);

    private static final ForgeConfigSpec.BooleanValue AFFECT_PROJECTILES = BUILDER
            .comment("斯安维斯坦：是否减速范围内的弹射物（箭、三叉戟等，默认 true）。",
                    "激活者自己发射的弹射物不受影响。")
            .define("sandevistan.affectProjectiles", true);

    private static final ForgeConfigSpec.BooleanValue AFFECT_ITEMS = BUILDER
            .comment("斯安维斯坦：是否让范围内的掉落物/经验球也慢动作（默认 false）。",
                    "纯粹视觉效果，开启后掉落物会缓慢飘落。")
            .define("sandevistan.affectItems", false);

    private static final ForgeConfigSpec.BooleanValue AFFECT_SELF_PROJECTILES = BUILDER
            .comment("斯安维斯坦：是否也让激活者自己发射的弹射物减速（默认 true）。",
                    "默认 true：连自己射出的箭/弩矢也慢动作，完整呈现「世界时间减缓」",
                    "（子弹时间里子弹/飞箭本身就该悬浮慢放）。",
                    "若想保留「只有我快」的爽快手感，可改为 false。")
            .define("sandevistan.affectSelfProjectiles", true);

    private static final ForgeConfigSpec.BooleanValue AFFECT_BOSSES = BUILDER
            .comment("斯安维斯坦：Boss（末影龙、凋灵）是否受影响（默认 true）。",
                    "Boss 不会被冻结 tick（避免 AI 阶段异常），只施加缓慢效果。")
            .define("sandevistan.affectBosses", true);

    // ========================================================================
    // 二、三级数值（列表下标 0/1/2 = I/II/III 级）
    // ========================================================================
    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> DURATIONS = BUILDER
            .comment("斯安维斯坦：各级持续时间（秒），依次为 I/II/III 级，默认 [3.0, 4.0, 5.0]")
            .defineList("sandevistan.durationsSeconds",
                    List.of(3.0, 4.0, 5.0),
                    o -> o instanceof Number n && n.doubleValue() >= 0.5 && n.doubleValue() <= 30.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> COOLDOWNS = BUILDER
            .comment("斯安维斯坦：各级冷却时间（秒），依次为 I/II/III 级，默认 [60.0, 45.0, 30.0]")
            .defineList("sandevistan.cooldownsSeconds",
                    List.of(60.0, 45.0, 30.0),
                    o -> o instanceof Number n && n.doubleValue() >= 0.0 && n.doubleValue() <= 600.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> TIME_SCALES = BUILDER
            .comment("斯安维斯坦：各级世界时缓倍率，依次为 I/II/III 级，默认 [0.50, 0.34, 0.25]。",
                    "值越小世界越慢：0.5 = 半速，0.25 = 四分之一速。范围 0.05 ~ 0.95。")
            .defineList("sandevistan.timeScales",
                    List.of(0.50, 0.34, 0.25),
                    o -> o instanceof Number n && n.doubleValue() >= 0.05 && n.doubleValue() <= 0.95);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SELF_SPEED = BUILDER
            .comment("斯安维斯坦：各级激活者自身移速加成（比例），默认 [0.30, 0.40, 0.50]（+30%/+40%/+50%）")
            .defineList("sandevistan.selfSpeedBonus",
                    List.of(0.30, 0.40, 0.50),
                    o -> o instanceof Number n && n.doubleValue() >= 0.0 && n.doubleValue() <= 5.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> SELF_ATTACK_SPEED = BUILDER
            .comment("斯安维斯坦：各级激活者自身攻速加成（比例），默认 [0.20, 0.35, 0.50]（+20%/+35%/+50%）")
            .defineList("sandevistan.selfAttackSpeedBonus",
                    List.of(0.20, 0.35, 0.50),
                    o -> o instanceof Number n && n.doubleValue() >= 0.0 && n.doubleValue() <= 5.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Number>> HUNGER_COSTS = BUILDER
            .comment("斯安维斯坦：各级激活时一次性消耗的饥饿值（鸡腿数），默认 [2, 2, 1]")
            .defineList("sandevistan.hungerCosts",
                    List.of(2, 2, 1),
                    o -> o instanceof Number n && n.intValue() >= 0 && n.intValue() <= 10);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ========================================================================
    // 运行时缓存字段（配置加载后才有效）
    // ========================================================================
    /** 时缓影响范围：X 方向半宽（区块数） */
    public static int radiusChunksX;
    /** 时缓影响范围：Z 方向半宽（区块数） */
    public static int radiusChunksZ;
    public static boolean affectPlayers;
    public static boolean affectProjectiles;
    public static boolean affectItems;
    public static boolean affectSelfProjectiles;
    public static boolean affectBosses;

    /** 各级持续时间（tick），下标 0/1/2 = I/II/III */
    public static int[] durationTicks = new int[3];
    /** 各级冷却时间（tick） */
    public static int[] cooldownTicks = new int[3];
    /** 各级世界时缓倍率 */
    public static double[] timeScales = new double[3];
    /** 各级激活者移速加成 */
    public static double[] selfSpeedBonus = new double[3];
    /** 各级激活者攻速加成 */
    public static double[] selfAttackSpeedBonus = new double[3];
    /** 各级激活饥饿消耗 */
    public static int[] hungerCosts = new int[3];

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：必须确认是本配置，否则其他配置加载时 get() 会抛异常
        if (event.getConfig() == null || event.getConfig().getSpec() != SandevistanConfig.SPEC) {
            return;
        }

        radiusChunksX = RADIUS_CHUNKS_X.get();
        radiusChunksZ = RADIUS_CHUNKS_Z.get();
        affectPlayers = AFFECT_PLAYERS.get();
        affectProjectiles = AFFECT_PROJECTILES.get();
        affectItems = AFFECT_ITEMS.get();
        affectSelfProjectiles = AFFECT_SELF_PROJECTILES.get();
        affectBosses = AFFECT_BOSSES.get();

        copyTicks(DURATIONS.get(), durationTicks, 20.0);    // 秒 → tick（×20）
        copyTicks(COOLDOWNS.get(), cooldownTicks, 20.0);
        copyDoubles(TIME_SCALES.get(), timeScales);
        copyDoubles(SELF_SPEED.get(), selfSpeedBonus);
        copyDoubles(SELF_ATTACK_SPEED.get(), selfAttackSpeedBonus);

        List<? extends Number> costs = HUNGER_COSTS.get();
        for (int i = 0; i < 3; i++) {
            hungerCosts[i] = costs.get(i).intValue();
        }
    }

    /** 秒数列表 → tick 数组（不足 3 项时用末项补齐） */
    private static void copyTicks(List<? extends Number> src, int[] dst, double multiplier) {
        for (int i = 0; i < 3; i++) {
            int idx = Math.min(i, src.size() - 1);
            dst[i] = (int) Math.round(src.get(idx).doubleValue() * multiplier);
        }
    }

    /** Number 列表 → double 数组（不足 3 项时用末项补齐） */
    private static void copyDoubles(List<? extends Number> src, double[] dst) {
        for (int i = 0; i < 3; i++) {
            int idx = Math.min(i, src.size() - 1);
            dst[i] = src.get(idx).doubleValue();
        }
    }

    // ========================================================================
    // 工具查询方法（等级 1~3，越界自动钳制）
    // ========================================================================

    /** 该级持续时间（tick） */
    public static int getDurationTicks(int level) {
        return durationTicks[clampLevel(level) - 1];
    }

    /** 该级冷却时间（tick） */
    public static int getCooldownTicks(int level) {
        return cooldownTicks[clampLevel(level) - 1];
    }

    /** 该级世界时缓倍率（0.05~0.95） */
    public static double getTimeScale(int level) {
        return timeScales[clampLevel(level) - 1];
    }

    /** 该级激活者移速加成（比例，如 0.5 = +50%） */
    public static double getSelfSpeedBonus(int level) {
        return selfSpeedBonus[clampLevel(level) - 1];
    }

    /** 该级激活者攻速加成（比例） */
    public static double getSelfAttackSpeedBonus(int level) {
        return selfAttackSpeedBonus[clampLevel(level) - 1];
    }

    /** 该级激活饥饿消耗（鸡腿数） */
    public static int getHungerCost(int level) {
        return hungerCosts[clampLevel(level) - 1];
    }

    /**
     * 时缓"慢放分母"：世界每 N tick 行动 1 tick。
     * 由时缓倍率换算：0.5→2、0.34→3、0.25→4。
     * 用固定相位（gameTime + 实体 id）判定放行，怪物均匀慢放而不是随机卡顿。
     */
    public static int getSlowDenominator(int level) {
        double scale = getTimeScale(level);
        return Math.max(2, (int) Math.round(1.0 / scale));
    }

    private static int clampLevel(int level) {
        return Math.max(1, Math.min(3, level));
    }
}
