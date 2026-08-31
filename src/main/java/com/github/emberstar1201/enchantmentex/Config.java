package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.List;

@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 古·云来弓法 & 云来弓法 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_ARROW_SPEED = BUILDER
            .comment("古·云来弓法：箭矢飞行速度倍率（原版满弦=1.0，默认 3.5）")
            .defineInRange("ancientYunLai.arrowSpeedMultiplier", 3.5, 1.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_DRAW_TIME = BUILDER
            .comment("古·云来弓法：蓄力时间（秒，原版=1.0s，默认 0.5s）",
                    "代码内部换算为蓄力倍率 = 1.0 / 蓄力时间")
            .defineInRange("ancientYunLai.drawTimeSeconds", 0.5, 0.05, 1.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_ARCHERY_DRAW_TIME = BUILDER
            .comment("云来弓法：蓄力时间（秒，原版=1.0s，默认 0.45s）",
                    "代码内部换算为蓄力倍率 = 1.0 / 蓄力时间")
            .defineInRange("yunLaiArchery.drawTimeSeconds", 0.45, 0.05, 1.0);

    // ================================================================
    // 云来剑法 & 古·云来剑法 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_ATTACK_SPEED = BUILDER
            .comment("云来剑法：攻击速度加成（ADDITION 操作，默认 2.5，基础攻速 4.0 → 6.5）")
            .defineInRange("yunlaiSwordmanship.attackSpeed", 2.5, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_ATTACK_REACH = BUILDER
            .comment("云来剑法：攻击距离加成（格，ADDITION 操作，默认 2.5，基础 3 格 → 5.5 格）")
            .defineInRange("yunlaiSwordmanship.attackReach", 2.5, 0.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_DAMAGE_MULTIPLIER = BUILDER
            .comment("云来剑法：伤害倍率（基础伤害 × 此值，默认 1.10 = +10%）")
            .defineInRange("yunlaiSwordmanship.damageMultiplier", 1.10, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_ATTACK_SPEED = BUILDER
            .comment("古·云来剑法：攻击速度加成（ADDITION 操作，默认 5.0，基础攻速 4.0 → 9.0）")
            .defineInRange("ancientYunlaiSwordmanship.attackSpeed", 5.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_ATTACK_REACH = BUILDER
            .comment("古·云来剑法：攻击距离加成（格，ADDITION 操作，默认 5.0，基础 3 格 → 8 格）")
            .defineInRange("ancientYunlaiSwordmanship.attackReach", 5.0, 0.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_DAMAGE_MULTIPLIER = BUILDER
            .comment("古·云来剑法：伤害倍率（基础伤害 × 此值，默认 1.50 = +50%）",
                    "宝藏级剑法的终局强化，配合攻速/距离加成定位\"终结剑法\"")
            .defineInRange("ancientYunlaiSwordmanship.damageMultiplier", 1.50, 1.0, 10.0);

    // ----------------------------------------------------------------
    // 剑气波（云来剑法 / 古·云来剑法）
    // 近战命中时按概率向前方发射一道月牙形剑气，直线飞行并穿透敌人
    // ----------------------------------------------------------------
    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_SWORD_QI_CHANCE = BUILDER
            .comment("云来剑法：剑气波触发概率（默认 0.6 = 每次近战命中 60% 概率发射剑气）")
            .defineInRange("yunlaiSwordmanship.swordQiChance", 0.6, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_SWORD_QI_DAMAGE = BUILDER
            .comment("云来剑法：剑气伤害（攻击伤害 × 此倍率，默认 0.5）")
            .defineInRange("yunlaiSwordmanship.swordQiDamageMultiplier", 0.5, 0.1, 5.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_SWORD_QI_RANGE = BUILDER
            .comment("云来剑法：剑气射程（格，默认 6）")
            .defineInRange("yunlaiSwordmanship.swordQiRange", 6.0, 2.0, 32.0);

    private static final ForgeConfigSpec.DoubleValue YUNLAI_SWORDMANSHIP_SWORD_QI_SPEED = BUILDER
            .comment("云来剑法：剑气飞行速度（格/tick，默认 0.75 = 每秒 15 格）")
            .defineInRange("yunlaiSwordmanship.swordQiSpeed", 0.75, 0.2, 3.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_CHANCE = BUILDER
            .comment("古·云来剑法：剑气波触发概率（默认 0.75 = 每次近战命中 75% 概率发射剑气）")
            .defineInRange("ancientYunlaiSwordmanship.swordQiChance", 0.75, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_DAMAGE = BUILDER
            .comment("古·云来剑法：剑气伤害（攻击伤害 × 此倍率，默认 0.75）")
            .defineInRange("ancientYunlaiSwordmanship.swordQiDamageMultiplier", 0.75, 0.1, 5.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_RANGE = BUILDER
            .comment("古·云来剑法：剑气射程（格，默认 10，比云来更远）")
            .defineInRange("ancientYunlaiSwordmanship.swordQiRange", 10.0, 2.0, 32.0);

    private static final ForgeConfigSpec.DoubleValue ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_SPEED = BUILDER
            .comment("古·云来剑法：剑气飞行速度（格/tick，默认 1.0 = 每秒 20 格）")
            .defineInRange("ancientYunlaiSwordmanship.swordQiSpeed", 1.0, 0.2, 3.0);

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
    // 琉璃冰魄箭 配置（简化版：取消二段蓄力，直接散射）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_DRAW_TIME = BUILDER
            .comment("琉璃冰魄箭：蓄力时间（秒，原版=1.0s，默认 0.5s = 原版的50%）",
                    "代码内部换算为蓄力倍率 = 1.0 / 蓄力时间")
            .defineInRange("glacialArrow.drawTimeSeconds", 0.5, 0.05, 1.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_SPEED = BUILDER
            .comment("琉璃冰魄箭：母箭速度倍率（原版满弦=1.0，默认 3.2）")
            .defineInRange("glacialArrow.speedMultiplier", 3.2, 1.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_DAMAGE = BUILDER
            .comment("琉璃冰魄箭：母箭范围伤害倍率（默认 2.5）")
            .defineInRange("glacialArrow.damageMultiplier", 2.5, 1.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_AOE_RANGE = BUILDER
            .comment("琉璃冰魄箭：母箭范围伤害半径（默认 2.5）")
            .defineInRange("glacialArrow.aoeRange", 2.5, 0.5, 10.0);

    private static final ForgeConfigSpec.IntValue GLACIAL_ARROW_PIERCE_COUNT = BUILDER
            .comment("琉璃冰魄箭：母箭穿透次数（默认 3，穿透多个敌人后消失）")
            .defineInRange("glacialArrow.pierceCount", 3, 1, 50);

    private static final ForgeConfigSpec.IntValue GLACIAL_ARROW_SUB_COUNT = BUILDER
            .comment("琉璃冰魄箭：子箭数量（散射，默认 12）")
            .defineInRange("glacialArrow.subArrowCount", 12, 0, 50);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_FAN_ANGLE = BUILDER
            .comment("琉璃冰魄箭：子箭扇形角度（度，默认 30）")
            .defineInRange("glacialArrow.fanAngle", 30.0, 5.0, 180.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_SUB_SPEED = BUILDER
            .comment("琉璃冰魄箭：子箭速度倍率（母箭速度 × 此值，默认 0.8）")
            .defineInRange("glacialArrow.subSpeedMultiplier", 0.8, 0.1, 2.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_SUB_DAMAGE = BUILDER
            .comment("琉璃冰魄箭：子箭范围伤害倍率（母箭基础伤害 × 此值，默认 1.0）")
            .defineInRange("glacialArrow.subDamageMultiplier", 1.0, 0.1, 2.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_SUB_AOE_RANGE = BUILDER
            .comment("琉璃冰魄箭：子箭范围伤害半径（默认 2.0）")
            .defineInRange("glacialArrow.subAoeRange", 2.0, 0.5, 10.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_PULL_STRENGTH = BUILDER
            .comment("琉璃冰魄箭：母箭命中后拉人强度（格，默认 0.4）")
            .defineInRange("glacialArrow.pullStrength", 0.4, 0.0, 5.0);

    private static final ForgeConfigSpec.DoubleValue GLACIAL_ARROW_PULL_RANGE = BUILDER
            .comment("琉璃冰魄箭：母箭命中后拉人范围（格，默认 2.0）")
            .defineInRange("glacialArrow.pullRange", 2.0, 0.5, 10.0);

    private static final ForgeConfigSpec.IntValue GLACIAL_ARROW_PARTICLE_INTERVAL = BUILDER
            .comment("琉璃冰魄箭：粒子生成间隔（tick，默认 2）")
            .defineInRange("glacialArrow.particleInterval", 2, 1, 10);

    // ================================================================
    // 自动冶炼 配置
    // ================================================================
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> AUTO_SMELT_ORE_BLOCKS = BUILDER
            .comment("自动冶炼：可冶炼的矿石方块 ID 列表（只有列表中的方块被破坏时才触发冶炼替换）",
                    "添加模组矿石只需在此列表中添加对应的方块 ID 即可")
            .defineList("autoSmelt.oreBlocks",
                    List.of(
                            "minecraft:iron_ore", "minecraft:deepslate_iron_ore",
                            "minecraft:raw_iron_block",
                            "minecraft:copper_ore", "minecraft:deepslate_copper_ore",
                            "minecraft:raw_copper_block",
                            "minecraft:gold_ore", "minecraft:deepslate_gold_ore",
                            "minecraft:raw_gold_block",
                            "minecraft:nether_gold_ore",
                            "minecraft:ancient_debris"
                    ),
                    it -> it instanceof String);

    private static final ForgeConfigSpec.DoubleValue AUTO_SMELT_XP_MIN = BUILDER
            .comment("自动冶炼：每块矿石经验补偿最小值")
            .defineInRange("autoSmelt.xpMin", 0.5, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue AUTO_SMELT_XP_MAX = BUILDER
            .comment("自动冶炼：每块矿石经验补偿最大值")
            .defineInRange("autoSmelt.xpMax", 1.0, 0.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue AUTO_SMELT_LEVEL2_BONUS_CHANCE = BUILDER
            .comment("自动冶炼：II级额外掉落1个的概率（默认 33%）")
            .defineInRange("autoSmelt.level2BonusChance", 0.33, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue AUTO_SMELT_LEVEL3_BONUS_CHANCE = BUILDER
            .comment("自动冶炼：III级额外掉落1~2个的概率（默认 50%）")
            .defineInRange("autoSmelt.level3BonusChance", 0.5, 0.0, 1.0);

    // ================================================================
    // 匠心传承 配置
    // ================================================================
    private static final ForgeConfigSpec.IntValue ARTISAN_LEGACY_MAX_MEMORY = BUILDER
            .comment("匠心传承：最大记忆方块种类数（默认 5）")
            .defineInRange("artisanLegacy.maxMemory", 5, 1, 20);

    private static final ForgeConfigSpec.DoubleValue ARTISAN_LEGACY_MAX_SPEED_MULTIPLIER = BUILDER
            .comment("匠心传承：最大挖掘速度倍率（默认 2.0 = 100%提升）")
            .defineInRange("artisanLegacy.maxSpeedMultiplier", 2.0, 1.0, 10.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends Integer>> ARTISAN_LEGACY_THRESHOLDS = BUILDER
            .comment("匠心传承：速度成长曲线阈值（挖掘次数阶梯，默认 10/25/50/100 次）",
                    "达到这些阈值时速度逐步提升，在阈值之间线性插值")
            .defineList("artisanLegacy.thresholds",
                    List.of(10, 25, 50, 100),
                    it -> it instanceof Integer);

    private static final ForgeConfigSpec.DoubleValue ARTISAN_LEGACY_EXTRA_DROP_CHANCE = BUILDER
            .comment("匠心传承：额外掉落最大概率（默认 0.2 = 20%，随挖掘次数增长）")
            .defineInRange("artisanLegacy.extraDropChance", 0.2, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue ARTISAN_LEGACY_MAX_EXTRA_DROP_THRESHOLD = BUILDER
            .comment("匠心传承：额外掉落达到最大概率所需的挖掘次数（默认 50）")
            .defineInRange("artisanLegacy.maxExtraDropThreshold", 50, 1, 1000);

    // ================================================================
    // 兵长的回声 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue LEVIS_ECHO_DAMAGE_BASE_PERCENT = BUILDER
            .comment("兵长的回声：基础伤害百分比（最大生命值的百分比，I级为 5%）",
                    "公式：basePercent + (等级-1) * perLevelPercent")
            .defineInRange("levisEcho.damageBasePercent", 5.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue LEVIS_ECHO_DAMAGE_PER_LEVEL_PERCENT = BUILDER
            .comment("兵长的回声：每级额外伤害百分比（II级起每级 +2%）")
            .defineInRange("levisEcho.damagePerLevelPercent", 2.0, 0.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue LEVIS_ECHO_HEALTH_THRESHOLD = BUILDER
            .comment("兵长的回声：触发条件所需目标生命值下限（默认 50.0 = 25❤️）")
            .defineInRange("levisEcho.healthThreshold", 50.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.IntValue LEVIS_ECHO_MARK_TIMEOUT = BUILDER
            .comment("兵长的回声：标记超时时间（tick，默认 100 = 5秒），\n超时后标记清零")
            .defineInRange("levisEcho.markTimeout", 100, 1, 72000);

    private static final ForgeConfigSpec.IntValue LEVIS_ECHO_MAX_STACKS = BUILDER
            .comment("兵长的回声：引爆所需标记层数（默认 3）")
            .defineInRange("levisEcho.maxStacks", 3, 2, 10);

    private static final ForgeConfigSpec.BooleanValue LEVIS_ECHO_PARTICLE_ENABLED = BUILDER
            .comment("兵长的回声：是否启用粒子视觉反馈（标记叠加时目标身上显示药水粒子，引爆时生成环形爆炸粒子）")
            .define("levisEcho.particleEnabled", true);

    // ================================================================
    // 嗜血 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue BLOODTHIRST_HEAL_PERCENT = BUILDER
            .comment("嗜血：每次触发回复的生命值百分比（默认 10.0 = 10% 最大生命值）")
            .defineInRange("bloodthirst.healPercent", 10.0, 0.0, 100.0);

    private static final ForgeConfigSpec.IntValue BLOODTHIRST_COOLDOWN_TICKS = BUILDER
            .comment("嗜血：冷却时间（tick，默认 100 = 5 秒），冷却期间不触发回复")
            .defineInRange("bloodthirst.cooldownTicks", 100, 1, 72000);

    // ================================================================
    // 拂晓重制 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue DAWN_DAMAGE_PER_KILL = BUILDER
            .comment("拂晓重制：每击杀伤害加成百分比（默认 0.5 = +0.5%/杀）")
            .defineInRange("dawn.damagePerKill", 0.5, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_DAMAGE_MAX = BUILDER
            .comment("拂晓重制：伤害加成上限百分比（默认 200 = +200% 伤害，400杀达上限）")
            .defineInRange("dawn.damageMax", 200.0, 0.0, 10000.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_CRIT_RATE_PER_KILL = BUILDER
            .comment("拂晓重制：每击杀暴击率加成百分比（默认 0.2 = +0.2%/杀）")
            .defineInRange("dawn.critRatePerKill", 0.2, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_CRIT_RATE_MAX = BUILDER
            .comment("拂晓重制：暴击率上限百分比（默认 55% = 275杀达上限）")
            .defineInRange("dawn.critRateMax", 55.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_CRIT_DAMAGE_PER_KILL = BUILDER
            .comment("拂晓重制：每击杀暴击伤害加成百分比（默认 0.5 = +0.5%/杀）")
            .defineInRange("dawn.critDamagePerKill", 0.5, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_CRIT_DAMAGE_MAX = BUILDER
            .comment("拂晓重制：暴击伤害上限百分比（默认 100% = 200杀达上限）")
            .defineInRange("dawn.critDamageMax", 100.0, 0.0, 10000.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_ATTACK_RANGE = BUILDER
            .comment("拂晓重制：攻击距离加成（格，默认 2.5）")
            .defineInRange("dawn.attackRange", 2.5, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_BOSS_MULTIPLIER_MIN = BUILDER
            .comment("拂晓重制：Boss击杀成长倍率最小值（默认 1.5）")
            .defineInRange("dawn.bossMultiplierMin", 1.5, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_BOSS_MULTIPLIER_MAX = BUILDER
            .comment("拂晓重制：Boss击杀成长倍率最大值（默认 2.0）")
            .defineInRange("dawn.bossMultiplierMax", 2.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_BOSS_HEALTH_THRESHOLD = BUILDER
            .comment("拂晓重制：Boss判定血量阈值（默认 200，≥此值的实体视为 Boss）")
            .defineInRange("dawn.bossHealthThreshold", 200.0, 1.0, Double.MAX_VALUE);

    // ================================================================
    // 拂晓重制 - 新机制（低血处决 + 击杀吸血/溅射）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue DAWN_EXECUTE_THRESHOLD = BUILDER
            .comment("拂晓重制：处决血线（目标剩余生命比例 ≤ 此值时本击必定暴击，默认 0.15 = 15%）")
            .defineInRange("dawn.executeThreshold", 0.15, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_EXECUTE_DAMAGE_MULTIPLIER = BUILDER
            .comment("拂晓重制：处决额外伤害倍率（处决时 = 暴击伤害 × 此倍率，默认 2.0）")
            .defineInRange("dawn.executeDamageMultiplier", 2.0, 1.0, 100.0);

    private static final ForgeConfigSpec.BooleanValue DAWN_LIFESTEAL_ENABLED = BUILDER
            .comment("拂晓重制：击杀吸血（默认 true）")
            .define("dawn.lifestealEnabled", true);

    private static final ForgeConfigSpec.DoubleValue DAWN_LIFESTEAL_PERCENT = BUILDER
            .comment("拂晓重制：击杀吸血比例（回复 = 目标最大生命 × 此值，默认 0.05 = 5%）")
            .defineInRange("dawn.lifestealPercent", 0.05, 0.0, 1.0);

    private static final ForgeConfigSpec.BooleanValue DAWN_SPLASH_ENABLED = BUILDER
            .comment("拂晓重制：击杀范围溅射（默认 true）")
            .define("dawn.splashEnabled", true);

    private static final ForgeConfigSpec.DoubleValue DAWN_SPLASH_RADIUS = BUILDER
            .comment("拂晓重制：击杀溅射半径（格，默认 3.0）")
            .defineInRange("dawn.splashRadius", 3.0, 0.5, 20.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_SPLASH_DAMAGE_PERCENT = BUILDER
            .comment("拂晓重制：击杀溅射伤害（溅射伤害 = 目标最大生命 × 此值，默认 0.5 = 50%")
            .defineInRange("dawn.splashDamagePercent", 0.5, 0.0, 100.0);

    // ================================================================
    // 拂晓重制 - 刺破长夜 配置（方案A+B 混合：连击爆发主 + 低血狂暴副，共享冷却）
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue DAWN_PIERCE_ENABLED = BUILDER
            .comment("刺破长夜：总开关（默认 true）")
            .define("dawn.pierceEnabled", true);

    private static final ForgeConfigSpec.IntValue DAWN_PIERCE_COMBO_THRESHOLD = BUILDER
            .comment("刺破长夜·连击爆发：触发所需连击数（默认 3）")
            .defineInRange("dawn.pierceComboThreshold", 3, 1, 10);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_COMBO_WINDOW_SECONDS = BUILDER
            .comment("刺破长夜·连击爆发：连击窗口（秒，相邻击杀间隔 ≤ 此值才累计，默认 8）")
            .defineInRange("dawn.pierceComboWindowSeconds", 8.0, 1.0, 120.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_DURATION_SECONDS = BUILDER
            .comment("刺破长夜：激活持续时间（秒，两种触发共用，默认 8）")
            .defineInRange("dawn.pierceDurationSeconds", 8.0, 1.0, 60.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_COOLDOWN_SECONDS = BUILDER
            .comment("刺破长夜：冷却时间（秒，连击与低血共享冷却，默认 15）")
            .defineInRange("dawn.pierceCooldownSeconds", 15.0, 1.0, 300.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_COMBO_DAMAGE_MULTIPLIER = BUILDER
            .comment("刺破长夜·连击爆发：伤害倍率（默认 1.5 = +50%）")
            .defineInRange("dawn.pierceComboDamageMultiplier", 1.5, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_COMBO_MOVE_SPEED_PERCENT = BUILDER
            .comment("刺破长夜·连击爆发：移动速度加成百分比（默认 10 = +10%）")
            .defineInRange("dawn.pierceComboMoveSpeedPercent", 10.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_COMBO_REACH_BONUS = BUILDER
            .comment("刺破长夜·连击爆发：额外攻击距离（格，默认 2）")
            .defineInRange("dawn.pierceComboReachBonus", 2.0, 0.0, 20.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_LOW_HP_THRESHOLD = BUILDER
            .comment("刺破长夜·低血狂暴：生命 ≤ 此比例时自动激活（默认 0.40 = 40%）")
            .defineInRange("dawn.pierceLowHpThreshold", 0.40, 0.05, 1.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_LOW_HP_DAMAGE_MULTIPLIER = BUILDER
            .comment("刺破长夜·低血狂暴：伤害倍率（数值减半，默认 1.4 = +40%）")
            .defineInRange("dawn.pierceLowHpDamageMultiplier", 1.4, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue DAWN_PIERCE_LOW_HP_LIFESTEAL_PERCENT = BUILDER
            .comment("刺破长夜·低血狂暴：吸血百分比（按造成伤害回复，数值减半，默认 5%）")
            .defineInRange("dawn.pierceLowHpLifestealPercent", 5.0, 0.0, 100.0);

    // ================================================================
    // 飞轮效应 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue FLYWHEEL_DISTANCE_1 = BUILDER
            .comment("飞轮效应：I级冲刺距离（格，默认 6.5）")
            .defineInRange("flywheel.distanceLevel1", 6.5, 1.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue FLYWHEEL_DISTANCE_2 = BUILDER
            .comment("飞轮效应：II级冲刺距离（格，默认 8.0）")
            .defineInRange("flywheel.distanceLevel2", 8.0, 1.0, 50.0);

    private static final ForgeConfigSpec.DoubleValue FLYWHEEL_DISTANCE_3 = BUILDER
            .comment("飞轮效应：III级冲刺距离（格，默认 9.5）")
            .defineInRange("flywheel.distanceLevel3", 9.5, 1.0, 50.0);

    private static final ForgeConfigSpec.IntValue FLYWHEEL_COOLDOWN_1 = BUILDER
            .comment("飞轮效应：I级冷却时间（秒，默认 60）")
            .defineInRange("flywheel.cooldownSeconds1", 60, 1, 3600);

    private static final ForgeConfigSpec.IntValue FLYWHEEL_COOLDOWN_2 = BUILDER
            .comment("飞轮效应：II级冷却时间（秒，默认 30）")
            .defineInRange("flywheel.cooldownSeconds2", 30, 1, 3600);

    private static final ForgeConfigSpec.IntValue FLYWHEEL_COOLDOWN_3 = BUILDER
            .comment("飞轮效应：III级冷却时间（秒，默认 15）")
            .defineInRange("flywheel.cooldownSeconds3", 15, 1, 3600);

    private static final ForgeConfigSpec.IntValue FLYWHEEL_DURABILITY_COST = BUILDER
            .comment("飞轮效应：每次冲刺耐久消耗（默认 3）")
            .defineInRange("flywheel.durabilityCost", 3, 0, 100);

    private static final ForgeConfigSpec.DoubleValue FLYWHEEL_DASH_SPEED = BUILDER
            .comment("飞轮效应：冲刺速度（blocks/tick，默认 1.5）",
                    "值越大冲刺越快完成，过大会导致穿墙")
            .defineInRange("flywheel.dashSpeed", 1.5, 0.1, 10.0);

    // ================================================================
    // 千破·青溟剑 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue QIANPO_QINGMING_DAMAGE_LEVEL1 = BUILDER
            .comment("千破·青溟剑：I级额外魔法伤害（默认 12）",
                    "该伤害为魔法伤害，无视盔甲，但受保护附魔和抗性提升影响")
            .defineInRange("qianpoQingMing.damageLevel1", 12.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue QIANPO_QINGMING_DAMAGE_LEVEL2 = BUILDER
            .comment("千破·青溟剑：II级额外魔法伤害（默认 24）")
            .defineInRange("qianpoQingMing.damageLevel2", 24.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue QIANPO_QINGMING_DAMAGE_LEVEL3 = BUILDER
            .comment("千破·青溟剑：III级额外魔法伤害（默认 36）")
            .defineInRange("qianpoQingMing.damageLevel3", 36.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue QIANPO_QINGMING_DAMAGE_LEVEL4 = BUILDER
            .comment("千破·青溟剑：IV级额外魔法伤害（默认 48）")
            .defineInRange("qianpoQingMing.damageLevel4", 48.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.DoubleValue QIANPO_QINGMING_DAMAGE_LEVEL5 = BUILDER
            .comment("千破·青溟剑：V级额外魔法伤害（默认 60）")
            .defineInRange("qianpoQingMing.damageLevel5", 60.0, 0.0, Double.MAX_VALUE);

    private static final ForgeConfigSpec.BooleanValue QIANPO_QINGMING_VISUAL_ENABLED = BUILDER
            .comment("千破·青溟剑：是否启用粒子+音效视觉反馈（默认 true）",
                    "关闭时伤害仍然生效，仅不播放心得粒子和音效")
            .define("qianpoQingMing.visualEnabled", true);

    // ================================================================
    // 风踏涟漪 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue WIND_RIPPLE_LAVA_SPEED = BUILDER
            .comment("风踏涟漪：岩浆行走移速倍率（默认 1.15，岩浆黏稠，比水面行走的 1.25 略慢）")
            .defineInRange("windRipple.lavaSpeedMultiplier", 1.15, 0.5, 5.0);

    // ================================================================
    // 无中生有·重制 配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue EX_NIHILO_RARE_CHANCE_PER_LEVEL = BUILDER
            .comment("无中生有·重制：每级稀有触发概率（默认 0.01 = 每级 1%，III 级为 3%）")
            .defineInRange("exNihilo.rareChancePerLevel", 0.01, 0.0, 1.0);

    private static final ForgeConfigSpec.IntValue EX_NIHILO_RARE_DIAMOND_WEIGHT = BUILDER
            .comment("无中生有·重制：稀有掉落中钻石的权重（默认 70）")
            .defineInRange("exNihilo.rareDiamondWeight", 70, 0, 1000);

    private static final ForgeConfigSpec.IntValue EX_NIHILO_RARE_SCRAP_WEIGHT = BUILDER
            .comment("无中生有·重制：稀有掉落中下界合金碎片的权重（默认 25）")
            .defineInRange("exNihilo.rareScrapWeight", 25, 0, 1000);

    private static final ForgeConfigSpec.IntValue EX_NIHILO_RARE_INGOT_WEIGHT = BUILDER
            .comment("无中生有·重制：稀有掉落中下界合金锭的权重（默认 5）")
            .defineInRange("exNihilo.rareIngotWeight", 5, 0, 1000);

    private static final ForgeConfigSpec.DoubleValue EX_NIHILO_SHEAR_GOLDEN_APPLE_CHANCE = BUILDER
            .comment("无中生有·重制：剪刀采集树叶时金苹果掉落概率（默认 0.005 = 0.5%）",
                    "实际概率 = 此值 × 等级倍率（I:1×, II:1.5×, III:2×）")
            .defineInRange("exNihilo.shearGoldenAppleChance", 0.005, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue EX_NIHILO_SHEAR_ENCHANTED_GOLDEN_APPLE_CHANCE = BUILDER
            .comment("无中生有·重制：剪刀采集树叶时附魔金苹果掉落概率（默认 0.0005 = 0.05%）",
                    "实际概率 = 此值 × 等级倍率（I:1×, II:1.5×, III:2×）")
            .defineInRange("exNihilo.shearEnchantedGoldenAppleChance", 0.0005, 0.0, 1.0);

    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> EX_NIHILO_STONE_BLOCKS = BUILDER
            .comment("无中生有·重制：触发额外掉落的石质方块 ID 列表",
                    "包括主世界（石头/花岗岩/闪长岩/安山岩/深板岩/凝灰岩及其变种）、",
                    "下界（地狱岩/玄武岩/黑石及其变种）、末地（末地石及其变种）",
                    "矿物方块本身不在此列表，只作用于石质基底方块",
                    "添加模组方块只需在此列表中添加对应的方块 ID 即可")
            .defineList("exNihilo.stoneBlocks",
                    List.of(
                            // ========== 主世界 ==========
                            "minecraft:stone",
                            "minecraft:smooth_stone",
                            "minecraft:stone_bricks", "minecraft:mossy_stone_bricks",
                            "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks",
                            "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                            // 花岗岩
                            "minecraft:granite", "minecraft:polished_granite",
                            // 闪长岩
                            "minecraft:diorite", "minecraft:polished_diorite",
                            // 安山岩
                            "minecraft:andesite", "minecraft:polished_andesite",
                            // 深板岩
                            "minecraft:deepslate", "minecraft:polished_deepslate",
                            "minecraft:deepslate_bricks", "minecraft:deepslate_tiles",
                            "minecraft:cracked_deepslate_bricks", "minecraft:cracked_deepslate_tiles",
                            "minecraft:chiseled_deepslate",
                            // 凝灰岩
                            "minecraft:tuff", "minecraft:polished_tuff",
                            "minecraft:tuff_bricks", "minecraft:cracked_tuff_bricks",
                            "minecraft:chiseled_tuff",
                            // ========== 下界 ==========
                            "minecraft:netherrack",
                            // 玄武岩
                            "minecraft:basalt", "minecraft:polished_basalt", "minecraft:smooth_basalt",
                            // 黑石
                            "minecraft:blackstone", "minecraft:polished_blackstone",
                            "minecraft:blackstone_bricks", "minecraft:polished_blackstone_bricks",
                            "minecraft:cracked_blackstone_bricks", "minecraft:cracked_polished_blackstone_bricks",
                            "minecraft:chiseled_blackstone",
                            "minecraft:gilded_blackstone",
                            // ========== 末地 ==========
                            "minecraft:end_stone", "minecraft:end_stone_bricks"
                    ),
                    it -> it instanceof String);

    

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

    // ================================================================
    // 人权剑（Sword of the Free Will）配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue SWORD_LIGHTNING_DAMAGE = BUILDER
            .comment("人权剑：闪电固定真实伤害（默认 20）")
            .defineInRange("swordOfFreeWill.lightningDamage", 20.0, 0.0, 1000.0);

    private static final ForgeConfigSpec.DoubleValue SWORD_LIGHTNING_CHANCE = BUILDER
            .comment("人权剑：闪电触发概率（默认 0.9 = 90%）")
            .defineInRange("swordOfFreeWill.lightningChance", 0.9, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue SWORD_HEALTH_COST_PERCENT = BUILDER
            .comment("人权剑：主动技能生命值消耗百分比（默认 0.25 = 25%）")
            .defineInRange("swordOfFreeWill.healthCostPercent", 0.25, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue SWORD_HEALTH_THRESHOLD = BUILDER
            .comment("人权剑：生命值低于此比例不再消耗生命（默认 0.5 = 50%）")
            .defineInRange("swordOfFreeWill.healthThreshold", 0.5, 0.0, 1.0);

    private static final ForgeConfigSpec.DoubleValue SWORD_DAMAGE_BOOST_PERCENT = BUILDER
            .comment("人权剑：伤害提升百分比（默认 1.5 = +150%，最终伤害 = 原伤害 × (1 + 此值)）")
            .defineInRange("swordOfFreeWill.damageBoostPercent", 1.5, 0.0, 100.0);

    private static final ForgeConfigSpec.IntValue SWORD_BUFF_DURATION = BUILDER
            .comment("人权剑：增益效果持续时间（秒，默认 600 = 10 分钟）")
            .defineInRange("swordOfFreeWill.buffDuration", 600, 1, 86400);

    private static final ForgeConfigSpec.IntValue SWORD_COOLDOWN = BUILDER
            .comment("人权剑：主动技能冷却时间（秒，默认 900 = 15 分钟）")
            .defineInRange("swordOfFreeWill.cooldown", 900, 1, 86400);

    private static final ForgeConfigSpec.DoubleValue SWORD_PASSIVE_ARMOR = BUILDER
            .comment("人权剑：被动护甲值（默认 10，以属性修饰符形式添加到 Attributes.ARMOR）")
            .defineInRange("swordOfFreeWill.passiveArmor", 10.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue SWORD_PASSIVE_DAMAGE_REDUCTION = BUILDER
            .comment("人权剑：被动伤害减免百分比（默认 0.25 = 25%）")
            .defineInRange("swordOfFreeWill.passiveDamageReduction", 0.25, 0.0, 1.0);

    // ================================================================
    // 星火不灭（Eternal Spark）配置
    // ================================================================
    private static final ForgeConfigSpec.IntValue ETERNAL_SPARK_DURATION_SECONDS = BUILDER
            .comment("星火不灭：标记持续时间（秒，默认 5；超过此时间标记自行消失，不再提供额外伤害加成）")
            .defineInRange("eternalSpark.durationSeconds", 5, 1, 7200);

    private static final ForgeConfigSpec.DoubleValue ETERNAL_SPARK_BASE_DAMAGE_PERCENT = BUILDER
            .comment("星火不灭：每次攻击每层基础伤害百分比（默认 5.0 = 每层每次攻击造成 5% 最大生命值真实伤害）",
                    "层数1→5%, 层数2→10%, 层数3→15%")
            .defineInRange("eternalSpark.baseDamagePercent", 5.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue ETERNAL_SPARK_ACTIVE_DAMAGE_PERCENT = BUILDER
            .comment("星火不灭：人权剑「人的意志」激活期间每次攻击每层伤害百分比（默认 8.0 = 每层 8% 最大生命值真实伤害）",
                    "层数1→8%, 层数2→16%, 层数3→24%")
            .defineInRange("eternalSpark.activeDamagePercent", 8.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue ETERNAL_SPARK_STACK_BONUS_PERCENT = BUILDER
            .comment("星火不灭：每层额外伤害倍率加成（默认 0.0 = 无额外加成）",
                    "公式：总百分比 = basePercent × stacks × (1 + (stacks-1) × stackBonusPercent / 100)",
                    "若希望层数3伤害翻倍（即层数3 = 层数1 × 2），则填 25（此时 (1 + 2×0.25) = 1.5 倍）")
            .defineInRange("eternalSpark.stackBonusPercent", 0.0, 0.0, 500.0);

    private static final ForgeConfigSpec.DoubleValue ETERNAL_SPARK_SPREAD_RANGE = BUILDER
            .comment("星火不灭：死亡扩散半径（格，默认 5.0 格）",
                    "人权剑 buff 激活期间此半径自动 × 2 = 10 格")
            .defineInRange("eternalSpark.spreadRange", 5.0, 0.5, 100.0);

    // ================================================================
    // 无烟冲击（Smokeless Dash）配置
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue SMOKELESS_DASH_MAX_MULTIPLIER = BUILDER
            .comment("无烟冲击：最大速度倍率（原版滑翔=1.0，烟花火箭≈2.0，默认上限 3.0）")
            .defineInRange("smokelessDash.maxMultiplier", 3.0, 1.0, 10.0);

    private static final ForgeConfigSpec.DoubleValue SMOKELESS_DASH_SCROLL_SENSITIVITY = BUILDER
            .comment("无烟冲击：滚轮灵敏度（每次滚轮调整的速度增量，值越大调得越快，推荐 0.15）")
            .defineInRange("smokelessDash.scrollSensitivity", 0.15, 0.01, 2.0);

    private static final ForgeConfigSpec.DoubleValue SMOKELESS_DASH_BASE_BOOST = BUILDER
            .comment("无烟冲击：基础加速常数（每 tick 沿视线方向的加速度，值越大加速越猛，推荐 0.12）")
            .defineInRange("smokelessDash.baseBoost", 0.12, 0.001, 1.0);

    // ================================================================
    // 伤害/治疗浮动数字
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_DAMAGE_POPUP = BUILDER
            .comment("伤害/治疗浮动数字：生物受伤显示红色数字（-N）、受治疗显示绿色数字（+N），仅 64 格内可见",
                    "默认 true")
            .define("damagePopup.enabled", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    // 加载时缓存到静态字段
    // 云来剑法 & 古·云来剑法
    public static double yunlaiSwordmanshipAttackSpeed;
    public static double yunlaiSwordmanshipAttackReach;
    public static double yunlaiSwordmanshipDamageMultiplier;
    public static double ancientYunlaiSwordmanshipAttackSpeed;
    public static double ancientYunlaiSwordmanshipAttackReach;
    public static double ancientYunlaiSwordmanshipDamageMultiplier;
    // 云来剑法 / 古·云来剑法：剑气波
    public static double yunlaiSwordmanshipSwordQiChance;
    public static double yunlaiSwordmanshipSwordQiDamageMultiplier;
    public static double yunlaiSwordmanshipSwordQiRange;
    public static double yunlaiSwordmanshipSwordQiSpeed;
    public static double ancientYunlaiSwordmanshipSwordQiChance;
    public static double ancientYunlaiSwordmanshipSwordQiDamageMultiplier;
    public static double ancientYunlaiSwordmanshipSwordQiRange;
    public static double ancientYunlaiSwordmanshipSwordQiSpeed;

    // 古·云来弓法 & 云来弓法
    public static double ancientYunLaiArrowSpeed;
    public static double ancientYunLaiChargeMultiplier;   // 由 drawTime 换算
    public static double yunLaiArcheryChargeMultiplier;   // 由 drawTime 换算

    // 终末将至
    public static double endApproachesDamageMultiplier;
    public static double endApproachesAttackSpeedBonus;
    public static int endApproachesEndermanRange;
    public static boolean endApproachesAnnounce;
    public static double endApproachesLootRolls;

    // 琉璃冰魄箭（简化版）
    public static double glacialArrowChargeMultiplier;  // 由 drawTime 换算
    public static double glacialArrowSpeed;
    public static double glacialArrowDamage;
    public static double glacialArrowAoeRange;
    public static int glacialArrowPierceCount;
    public static int glacialArrowSubCount;
    public static double glacialArrowFanAngle;
    public static double glacialArrowSubSpeed;
    public static double glacialArrowSubDamage;
    public static double glacialArrowSubAoeRange;
    public static double glacialArrowPullStrength;
    public static double glacialArrowPullRange;
    public static int glacialArrowParticleInterval;

    // 自动冶炼
    public static List<? extends String> autoSmeltOreBlocks;
    public static double autoSmeltXpMin;
    public static double autoSmeltXpMax;
    public static double autoSmeltLevel2BonusChance;
    public static double autoSmeltLevel3BonusChance;

    // 匠心传承
    public static int artisanLegacyMaxMemory;
    public static double artisanLegacyMaxSpeedMultiplier;
    public static int[] artisanLegacyThresholds;
    public static double artisanLegacyExtraDropChance;
    public static int artisanLegacyMaxExtraDropThreshold;

    // 兵长的回声
    public static double levisEchoDamageBasePercent;
    public static double levisEchoDamagePerLevelPercent;
    public static double levisEchoHealthThreshold;
    public static int levisEchoMarkTimeout;
    public static int levisEchoMaxStacks;
    public static boolean levisEchoParticleEnabled;

    // 嗜血
    public static double bloodthirstHealPercent;
    public static int bloodthirstCooldownTicks;

    // 拂晓重制
    public static double dawnDamagePerKill;
    public static double dawnDamageMax;
    public static double dawnCritRatePerKill;
    public static double dawnCritRateMax;
    public static double dawnCritDamagePerKill;
    public static double dawnCritDamageMax;
    public static double dawnAttackRange;
    public static double dawnBossMultiplierMin;
    public static double dawnBossMultiplierMax;
    public static double dawnBossHealthThreshold;
    public static double dawnExecuteThreshold;
    public static double dawnExecuteDamageMultiplier;
    public static boolean dawnLifestealEnabled;
    public static double dawnLifestealPercent;
    // 伤害/治疗浮动数字
    public static boolean enableDamagePopup;

    public static boolean dawnSplashEnabled;
    public static double dawnSplashRadius;
    public static double dawnSplashDamagePercent;

    // 刺破长夜（方案A+B 混合）
    public static boolean dawnPierceEnabled;
    public static int dawnPierceComboThreshold;
    public static double dawnPierceComboWindowSeconds;
    public static double dawnPierceDurationSeconds;
    public static double dawnPierceCooldownSeconds;
    public static double dawnPierceComboDamageMultiplier;
    public static double dawnPierceComboMoveSpeedPercent;
    public static double dawnPierceComboReachBonus;
    public static double dawnPierceLowHpThreshold;
    public static double dawnPierceLowHpDamageMultiplier;
    public static double dawnPierceLowHpLifestealPercent;

    // 飞轮效应
    public static double flywheelDistance1;
    public static double flywheelDistance2;
    public static double flywheelDistance3;
    public static int flywheelCooldownSeconds1;
    public static int flywheelCooldownSeconds2;
    public static int flywheelCooldownSeconds3;
    public static int flywheelDurabilityCost;
    public static double flywheelDashSpeed;

    // 千破·青溟剑
    public static double qianpoQingMingDamageLevel1;
    public static double qianpoQingMingDamageLevel2;
    public static double qianpoQingMingDamageLevel3;
    public static double qianpoQingMingDamageLevel4;
    public static double qianpoQingMingDamageLevel5;
    public static boolean qianpoQingMingVisualEnabled;

    // 风踏涟漪
    public static double windRippleLavaSpeed;

    // 无中生有·重制
    public static double exNihiloRareChancePerLevel;
    public static int exNihiloRareDiamondWeight;
    public static int exNihiloRareScrapWeight;
    public static int exNihiloRareIngotWeight;
    public static double exNihiloShearGoldenAppleChance;
    public static double exNihiloShearEnchantedGoldenAppleChance;
    public static List<? extends String> exNihiloStoneBlocks;

    

    public static boolean enableEndStarFlight;
    public static boolean enableEndStarDamageReduction;
    public static double endStarDamageReductionPercent;
    public static boolean enableEndStarDamageBonus;
    public static int endStarMaxBonusPercent;
    public static int endStarDropDelay;
    public static int endStarDespawnTime;

    // 人权剑
    public static double swordLightningDamage;
    public static double swordLightningChance;
    public static double swordHealthCostPercent;
    public static double swordHealthThreshold;
    public static double swordDamageBoostPercent;
    public static int swordBuffDuration;
    public static int swordCooldown;
    public static double swordPassiveArmor;
    public static double swordPassiveDamageReduction;

    // 星火不灭
    public static int eternalSparkDurationSeconds;
    public static double eternalSparkBaseDamagePercent;
    public static double eternalSparkActiveDamagePercent;
    public static double eternalSparkStackBonusPercent;
    public static double eternalSparkSpreadRange;

    // 无烟冲击
    public static double smokelessDashMaxMultiplier;
    public static double smokelessDashScrollSensitivity;
    public static double smokelessDashBaseBoost;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // 古·云来弓法 & 云来弓法：将蓄力时间换算为蓄力加速倍率
        ancientYunLaiArrowSpeed = ANCIENT_YUNLAI_ARROW_SPEED.get();
        ancientYunLaiChargeMultiplier = 1.0 / ANCIENT_YUNLAI_DRAW_TIME.get();
        yunLaiArcheryChargeMultiplier = 1.0 / YUNLAI_ARCHERY_DRAW_TIME.get();

        // 云来剑法 & 古·云来剑法
        yunlaiSwordmanshipAttackSpeed = YUNLAI_SWORDMANSHIP_ATTACK_SPEED.get();
        yunlaiSwordmanshipAttackReach = YUNLAI_SWORDMANSHIP_ATTACK_REACH.get();
        yunlaiSwordmanshipDamageMultiplier = YUNLAI_SWORDMANSHIP_DAMAGE_MULTIPLIER.get();
        ancientYunlaiSwordmanshipAttackSpeed = ANCIENT_YUNLAI_SWORDMANSHIP_ATTACK_SPEED.get();
        ancientYunlaiSwordmanshipAttackReach = ANCIENT_YUNLAI_SWORDMANSHIP_ATTACK_REACH.get();
        ancientYunlaiSwordmanshipDamageMultiplier = ANCIENT_YUNLAI_SWORDMANSHIP_DAMAGE_MULTIPLIER.get();

        // 剑气波（云来剑法 / 古·云来剑法）
        yunlaiSwordmanshipSwordQiChance = YUNLAI_SWORDMANSHIP_SWORD_QI_CHANCE.get();
        yunlaiSwordmanshipSwordQiDamageMultiplier = YUNLAI_SWORDMANSHIP_SWORD_QI_DAMAGE.get();
        yunlaiSwordmanshipSwordQiRange = YUNLAI_SWORDMANSHIP_SWORD_QI_RANGE.get();
        yunlaiSwordmanshipSwordQiSpeed = YUNLAI_SWORDMANSHIP_SWORD_QI_SPEED.get();
        ancientYunlaiSwordmanshipSwordQiChance = ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_CHANCE.get();
        ancientYunlaiSwordmanshipSwordQiDamageMultiplier = ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_DAMAGE.get();
        ancientYunlaiSwordmanshipSwordQiRange = ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_RANGE.get();
        ancientYunlaiSwordmanshipSwordQiSpeed = ANCIENT_YUNLAI_SWORDMANSHIP_SWORD_QI_SPEED.get();

        // 终末将至
        endApproachesDamageMultiplier = END_APPROACHES_DAMAGE_MULTIPLIER.get();
        endApproachesAttackSpeedBonus = END_APPROACHES_ATTACK_SPEED_BONUS.get();
        endApproachesEndermanRange = END_APPROACHES_ENDERMAN_RANGE.get();
        endApproachesAnnounce = END_APPROACHES_ANNOUNCE.get();
        endApproachesLootRolls = END_APPROACHES_LOOT_ROLLS.get();
        // 琉璃冰魄箭（简化版）
        glacialArrowChargeMultiplier = 1.0 / GLACIAL_ARROW_DRAW_TIME.get();
        glacialArrowSpeed = GLACIAL_ARROW_SPEED.get();
        glacialArrowDamage = GLACIAL_ARROW_DAMAGE.get();
        glacialArrowAoeRange = GLACIAL_ARROW_AOE_RANGE.get();
        glacialArrowPierceCount = GLACIAL_ARROW_PIERCE_COUNT.get();
        glacialArrowSubCount = GLACIAL_ARROW_SUB_COUNT.get();
        glacialArrowFanAngle = GLACIAL_ARROW_FAN_ANGLE.get();
        glacialArrowSubSpeed = GLACIAL_ARROW_SUB_SPEED.get();
        glacialArrowSubDamage = GLACIAL_ARROW_SUB_DAMAGE.get();
        glacialArrowSubAoeRange = GLACIAL_ARROW_SUB_AOE_RANGE.get();
        glacialArrowPullStrength = GLACIAL_ARROW_PULL_STRENGTH.get();
        glacialArrowPullRange = GLACIAL_ARROW_PULL_RANGE.get();
        glacialArrowParticleInterval = GLACIAL_ARROW_PARTICLE_INTERVAL.get();

        // 自动冶炼
        autoSmeltOreBlocks = AUTO_SMELT_ORE_BLOCKS.get();
        autoSmeltXpMin = AUTO_SMELT_XP_MIN.get();
        autoSmeltXpMax = AUTO_SMELT_XP_MAX.get();
        autoSmeltLevel2BonusChance = AUTO_SMELT_LEVEL2_BONUS_CHANCE.get();
        autoSmeltLevel3BonusChance = AUTO_SMELT_LEVEL3_BONUS_CHANCE.get();

        // 匠心传承
        artisanLegacyMaxMemory = ARTISAN_LEGACY_MAX_MEMORY.get();
        artisanLegacyMaxSpeedMultiplier = ARTISAN_LEGACY_MAX_SPEED_MULTIPLIER.get();
        // 将 List<Integer> 转换为 int[]（速度阈值）
        var thresholdsList = ARTISAN_LEGACY_THRESHOLDS.get();
        artisanLegacyThresholds = new int[thresholdsList.size()];
        for (int i = 0; i < thresholdsList.size(); i++) {
            artisanLegacyThresholds[i] = thresholdsList.get(i);
        }
        artisanLegacyExtraDropChance = ARTISAN_LEGACY_EXTRA_DROP_CHANCE.get();
        artisanLegacyMaxExtraDropThreshold = ARTISAN_LEGACY_MAX_EXTRA_DROP_THRESHOLD.get();

        // 兵长的回声
        levisEchoDamageBasePercent = LEVIS_ECHO_DAMAGE_BASE_PERCENT.get();
        levisEchoDamagePerLevelPercent = LEVIS_ECHO_DAMAGE_PER_LEVEL_PERCENT.get();
        levisEchoHealthThreshold = LEVIS_ECHO_HEALTH_THRESHOLD.get();
        levisEchoMarkTimeout = LEVIS_ECHO_MARK_TIMEOUT.get();
        levisEchoMaxStacks = LEVIS_ECHO_MAX_STACKS.get();
        levisEchoParticleEnabled = LEVIS_ECHO_PARTICLE_ENABLED.get();

        // 嗜血
        bloodthirstHealPercent = BLOODTHIRST_HEAL_PERCENT.get();
        bloodthirstCooldownTicks = BLOODTHIRST_COOLDOWN_TICKS.get();

        // 拂晓重制
        dawnDamagePerKill = DAWN_DAMAGE_PER_KILL.get();
        dawnDamageMax = DAWN_DAMAGE_MAX.get();
        dawnCritRatePerKill = DAWN_CRIT_RATE_PER_KILL.get();
        dawnCritRateMax = DAWN_CRIT_RATE_MAX.get();
        dawnCritDamagePerKill = DAWN_CRIT_DAMAGE_PER_KILL.get();
        dawnCritDamageMax = DAWN_CRIT_DAMAGE_MAX.get();
        dawnAttackRange = DAWN_ATTACK_RANGE.get();
        dawnBossMultiplierMin = DAWN_BOSS_MULTIPLIER_MIN.get();
        dawnBossMultiplierMax = DAWN_BOSS_MULTIPLIER_MAX.get();
        dawnBossHealthThreshold = DAWN_BOSS_HEALTH_THRESHOLD.get();
        dawnExecuteThreshold = DAWN_EXECUTE_THRESHOLD.get();
        dawnExecuteDamageMultiplier = DAWN_EXECUTE_DAMAGE_MULTIPLIER.get();
        dawnLifestealEnabled = DAWN_LIFESTEAL_ENABLED.get();
        dawnLifestealPercent = DAWN_LIFESTEAL_PERCENT.get();
        dawnSplashEnabled = DAWN_SPLASH_ENABLED.get();
        dawnSplashRadius = DAWN_SPLASH_RADIUS.get();
        dawnSplashDamagePercent = DAWN_SPLASH_DAMAGE_PERCENT.get();

        // 刺破长夜（方案A+B 混合）
        dawnPierceEnabled = DAWN_PIERCE_ENABLED.get();
        dawnPierceComboThreshold = DAWN_PIERCE_COMBO_THRESHOLD.get();
        dawnPierceComboWindowSeconds = DAWN_PIERCE_COMBO_WINDOW_SECONDS.get();
        dawnPierceDurationSeconds = DAWN_PIERCE_DURATION_SECONDS.get();
        dawnPierceCooldownSeconds = DAWN_PIERCE_COOLDOWN_SECONDS.get();
        dawnPierceComboDamageMultiplier = DAWN_PIERCE_COMBO_DAMAGE_MULTIPLIER.get();
        dawnPierceComboMoveSpeedPercent = DAWN_PIERCE_COMBO_MOVE_SPEED_PERCENT.get();
        dawnPierceComboReachBonus = DAWN_PIERCE_COMBO_REACH_BONUS.get();
        dawnPierceLowHpThreshold = DAWN_PIERCE_LOW_HP_THRESHOLD.get();
        dawnPierceLowHpDamageMultiplier = DAWN_PIERCE_LOW_HP_DAMAGE_MULTIPLIER.get();
        dawnPierceLowHpLifestealPercent = DAWN_PIERCE_LOW_HP_LIFESTEAL_PERCENT.get();

        // 飞轮效应
        flywheelDistance1 = FLYWHEEL_DISTANCE_1.get();
        flywheelDistance2 = FLYWHEEL_DISTANCE_2.get();
        flywheelDistance3 = FLYWHEEL_DISTANCE_3.get();
        flywheelCooldownSeconds1 = FLYWHEEL_COOLDOWN_1.get();
        flywheelCooldownSeconds2 = FLYWHEEL_COOLDOWN_2.get();
        flywheelCooldownSeconds3 = FLYWHEEL_COOLDOWN_3.get();
        flywheelDurabilityCost = FLYWHEEL_DURABILITY_COST.get();
        flywheelDashSpeed = FLYWHEEL_DASH_SPEED.get();

        // 千破·青溟剑
        qianpoQingMingDamageLevel1 = QIANPO_QINGMING_DAMAGE_LEVEL1.get();
        qianpoQingMingDamageLevel2 = QIANPO_QINGMING_DAMAGE_LEVEL2.get();
        qianpoQingMingDamageLevel3 = QIANPO_QINGMING_DAMAGE_LEVEL3.get();
        qianpoQingMingDamageLevel4 = QIANPO_QINGMING_DAMAGE_LEVEL4.get();
        qianpoQingMingDamageLevel5 = QIANPO_QINGMING_DAMAGE_LEVEL5.get();
        qianpoQingMingVisualEnabled = QIANPO_QINGMING_VISUAL_ENABLED.get();

        // 风踏涟漪
        windRippleLavaSpeed = WIND_RIPPLE_LAVA_SPEED.get();

        // 无中生有·重制
        exNihiloRareChancePerLevel = EX_NIHILO_RARE_CHANCE_PER_LEVEL.get();
        exNihiloRareDiamondWeight = EX_NIHILO_RARE_DIAMOND_WEIGHT.get();
        exNihiloRareScrapWeight = EX_NIHILO_RARE_SCRAP_WEIGHT.get();
        exNihiloRareIngotWeight = EX_NIHILO_RARE_INGOT_WEIGHT.get();
        exNihiloShearGoldenAppleChance = EX_NIHILO_SHEAR_GOLDEN_APPLE_CHANCE.get();
        exNihiloShearEnchantedGoldenAppleChance = EX_NIHILO_SHEAR_ENCHANTED_GOLDEN_APPLE_CHANCE.get();
        exNihiloStoneBlocks = EX_NIHILO_STONE_BLOCKS.get();

        

        // 终界之星
        enableEndStarFlight = ENABLE_END_STAR_FLIGHT.get();
        enableEndStarDamageReduction = ENABLE_END_STAR_DAMAGE_REDUCTION.get();
        endStarDamageReductionPercent = END_STAR_DAMAGE_REDUCTION_PERCENT.get();
        enableEndStarDamageBonus = ENABLE_END_STAR_DAMAGE_BONUS.get();
        endStarMaxBonusPercent = END_STAR_MAX_BONUS_PERCENT.get();
        endStarDropDelay = END_STAR_DROP_DELAY.get();
        endStarDespawnTime = END_STAR_DESPAWN_TIME.get();

        // 人权剑
        swordLightningDamage = SWORD_LIGHTNING_DAMAGE.get();
        swordLightningChance = SWORD_LIGHTNING_CHANCE.get();
        swordHealthCostPercent = SWORD_HEALTH_COST_PERCENT.get();
        swordHealthThreshold = SWORD_HEALTH_THRESHOLD.get();
        swordDamageBoostPercent = SWORD_DAMAGE_BOOST_PERCENT.get();
        swordBuffDuration = SWORD_BUFF_DURATION.get();
        swordCooldown = SWORD_COOLDOWN.get();
        swordPassiveArmor = SWORD_PASSIVE_ARMOR.get();
        swordPassiveDamageReduction = SWORD_PASSIVE_DAMAGE_REDUCTION.get();

        // 星火不灭
        eternalSparkDurationSeconds = ETERNAL_SPARK_DURATION_SECONDS.get();
        eternalSparkBaseDamagePercent = ETERNAL_SPARK_BASE_DAMAGE_PERCENT.get();
        eternalSparkActiveDamagePercent = ETERNAL_SPARK_ACTIVE_DAMAGE_PERCENT.get();
        eternalSparkStackBonusPercent = ETERNAL_SPARK_STACK_BONUS_PERCENT.get();
        eternalSparkSpreadRange = ETERNAL_SPARK_SPREAD_RANGE.get();

        // 无烟冲击
        smokelessDashMaxMultiplier = SMOKELESS_DASH_MAX_MULTIPLIER.get();
        smokelessDashScrollSensitivity = SMOKELESS_DASH_SCROLL_SENSITIVITY.get();
        smokelessDashBaseBoost = SMOKELESS_DASH_BASE_BOOST.get();

        // 伤害/治疗浮动数字
        enableDamagePopup = ENABLE_DAMAGE_POPUP.get();
    }
}
