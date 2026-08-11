package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ===== 终末将至 附魔配置 =====
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

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double endApproachesDamageMultiplier;
    public static double endApproachesAttackSpeedBonus;
    public static int endApproachesEndermanRange;
    public static boolean endApproachesAnnounce;
    public static double endApproachesLootRolls;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        endApproachesDamageMultiplier = END_APPROACHES_DAMAGE_MULTIPLIER.get();
        endApproachesAttackSpeedBonus = END_APPROACHES_ATTACK_SPEED_BONUS.get();
        endApproachesEndermanRange = END_APPROACHES_ENDERMAN_RANGE.get();
        endApproachesAnnounce = END_APPROACHES_ANNOUNCE.get();
        endApproachesLootRolls = END_APPROACHES_LOOT_ROLLS.get();
    }
}
