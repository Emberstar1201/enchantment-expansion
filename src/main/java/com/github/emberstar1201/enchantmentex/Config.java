package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 终末将至 附魔配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue END_APPROACHES_DAMAGE_MULTIPLIER = BUILDER
            .comment("终末将至：伤害倍率（基础伤害 × 此值，3.5 = +250%）")
            .defineInRange("endApproaches.damageMultiplier", 3.5, 1.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue END_APPROACHES_ATTACK_SPEED_BONUS = BUILDER
            .comment("终末将至：攻速加成分值（MULTIPLY_TOTAL 操作，9.0 = 攻速 ×10 = 冷却缩短 90%）")
            .defineInRange("endApproaches.attackSpeedBonus", 9.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue END_APPROACHES_ENDERMAN_RANGE = BUILDER
            .comment("终末将至：末影人仇恨清除检测范围（格）")
            .defineInRange("endApproaches.endermanCheckRange", 32, 1, 256);

    private static final ForgeConfigSpec.BooleanValue END_APPROACHES_ANNOUNCE = BUILDER
            .comment("终末将至：打开含附魔书的箱子时是否发送提示消息")
            .define("endApproaches.announceOnChestOpen", true);

    private static final ForgeConfigSpec.DoubleValue END_APPROACHES_LOOT_ROLLS = BUILDER
            .comment("终末将至：要塞图书馆附魔书生成概率（0.5 = 50%）")
            .defineInRange("endApproaches.lootRolls", 0.5, 0.0, 1.0);

    // ================================================================
    // 终界之星 配置
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_FLIGHT = BUILDER
            .comment("是否允许手持终界之星时获得创造模式飞行能力（默认 true）")
            .define("endStar.enable_end_star_flight", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_DAMAGE_REDUCTION = BUILDER
            .comment("是否允许手持终界之星时获得伤害减免（默认 true）")
            .define("endStar.enable_end_star_damage_reduction", true);

    private static final ForgeConfigSpec.DoubleValue END_STAR_DAMAGE_REDUCTION_PERCENT = BUILDER
            .comment("终界之星减伤百分比（0.0=无减伤，1.0=无敌，默认 0.8 即 80%）",
                    "覆盖所有伤害类型，包括监守者音波伤害 SONIC_BOOM")
            .defineInRange("endStar.end_star_damage_reduction_percent", 0.8, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue ENABLE_END_STAR_DAMAGE_BONUS = BUILDER
            .comment("是否允许手持终界之星时根据经验等级获得伤害加成（默认 true）",
                    "加成公式：每 10 级 +100% 伤害，上限 1000%（100 级达到上限）")
            .define("endStar.enable_end_star_damage_bonus", true);

    private static final ForgeConfigSpec.IntValue END_STAR_MAX_BONUS_PERCENT = BUILDER
            .comment("经验等级伤害加成上限（百分比，默认 1000 即 1000% 加成）",
                    "100 级玩家达到上限：floor(100/10)*100 = 1000")
            .defineInRange("endStar.end_star_max_bonus_percent", 1000, 0, 100000);

    private static final ForgeConfigSpec.IntValue END_STAR_DROP_DELAY = BUILDER
            .comment("终界之星生成后多少 tick 可拾取（默认 100 = 5秒）")
            .defineInRange("endStar.end_star_drop_delay", 100, 0, 72000);

    private static final ForgeConfigSpec.IntValue END_STAR_DESPAWN_TIME = BUILDER
            .comment("终界之星生成后多少 tick 自动消失（默认 12000 = 10分钟）")
            .defineInRange("endStar.end_star_despawn_time", 12000, 20, 1728000);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 加载时缓存到静态字段
    public static double endApproachesDamageMultiplier;
    public static double endApproachesAttackSpeedBonus;
    public static int endApproachesEndermanRange;
    public static boolean endApproachesAnnounce;
    public static double endApproachesLootRolls;

    public static boolean enableEndStarFlight;
    public static boolean enableEndStarDamageReduction;
    public static double endStarDamageReductionPercent;
    public static boolean enableEndStarDamageBonus;
    public static int endStarMaxBonusPercent;
    public static int endStarDropDelay;
    public static int endStarDespawnTime;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 终末将至
        endApproachesDamageMultiplier = END_APPROACHES_DAMAGE_MULTIPLIER.get();
        endApproachesAttackSpeedBonus = END_APPROACHES_ATTACK_SPEED_BONUS.get();
        endApproachesEndermanRange = END_APPROACHES_ENDERMAN_RANGE.get();
        endApproachesAnnounce = END_APPROACHES_ANNOUNCE.get();
        endApproachesLootRolls = END_APPROACHES_LOOT_ROLLS.get();
        // 终界之星
        enableEndStarFlight = ENABLE_END_STAR_FLIGHT.get();
        enableEndStarDamageReduction = ENABLE_END_STAR_DAMAGE_REDUCTION.get();
        endStarDamageReductionPercent = END_STAR_DAMAGE_REDUCTION_PERCENT.get();
        enableEndStarDamageBonus = ENABLE_END_STAR_DAMAGE_BONUS.get();
        endStarMaxBonusPercent = END_STAR_MAX_BONUS_PERCENT.get();
        endStarDropDelay = END_STAR_DROP_DELAY.get();
        endStarDespawnTime = END_STAR_DESPAWN_TIME.get();
    }
}
