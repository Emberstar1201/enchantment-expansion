package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

// ========================================================================
// 「原版怪物强化」独立配置文件
//
// 加载本模组后，原版怪物（僵尸系 / 骷髅系 / 蜘蛛 / 苦力怕 / 末影人）
// 的数值会被整体上调。所有数值都在这里集中管理，
// 服务器管理员可单独调整，不污染主 Config.java。
//
// 配置路径：config/enchantment_expansion-mob_buff.toml
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MobBuffConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 总开关
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("总开关：是否启用「原版怪物强化」的全部改动（默认 true）",
                     "关闭后所有怪物恢复原版数值，便于与原版行为做对比测试")
            .define("mobBuff.enabled", true);

    // ================================================================
    // 一、僵尸系（僵尸 / 尸壳 / 溺尸 / 僵尸村民 / 僵尸猪灵）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue ZOMBIE_MIN_HEALTH = BUILDER
            .comment("僵尸系：生成时的最低血量（默认 25.0，原版为 20.0）")
            .defineInRange("zombie.minHealth", 25.0D, 1.0D, 1024.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_MAX_HEALTH = BUILDER
            .comment("僵尸系：生成时的最高血量（默认 30.0）",
                     "实际血量在 [minHealth, maxHealth] 之间随机")
            .defineInRange("zombie.maxHealth", 30.0D, 1.0D, 1024.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_EQUIP_CHANCE = BUILDER
            .comment("僵尸系：生成时「拥有一整套装备」的总概率（百分比，默认 25.0）",
                     "原版为 15% × 难度系数；此处直接替换为固定 25%")
            .defineInRange("zombie.equipChance", 50.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_EQUIP_PIECE_CHANCE = BUILDER
            .comment("僵尸系：逐件穿甲的概率（百分比，默认 25.0，与原版普通难度一致）",
                     "材质等级仍沿用原版（皮革/金/锁链/铁/钻石），因此不会出现下界合金装备")
            .defineInRange("zombie.equipPieceChance", 100.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_WEAPON_CHANCE = BUILDER
            .comment("僵尸系：主手为空时补一把武器（铁剑/铁锹）的概率（百分比，默认 25.0）",
                     "僵尸猪灵、溺尸等已有专属武器的变种不会被覆盖")
            .defineInRange("zombie.weaponChance", 25.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_DROP_CHANCE = BUILDER
            .comment("僵尸系：身上装备的掉落概率（百分比，默认 15.0，原版为 8.5）",
                     "这里指的是「被杀死后装备掉落的概率」，不是物品掉落概率")
            .defineInRange("zombie.equipmentDropChance", 15.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_COPPER_INGOT_CHANCE = BUILDER
            .comment("僵尸系：额外掉落铜锭的概率（百分比，默认 5.0，填 0 关闭）")
            .defineInRange("zombie.dropCopperIngotChance", 5.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_GOLD_INGOT_CHANCE = BUILDER
            .comment("僵尸系：额外掉落金锭的概率（百分比，默认 1.2，填 0 关闭）")
            .defineInRange("zombie.dropGoldIngotChance", 1.2D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_GOLD_NUGGET_CHANCE = BUILDER
            .comment("僵尸系：额外掉落金粒的概率（百分比，默认 2.5，填 0 关闭）")
            .defineInRange("zombie.dropGoldNuggetChance", 2.5D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_IRON_NUGGET_CHANCE = BUILDER
            .comment("僵尸系：额外掉落铁粒的概率（百分比，默认 45.0，填 0 关闭）")
            .defineInRange("zombie.dropIronNuggetChance", 45.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ZOMBIE_IRON_INGOT_CHANCE = BUILDER
            .comment("僵尸系：额外掉落铁锭的概率（百分比，默认 2.5）",
                     "原版僵尸战利品表本身已有约 2.5% 的铁锭，这里是额外追加一份，",
                     "因此实际铁锭掉率约为原版的两倍（「掉落铁锭的概率更高」）")
            .defineInRange("zombie.dropIronIngotChance", 7.5D, 0.0D, 100.0D);

    // ================================================================
    // 二、骷髅系（骷髅 / 流浪者 / 凋灵骷髅）
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue SKELETON_MIN_HEALTH = BUILDER
            .comment("骷髅系：生成时的最低血量（默认 25.0，原版为 20.0）")
            .defineInRange("skeleton.minHealth", 25.0D, 1.0D, 1024.0D);

    private static final ForgeConfigSpec.DoubleValue SKELETON_MAX_HEALTH = BUILDER
            .comment("骷髅系：生成时的最高血量（默认 30.0，与僵尸一致）")
            .defineInRange("skeleton.maxHealth", 30.0D, 1.0D, 1024.0D);

    private static final ForgeConfigSpec.DoubleValue SKELETON_EQUIP_CHANCE = BUILDER
            .comment("骷髅系：生成时穿甲的总概率（百分比，默认 25.0，原版为 15%×难度系数）")
            .defineInRange("skeleton.equipChance", 50.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue SKELETON_EQUIP_PIECE_CHANCE = BUILDER
            .comment("骷髅系：逐件穿甲的概率（百分比，默认 25.0）")
            .defineInRange("skeleton.equipPieceChance", 100.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue SKELETON_DROP_CHANCE = BUILDER
            .comment("骷髅系：身上装备的掉落概率（百分比，默认 15.0，原版为 8.5）")
            .defineInRange("skeleton.equipmentDropChance", 25.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.IntValue SKELETON_BOW_DRAW_TICKS = BUILDER
            .comment("骷髅系：拉满弓所需 tick 数（默认 10，原版为 20；20 tick = 1 秒）",
                     "实现方式：把实际拉弓 tick 按比例放大到原版 20 tick 阈值，",
                     "因此射出的箭依旧是满蓄力（100% 伤害与速度），只是更早射出。",
                     "取值必须 ≤ 20，填 20 等于原版行为")
            .defineInRange("skeleton.bowDrawTicks", 10, 1, 20);

    // ================================================================
    // 三、小僵尸碰撞箱
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue BABY_ZOMBIE_SIZE_ENABLED = BUILDER
            .comment("小僵尸：是否放大其碰撞箱，方便玩家命中（默认 true）")
            .define("babyZombie.enlargeHitbox", true);

    private static final ForgeConfigSpec.DoubleValue BABY_ZOMBIE_SIZE_MULTIPLIER = BUILDER
            .comment("小僵尸：碰撞箱放大倍率（默认 1.5）",
                     "小僵尸原版碰撞箱为 0.3 × 0.975 格；1.5 倍后约为 0.45 × 1.46 格")
            .defineInRange("babyZombie.sizeMultiplier", 1.5D, 1.0D, 4.0D);

    // ================================================================
    // 四、蜘蛛：攻击时生成蜘蛛网
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue SPIDER_WEB_ENABLED = BUILDER
            .comment("蜘蛛：攻击玩家时是否在附近生成蜘蛛网（默认 true）")
            .define("spider.webEnabled", true);

    private static final ForgeConfigSpec.DoubleValue SPIDER_WEB_CHANCE = BUILDER
            .comment("蜘蛛：每次命中玩家触发结网的概率（百分比，默认 50.0）",
                     "蜘蛛网本身会大幅限制走位，若设为 100（每击必触发），",
                     "成群蜘蛛会把玩家直接粘死，因此默认只给一半概率；",
                     "觉得过强可下调到 30 左右，想还原「每击必结网」则填 100")
            .defineInRange("spider.webChance", 50.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.IntValue SPIDER_WEB_RADIUS = BUILDER
            .comment("蜘蛛：结网半径（格，默认 4，以被攻击的玩家为中心）")
            .defineInRange("spider.webRadius", 4, 1, 16);

    private static final ForgeConfigSpec.IntValue SPIDER_WEB_COUNT = BUILDER
            .comment("蜘蛛：每次触发最多生成多少个蜘蛛网（默认 2）")
            .defineInRange("spider.webCount", 2, 1, 16);

    // ================================================================
    // 五、洞穴蜘蛛、溺尸、灾厄、幻翼与劫掠兽
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue CAVE_SPIDER_HEALTH = BUILDER
            .comment("洞穴蜘蛛：最大生命值（默认 14.0）")
            .defineInRange("caveSpider.health", 14.0D, 1.0D, 1024.0D);
    private static final ForgeConfigSpec.DoubleValue CAVE_SPIDER_SIZE_MULTIPLIER = BUILDER
            .comment("洞穴蜘蛛：碰撞箱倍率（默认 1.25）")
            .defineInRange("caveSpider.sizeMultiplier", 1.25D, 1.0D, 4.0D);
    private static final ForgeConfigSpec.DoubleValue CAVE_SPIDER_SPAWN_MULTIPLIER = BUILDER
            .comment("洞穴蜘蛛：自然生成增强倍率（供生成相关兼容配置使用，默认 1.5）")
            .defineInRange("caveSpider.spawnMultiplier", 1.5D, 1.0D, 5.0D);
    private static final ForgeConfigSpec.DoubleValue DROWNED_TRIDENT_CHANCE = BUILDER
            .comment("溺尸：空主手补充三叉戟的概率（默认 50%）")
            .defineInRange("drowned.tridentChance", 50.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue DROWNED_TRIDENT_DROP_CHANCE = BUILDER
            .comment("溺尸：三叉戟掉落概率（默认 15%）")
            .defineInRange("drowned.tridentDropChance", 15.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue PILLAGER_HEALTH = BUILDER.defineInRange("pillager.health", 32.0D, 1.0D, 1024.0D);
    private static final ForgeConfigSpec.DoubleValue PILLAGER_ARMOR = BUILDER.defineInRange("pillager.armor", 4.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue VINDICATOR_HEALTH = BUILDER.defineInRange("vindicator.health", 32.0D, 1.0D, 1024.0D);
    private static final ForgeConfigSpec.DoubleValue VINDICATOR_ARMOR = BUILDER.defineInRange("vindicator.armor", 4.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue EVOKER_HEALTH = BUILDER.defineInRange("evoker.health", 28.0D, 1.0D, 1024.0D);
    private static final ForgeConfigSpec.DoubleValue EVOKER_ARMOR = BUILDER.defineInRange("evoker.armor", 3.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue ILLAGER_EMERALD_CHANCE = BUILDER.defineInRange("illager.emeraldChance", 100.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.IntValue ILLAGER_EMERALD_COUNT = BUILDER.defineInRange("illager.emeraldCount", 2, 1, 16);
    private static final ForgeConfigSpec.DoubleValue ILLAGER_WEAPON_DROP_CHANCE = BUILDER.defineInRange("illager.weaponDropChance", 25.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.DoubleValue PHANTOM_SIZE_MULTIPLIER = BUILDER.defineInRange("phantom.sizeMultiplier", 1.25D, 1.0D, 4.0D);
    private static final ForgeConfigSpec.DoubleValue PHANTOM_SPEED_MULTIPLIER = BUILDER.defineInRange("phantom.speedMultiplier", 1.35D, 1.0D, 5.0D);
    private static final ForgeConfigSpec.DoubleValue PHANTOM_MEMBRANE_DROP_CHANCE = BUILDER.defineInRange("phantom.membraneDropChance", 90.0D, 0.0D, 100.0D);
    private static final ForgeConfigSpec.IntValue PHANTOM_MEMBRANE_MIN = BUILDER.defineInRange("phantom.membraneMin", 3, 0, 16);
    private static final ForgeConfigSpec.IntValue PHANTOM_MEMBRANE_MAX = BUILDER.defineInRange("phantom.membraneMax", 4, 0, 16);
    private static final ForgeConfigSpec.DoubleValue RAVAGER_HEALTH = BUILDER.defineInRange("ravager.health", 200.0D, 1.0D, 2048.0D);
    private static final ForgeConfigSpec.DoubleValue RAVAGER_SPEED_MULTIPLIER = BUILDER.defineInRange("ravager.speedMultiplier", 1.25D, 1.0D, 5.0D);
    private static final ForgeConfigSpec.IntValue PHANTOM_NO_SLEEP_DAYS = BUILDER.defineInRange("phantom.noSleepDays", 6, 1, 100);
    private static final ForgeConfigSpec.IntValue RAVAGER_STUN_TICKS = BUILDER
            .comment("劫掠兽：盾击后的额外眩晕时间（tick，默认 60，即额外 3 秒）")
            .defineInRange("ravager.stunTicks", 60, 0, 600);
    private static final ForgeConfigSpec.IntValue RAVAGER_ATTACK_LOCK_TICKS = BUILDER
            .comment("劫掠兽：攻击后的额外锁定时间（tick，默认 10，即额外 0.5 秒）")
            .defineInRange("ravager.attackLockTicks", 10, 0, 600);

    // ================================================================
    // 六、苦力怕：蓄力更久、移速更快
    // ================================================================
    private static final ForgeConfigSpec.IntValue CREEPER_SWELL_TICKS = BUILDER
            .comment("苦力怕：引爆所需蓄力 tick（默认 50，原版为 30；20 tick = 1 秒）",
                     "数值越大，玩家越有时间逃离爆炸范围")
            .defineInRange("creeper.swellTicks", 50, 30, 200);

    private static final ForgeConfigSpec.DoubleValue CREEPER_SPEED_MULTIPLIER = BUILDER
            .comment("苦力怕：移速倍率（默认 1.4，原版基础移速 0.25）")
            .defineInRange("creeper.speedMultiplier", 1.4D, 1.0D, 5.0D);

    // ================================================================
    // 六、末影人
    // ================================================================
    private static final ForgeConfigSpec.DoubleValue ENDERMAN_HEALTH = BUILDER
            .comment("末影人：生成血量（默认 50.0，原版为 40.0）")
            .defineInRange("enderman.health", 50.0D, 1.0D, 1024.0D);

    private static final ForgeConfigSpec.BooleanValue ENDERMAN_PROJECTILE_VULNERABLE = BUILDER
            .comment("末影人：是否可以被弹射物命中（默认 true）",
                     "开启后弓箭/弩箭/雪球等弹射物不再被瞬移躲避，而是正常造成伤害；",
                     "药水与近战逻辑保持原版不变")
            .define("enderman.vulnerableToProjectiles", true);

    // ================================================================
    // 七、末影龙强化
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENDER_DRAGON_ENABLED = BUILDER
            .comment("末影龙强化：是否启用（默认 true）")
            .define("enderDragon.enabled", true);

    private static final ForgeConfigSpec.DoubleValue ENDER_DRAGON_HEALTH = BUILDER
            .comment("末影龙：最大生命值（默认 1000.0，原版为 200.0）")
            .defineInRange("enderDragon.health", 1000.0D, 200.0D, 2048.0D);

    private static final ForgeConfigSpec.DoubleValue ENDER_DRAGON_ARMOR = BUILDER
            .comment("末影龙：护甲值（默认 15.0）")
            .defineInRange("enderDragon.armor", 15.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue ENDER_DRAGON_LOW_HEALTH_DAMAGE_MULTIPLIER = BUILDER
            .comment("末影龙：低于 50% 生命值后的伤害倍率（默认 2.5，即伤害提升 150%）")
            .defineInRange("enderDragon.lowHealthDamageMultiplier", 2.5D, 1.0D, 10.0D);

    private static final ForgeConfigSpec.IntValue ENDER_DRAGON_ROAR_DAMAGE_SECONDS = BUILDER
            .comment("末影龙：吼叫时给予附近玩家的持续伤害时长（秒，默认 5）")
            .defineInRange("enderDragon.roarDamageSeconds", 5, 1, 30);

    private static final ForgeConfigSpec.IntValue ENDER_DRAGON_ATTACK_COOLDOWN = BUILDER
            .comment("末影龙：主动攻击间隔（tick，默认 60；生命值低于 50% 时减半）")
            .defineInRange("enderDragon.attackCooldown", 60, 20, 600);

    private static final ForgeConfigSpec.IntValue ENDER_DRAGON_ALTAR_BREATH_SECONDS = BUILDER
            .comment("末影龙：首次降至半血时，祭坛附近龙息持续时间（秒，默认 3）")
            .defineInRange("enderDragon.altarBreathSeconds", 3, 1, 15);

    // ================================================================
    // 八、凋零与凋零骷髅强化
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue WITHER_ENABLED = BUILDER
            .comment("凋零强化：是否启用（默认 true）")
            .define("wither.enabled", true);

    private static final ForgeConfigSpec.DoubleValue WITHER_HEALTH = BUILDER
            .comment("凋零：最大生命值（默认 800.0，原版为 300.0）")
            .defineInRange("wither.health", 800.0D, 300.0D, 4096.0D);

    private static final ForgeConfigSpec.DoubleValue WITHER_ARMOR = BUILDER
            .comment("凋零：护甲值（默认 14.0）")
            .defineInRange("wither.armor", 14.0D, 0.0D, 100.0D);

    private static final ForgeConfigSpec.DoubleValue WITHER_LOW_HEALTH_DAMAGE_REDUCTION = BUILDER
            .comment("凋零：低于 50% 生命值后的普通伤害减免比例（默认 0.2）",
                     "带 DamageTypeTags.BYPASSES_ARMOR 的伤害不受此减免影响")
            .defineInRange("wither.lowHealthDamageReduction", 0.2D, 0.0D, 1.0D);

    private static final ForgeConfigSpec.DoubleValue WITHER_SKULL_DAMAGE_MULTIPLIER = BUILDER
            .comment("凋零头颅：造成伤害的倍率（默认 2.0）")
            .defineInRange("wither.skullDamageMultiplier", 2.0D, 0.1D, 10.0D);

    private static final ForgeConfigSpec.BooleanValue WITHER_MOTHER_SKULL_ENABLED = BUILDER
            .comment("凋零：是否启用母弹飞行后分裂（默认 true）")
            .define("wither.motherSkullEnabled", true);

    private static final ForgeConfigSpec.IntValue WITHER_MOTHER_SKULL_FLIGHT_TICKS = BUILDER
            .comment("凋零母弹：飞行多少 tick 后分裂（默认 100）")
            .defineInRange("wither.motherSkullFlightTicks", 100, 1, 1200);

    private static final ForgeConfigSpec.IntValue WITHER_MOTHER_SKULL_SPLIT_COUNT = BUILDER
            .comment("凋零母弹：分裂数量（默认 2，允许 2~3）")
            .defineInRange("wither.motherSkullSplitCount", 2, 2, 3);

    private static final ForgeConfigSpec.DoubleValue WITHER_DEATH_EXPLOSION_POWER = BUILDER
            .comment("凋零：死亡爆炸威力（默认 3.0，使用 NONE 不破坏方块）")
            .defineInRange("wither.deathExplosionPower", 3.0D, 0.0D, 20.0D);

    private static final ForgeConfigSpec.DoubleValue WITHER_SKELETON_LOW_HEALTH_DAMAGE_MULTIPLIER = BUILDER
            .comment("凋灵骷髅：低于 50% 生命值后的伤害倍率（默认 2.0）")
            .defineInRange("witherSkeleton.lowHealthDamageMultiplier", 2.0D, 1.0D, 10.0D);

    private static final ForgeConfigSpec.IntValue WITHER_EXPERIENCE = BUILDER
            .comment("凋零：死亡经验值（默认 500）")
            .defineInRange("wither.experience", 500, 0, 100000);

    private static final ForgeConfigSpec.DoubleValue HOSTILE_EXPERIENCE_BONUS_PERCENT = BUILDER
            .comment("敌对与中立敌对生物：经验掉落增加比例（百分比，默认 450，即原版的 5.5 倍）",
                     "凋零和末影龙不受此倍率影响")
            .defineInRange("experience.hostileBonusPercent", 450.0D, 0.0D, 10000.0D);

    private static final ForgeConfigSpec.IntValue ENDER_DRAGON_FIRST_EXPERIENCE = BUILDER
            .comment("末影龙：首次击败经验值（默认 48000）")
            .defineInRange("enderDragon.firstExperience", 48000, 0, 1000000);

    private static final ForgeConfigSpec.IntValue ENDER_DRAGON_RESPAWN_EXPERIENCE = BUILDER
            .comment("末影龙：重生后击败经验值（默认 24000）")
            .defineInRange("enderDragon.respawnExperience", 24000, 0, 1000000);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 游戏内命令用的配置项登记表（路径 → 配置项 + 类型 + 取值范围）
    //
    // 为什么不直接用 SPEC.getValues()？
    //   getValues() 返回的是 UnmodifiableConfig（纯结构快照），既能拿到
    //   配置项对象、也读不到 defineInRange 的上下界；而 ConfigValue.set(T)
    //   在字节码层面不做任何范围校验（只检查 spec / childConfig 非 null），
    //   越界值会被原样写进 toml。因此这里手工登记范围，由命令层先校验再写入。
    // ================================================================
    private static final Map<String, Entry> ENTRIES;

    static {
        Map<String, Entry> map = new LinkedHashMap<>();
        add(map, "mobBuff.enabled", ValueType.BOOLEAN, ENABLED, 0.0D, 0.0D);

        add(map, "zombie.minHealth", ValueType.DOUBLE, ZOMBIE_MIN_HEALTH, 1.0D, 1024.0D);
        add(map, "zombie.maxHealth", ValueType.DOUBLE, ZOMBIE_MAX_HEALTH, 1.0D, 1024.0D);
        add(map, "zombie.equipChance", ValueType.DOUBLE, ZOMBIE_EQUIP_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.equipPieceChance", ValueType.DOUBLE, ZOMBIE_EQUIP_PIECE_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.weaponChance", ValueType.DOUBLE, ZOMBIE_WEAPON_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.equipmentDropChance", ValueType.DOUBLE, ZOMBIE_DROP_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.dropCopperIngotChance", ValueType.DOUBLE, ZOMBIE_COPPER_INGOT_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.dropGoldIngotChance", ValueType.DOUBLE, ZOMBIE_GOLD_INGOT_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.dropGoldNuggetChance", ValueType.DOUBLE, ZOMBIE_GOLD_NUGGET_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.dropIronNuggetChance", ValueType.DOUBLE, ZOMBIE_IRON_NUGGET_CHANCE, 0.0D, 100.0D);
        add(map, "zombie.dropIronIngotChance", ValueType.DOUBLE, ZOMBIE_IRON_INGOT_CHANCE, 0.0D, 100.0D);

        add(map, "skeleton.minHealth", ValueType.DOUBLE, SKELETON_MIN_HEALTH, 1.0D, 1024.0D);
        add(map, "skeleton.maxHealth", ValueType.DOUBLE, SKELETON_MAX_HEALTH, 1.0D, 1024.0D);
        add(map, "skeleton.equipChance", ValueType.DOUBLE, SKELETON_EQUIP_CHANCE, 0.0D, 100.0D);
        add(map, "skeleton.equipPieceChance", ValueType.DOUBLE, SKELETON_EQUIP_PIECE_CHANCE, 0.0D, 100.0D);
        add(map, "skeleton.equipmentDropChance", ValueType.DOUBLE, SKELETON_DROP_CHANCE, 0.0D, 100.0D);
        add(map, "skeleton.bowDrawTicks", ValueType.INT, SKELETON_BOW_DRAW_TICKS, 1.0D, 20.0D);

        add(map, "babyZombie.enlargeHitbox", ValueType.BOOLEAN, BABY_ZOMBIE_SIZE_ENABLED, 0.0D, 0.0D);
        add(map, "babyZombie.sizeMultiplier", ValueType.DOUBLE, BABY_ZOMBIE_SIZE_MULTIPLIER, 1.0D, 4.0D);

        add(map, "spider.webEnabled", ValueType.BOOLEAN, SPIDER_WEB_ENABLED, 0.0D, 0.0D);
        add(map, "spider.webChance", ValueType.DOUBLE, SPIDER_WEB_CHANCE, 0.0D, 100.0D);
        add(map, "spider.webRadius", ValueType.INT, SPIDER_WEB_RADIUS, 1.0D, 16.0D);
        add(map, "spider.webCount", ValueType.INT, SPIDER_WEB_COUNT, 1.0D, 16.0D);

        add(map, "caveSpider.health", ValueType.DOUBLE, CAVE_SPIDER_HEALTH, 1.0D, 1024.0D);
        add(map, "caveSpider.sizeMultiplier", ValueType.DOUBLE, CAVE_SPIDER_SIZE_MULTIPLIER, 1.0D, 4.0D);
        add(map, "caveSpider.spawnMultiplier", ValueType.DOUBLE, CAVE_SPIDER_SPAWN_MULTIPLIER, 1.0D, 5.0D);
        add(map, "drowned.tridentChance", ValueType.DOUBLE, DROWNED_TRIDENT_CHANCE, 0.0D, 100.0D);
        add(map, "drowned.tridentDropChance", ValueType.DOUBLE, DROWNED_TRIDENT_DROP_CHANCE, 0.0D, 100.0D);
        add(map, "pillager.health", ValueType.DOUBLE, PILLAGER_HEALTH, 1.0D, 1024.0D);
        add(map, "pillager.armor", ValueType.DOUBLE, PILLAGER_ARMOR, 0.0D, 100.0D);
        add(map, "vindicator.health", ValueType.DOUBLE, VINDICATOR_HEALTH, 1.0D, 1024.0D);
        add(map, "vindicator.armor", ValueType.DOUBLE, VINDICATOR_ARMOR, 0.0D, 100.0D);
        add(map, "evoker.health", ValueType.DOUBLE, EVOKER_HEALTH, 1.0D, 1024.0D);
        add(map, "evoker.armor", ValueType.DOUBLE, EVOKER_ARMOR, 0.0D, 100.0D);
        add(map, "illager.emeraldChance", ValueType.DOUBLE, ILLAGER_EMERALD_CHANCE, 0.0D, 100.0D);
        add(map, "illager.emeraldCount", ValueType.INT, ILLAGER_EMERALD_COUNT, 1.0D, 16.0D);
        add(map, "illager.weaponDropChance", ValueType.DOUBLE, ILLAGER_WEAPON_DROP_CHANCE, 0.0D, 100.0D);
        add(map, "phantom.sizeMultiplier", ValueType.DOUBLE, PHANTOM_SIZE_MULTIPLIER, 1.0D, 4.0D);
        add(map, "phantom.speedMultiplier", ValueType.DOUBLE, PHANTOM_SPEED_MULTIPLIER, 1.0D, 5.0D);
        add(map, "phantom.membraneDropChance", ValueType.DOUBLE, PHANTOM_MEMBRANE_DROP_CHANCE, 0.0D, 100.0D);
        add(map, "phantom.membraneMin", ValueType.INT, PHANTOM_MEMBRANE_MIN, 0.0D, 16.0D);
        add(map, "phantom.membraneMax", ValueType.INT, PHANTOM_MEMBRANE_MAX, 0.0D, 16.0D);
        add(map, "phantom.noSleepDays", ValueType.INT, PHANTOM_NO_SLEEP_DAYS, 1.0D, 100.0D);
        add(map, "ravager.health", ValueType.DOUBLE, RAVAGER_HEALTH, 1.0D, 2048.0D);
        add(map, "ravager.speedMultiplier", ValueType.DOUBLE, RAVAGER_SPEED_MULTIPLIER, 1.0D, 5.0D);
        add(map, "ravager.stunTicks", ValueType.INT, RAVAGER_STUN_TICKS, 0.0D, 600.0D);
        add(map, "ravager.attackLockTicks", ValueType.INT, RAVAGER_ATTACK_LOCK_TICKS, 0.0D, 600.0D);

        add(map, "creeper.swellTicks", ValueType.INT, CREEPER_SWELL_TICKS, 30.0D, 200.0D);
        add(map, "creeper.speedMultiplier", ValueType.DOUBLE, CREEPER_SPEED_MULTIPLIER, 1.0D, 5.0D);

        add(map, "enderman.health", ValueType.DOUBLE, ENDERMAN_HEALTH, 1.0D, 1024.0D);
        add(map, "enderman.vulnerableToProjectiles", ValueType.BOOLEAN, ENDERMAN_PROJECTILE_VULNERABLE, 0.0D, 0.0D);

        add(map, "enderDragon.enabled", ValueType.BOOLEAN, ENDER_DRAGON_ENABLED, 0.0D, 0.0D);
        add(map, "enderDragon.health", ValueType.DOUBLE, ENDER_DRAGON_HEALTH, 200.0D, 2048.0D);
        add(map, "enderDragon.armor", ValueType.DOUBLE, ENDER_DRAGON_ARMOR, 0.0D, 100.0D);
        add(map, "enderDragon.lowHealthDamageMultiplier", ValueType.DOUBLE,
                ENDER_DRAGON_LOW_HEALTH_DAMAGE_MULTIPLIER, 1.0D, 10.0D);
        add(map, "enderDragon.roarDamageSeconds", ValueType.INT,
                ENDER_DRAGON_ROAR_DAMAGE_SECONDS, 1.0D, 30.0D);
        add(map, "enderDragon.attackCooldown", ValueType.INT,
                ENDER_DRAGON_ATTACK_COOLDOWN, 20.0D, 600.0D);
        add(map, "enderDragon.altarBreathSeconds", ValueType.INT,
                ENDER_DRAGON_ALTAR_BREATH_SECONDS, 1.0D, 15.0D);

        add(map, "wither.enabled", ValueType.BOOLEAN, WITHER_ENABLED, 0.0D, 0.0D);
        add(map, "wither.health", ValueType.DOUBLE, WITHER_HEALTH, 300.0D, 4096.0D);
        add(map, "wither.armor", ValueType.DOUBLE, WITHER_ARMOR, 0.0D, 100.0D);
        add(map, "wither.lowHealthDamageReduction", ValueType.DOUBLE,
                WITHER_LOW_HEALTH_DAMAGE_REDUCTION, 0.0D, 1.0D);
        add(map, "wither.skullDamageMultiplier", ValueType.DOUBLE,
                WITHER_SKULL_DAMAGE_MULTIPLIER, 0.1D, 10.0D);
        add(map, "wither.motherSkullEnabled", ValueType.BOOLEAN,
                WITHER_MOTHER_SKULL_ENABLED, 0.0D, 0.0D);
        add(map, "wither.motherSkullFlightTicks", ValueType.INT,
                WITHER_MOTHER_SKULL_FLIGHT_TICKS, 1.0D, 1200.0D);
        add(map, "wither.motherSkullSplitCount", ValueType.INT,
                WITHER_MOTHER_SKULL_SPLIT_COUNT, 2.0D, 3.0D);
        add(map, "wither.deathExplosionPower", ValueType.DOUBLE,
                WITHER_DEATH_EXPLOSION_POWER, 0.0D, 20.0D);
        add(map, "witherSkeleton.lowHealthDamageMultiplier", ValueType.DOUBLE,
                WITHER_SKELETON_LOW_HEALTH_DAMAGE_MULTIPLIER, 1.0D, 10.0D);
        add(map, "wither.experience", ValueType.INT, WITHER_EXPERIENCE, 0.0D, 100000.0D);
        add(map, "experience.hostileBonusPercent", ValueType.DOUBLE,
                HOSTILE_EXPERIENCE_BONUS_PERCENT, 0.0D, 10000.0D);
        add(map, "enderDragon.firstExperience", ValueType.INT,
                ENDER_DRAGON_FIRST_EXPERIENCE, 0.0D, 1000000.0D);
        add(map, "enderDragon.respawnExperience", ValueType.INT,
                ENDER_DRAGON_RESPAWN_EXPERIENCE, 0.0D, 1000000.0D);

        ENTRIES = Collections.unmodifiableMap(map);
    }

    private static void add(Map<String, Entry> map, String path, ValueType type,
                            ForgeConfigSpec.ConfigValue<?> value, double min, double max) {
        map.put(path, new Entry(path, type, value, min, max));
    }

    /** 配置项类型（决定命令参数的解析方式与校验方式） */
    public enum ValueType {
        BOOLEAN, INT, DOUBLE
    }

    /** 一个可在游戏内读写的配置项 */
    public static final class Entry {

        private final String path;
        private final ValueType type;
        private final ForgeConfigSpec.ConfigValue<?> value;
        private final double min;
        private final double max;

        private Entry(String path, ValueType type, ForgeConfigSpec.ConfigValue<?> value,
                      double min, double max) {
            this.path = path;
            this.type = type;
            this.value = value;
            this.min = min;
            this.max = max;
        }

        /** toml 中的配置路径，如 zombie.maxHealth */
        public String path() {
            return path;
        }

        public ValueType type() {
            return type;
        }

        /** 当前值的展示字符串（供 list / get 输出） */
        public String currentValue() {
            Object current = value.get();
            if (type == ValueType.DOUBLE && current instanceof Number number) {
                return trimTrailingZeros(number.doubleValue());
            }
            return String.valueOf(current);
        }

        /** 默认值的展示字符串 */
        public String defaultValue() {
            Object def = value.getDefault();
            if (type == ValueType.DOUBLE && def instanceof Number number) {
                return trimTrailingZeros(number.doubleValue());
            }
            return String.valueOf(def);
        }

        /** 取值范围的可读描述 */
        public String rangeText() {
            return switch (type) {
                case BOOLEAN -> "true / false";
                case INT -> (long) min + " ~ " + (long) max;
                case DOUBLE -> trimTrailingZeros(min) + " ~ " + trimTrailingZeros(max);
            };
        }

        /**
         * 按字符串写入配置并落盘，然后刷新静态缓存使其立即生效。
         * 输入非法时抛 IllegalArgumentException，消息可直接反馈给命令执行者。
         *
         * ★ ConfigValue.set() 自身不做范围校验，必须在这里挡住越界值，
         *   否则 toml 里会被写入非法数值（游戏内表现为数值直接生效，非常危险）。
         */
        @SuppressWarnings("unchecked")
        public void setFromString(String raw) {
            String text = raw == null ? "" : raw.trim();
            switch (type) {
                case BOOLEAN -> {
                    if ("true".equalsIgnoreCase(text)) {
                        ((ForgeConfigSpec.ConfigValue<Boolean>) value).set(Boolean.TRUE);
                    } else if ("false".equalsIgnoreCase(text)) {
                        ((ForgeConfigSpec.ConfigValue<Boolean>) value).set(Boolean.FALSE);
                    } else {
                        throw new IllegalArgumentException("应为 true 或 false");
                    }
                }
                case INT -> {
                    int parsed;
                    try {
                        parsed = Integer.parseInt(text);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("应为整数，范围 " + rangeText());
                    }
                    if (parsed < min || parsed > max) {
                        throw new IllegalArgumentException("超出范围，应为 " + rangeText());
                    }
                    ((ForgeConfigSpec.ConfigValue<Integer>) value).set(parsed);
                }
                case DOUBLE -> {
                    double parsed;
                    try {
                        parsed = Double.parseDouble(text);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("应为数字，范围 " + rangeText());
                    }
                    if (parsed < min || parsed > max) {
                        throw new IllegalArgumentException("超出范围，应为 " + rangeText());
                    }
                    ((ForgeConfigSpec.ConfigValue<Double>) value).set(parsed);
                }
            }
            value.save();
            refreshCache();
        }
    }

    /** 去掉浮点数的多余尾随零（5.0 → 5，1.2 保持 1.2） */
    private static String trimTrailingZeros(double value) {
        if (!Double.isInfinite(value) && value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    /** 全部可调整项（按登记顺序，供 list 命令遍历） */
    public static Collection<Entry> entries() {
        return ENTRIES.values();
    }

    /** 按路径查找配置项；不存在返回 null */
    public static Entry entry(String path) {
        return ENTRIES.get(path);
    }

    // ================================================================
    // 缓存到静态字段的配置值（运行时读取这些字段）
    // ================================================================
    public static boolean enabled;

    public static double zombieMinHealth;
    public static double zombieMaxHealth;
    public static double zombieEquipChance;
    public static double zombieEquipPieceChance;
    public static double zombieWeaponChance;
    public static double zombieEquipmentDropChance;
    public static double zombieDropCopperIngotChance;
    public static double zombieDropGoldIngotChance;
    public static double zombieDropGoldNuggetChance;
    public static double zombieDropIronNuggetChance;
    public static double zombieDropIronIngotChance;

    public static double skeletonMinHealth;
    public static double skeletonMaxHealth;
    public static double skeletonEquipChance;
    public static double skeletonEquipPieceChance;
    public static double skeletonEquipmentDropChance;
    public static int skeletonBowDrawTicks;

    public static boolean babyZombieEnlargeHitbox;
    public static double babyZombieSizeMultiplier;

    public static boolean spiderWebEnabled;
    public static double spiderWebChance;
    public static int spiderWebRadius;
    public static int spiderWebCount;

    public static double caveSpiderHealth;
    public static double caveSpiderSizeMultiplier;
    public static double caveSpiderSpawnMultiplier;
    public static double drownedTridentChance;
    public static double drownedTridentDropChance;
    public static double pillagerHealth;
    public static double pillagerArmor;
    public static double vindicatorHealth;
    public static double vindicatorArmor;
    public static double evokerHealth;
    public static double evokerArmor;
    public static double illagerEmeraldChance;
    public static int illagerEmeraldCount;
    public static double illagerWeaponDropChance;
    public static double phantomSizeMultiplier;
    public static double phantomSpeedMultiplier;
    public static double phantomMembraneDropChance;
    public static int phantomMembraneMin;
    public static int phantomMembraneMax;

    public static int creeperSwellTicks;
    public static double creeperSpeedMultiplier;

    public static int phantomNoSleepDays;
    public static double ravagerHealth;
    public static double ravagerSpeedMultiplier;
    public static int ravagerStunTicks;
    public static int ravagerAttackLockTicks;

    public static double enderManHealth;
    public static boolean enderManProjectileVulnerable;

    public static boolean enderDragonEnabled;
    public static double enderDragonHealth;
    public static double enderDragonArmor;
    public static double enderDragonLowHealthDamageMultiplier;
    public static int enderDragonRoarDamageSeconds;
    public static int enderDragonAttackCooldown;
    public static int enderDragonAltarBreathSeconds;

    public static boolean witherEnabled;
    public static double witherHealth;
    public static double witherArmor;
    public static double witherLowHealthDamageReduction;
    public static double witherSkullDamageMultiplier;
    public static boolean witherMotherSkullEnabled;
    public static int witherMotherSkullFlightTicks;
    public static int witherMotherSkullSplitCount;
    public static double witherDeathExplosionPower;
    public static double witherSkeletonLowHealthDamageMultiplier;
    public static int witherExperience;
    public static double hostileExperienceBonusPercent;
    public static int enderDragonFirstExperience;
    public static int enderDragonRespawnExperience;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：本类会被自动注册到所有 MOD 配置的加载事件上，
        //   只有当事件对应的就是「本模组怪物强化」这份配置时才读取数值。
        //   否则在其他配置加载时，尚未加载的 ConfigValue 直接 .get()
        //   会抛 "Cannot get config value before config is loaded"。
        if (event.getConfig() == null || event.getConfig().getSpec() != MobBuffConfig.SPEC) {
            return;
        }
        refreshCache();
    }

    /**
     * 把配置项的当前值同步到静态缓存字段。
     * 配置加载（onLoad）与游戏内命令改动后都会调用，
     * 保证事件处理器读到的永远是最新值。
     *
     * ★ ConfigValue 内部带缓存，但 set(T) 会同步更新该缓存，
     *   所以 set 之后立刻 get() 拿到的是新值，这里无需 clearCache()。
     */
    private static void refreshCache() {
        enabled = ENABLED.get();

        zombieMinHealth = ZOMBIE_MIN_HEALTH.get();
        zombieMaxHealth = ZOMBIE_MAX_HEALTH.get();
        zombieEquipChance = ZOMBIE_EQUIP_CHANCE.get();
        zombieEquipPieceChance = ZOMBIE_EQUIP_PIECE_CHANCE.get();
        zombieWeaponChance = ZOMBIE_WEAPON_CHANCE.get();
        zombieEquipmentDropChance = ZOMBIE_DROP_CHANCE.get();
        zombieDropCopperIngotChance = ZOMBIE_COPPER_INGOT_CHANCE.get();
        zombieDropGoldIngotChance = ZOMBIE_GOLD_INGOT_CHANCE.get();
        zombieDropGoldNuggetChance = ZOMBIE_GOLD_NUGGET_CHANCE.get();
        zombieDropIronNuggetChance = ZOMBIE_IRON_NUGGET_CHANCE.get();
        zombieDropIronIngotChance = ZOMBIE_IRON_INGOT_CHANCE.get();

        skeletonMinHealth = SKELETON_MIN_HEALTH.get();
        skeletonMaxHealth = SKELETON_MAX_HEALTH.get();
        skeletonEquipChance = SKELETON_EQUIP_CHANCE.get();
        skeletonEquipPieceChance = SKELETON_EQUIP_PIECE_CHANCE.get();
        skeletonEquipmentDropChance = SKELETON_DROP_CHANCE.get();
        skeletonBowDrawTicks = SKELETON_BOW_DRAW_TICKS.get();

        babyZombieEnlargeHitbox = BABY_ZOMBIE_SIZE_ENABLED.get();
        babyZombieSizeMultiplier = BABY_ZOMBIE_SIZE_MULTIPLIER.get();

        spiderWebEnabled = SPIDER_WEB_ENABLED.get();
        spiderWebChance = SPIDER_WEB_CHANCE.get();
        spiderWebRadius = SPIDER_WEB_RADIUS.get();
        spiderWebCount = SPIDER_WEB_COUNT.get();

        caveSpiderHealth = CAVE_SPIDER_HEALTH.get();
        caveSpiderSizeMultiplier = CAVE_SPIDER_SIZE_MULTIPLIER.get();
        caveSpiderSpawnMultiplier = CAVE_SPIDER_SPAWN_MULTIPLIER.get();
        drownedTridentChance = DROWNED_TRIDENT_CHANCE.get();
        drownedTridentDropChance = DROWNED_TRIDENT_DROP_CHANCE.get();
        pillagerHealth = PILLAGER_HEALTH.get();
        pillagerArmor = PILLAGER_ARMOR.get();
        vindicatorHealth = VINDICATOR_HEALTH.get();
        vindicatorArmor = VINDICATOR_ARMOR.get();
        evokerHealth = EVOKER_HEALTH.get();
        evokerArmor = EVOKER_ARMOR.get();
        illagerEmeraldChance = ILLAGER_EMERALD_CHANCE.get();
        illagerEmeraldCount = ILLAGER_EMERALD_COUNT.get();
        illagerWeaponDropChance = ILLAGER_WEAPON_DROP_CHANCE.get();
        phantomSizeMultiplier = PHANTOM_SIZE_MULTIPLIER.get();
        phantomSpeedMultiplier = PHANTOM_SPEED_MULTIPLIER.get();
        phantomMembraneDropChance = PHANTOM_MEMBRANE_DROP_CHANCE.get();
        phantomMembraneMin = PHANTOM_MEMBRANE_MIN.get();
        phantomMembraneMax = PHANTOM_MEMBRANE_MAX.get();

        creeperSwellTicks = CREEPER_SWELL_TICKS.get();
        creeperSpeedMultiplier = CREEPER_SPEED_MULTIPLIER.get();

        phantomNoSleepDays = PHANTOM_NO_SLEEP_DAYS.get();
        ravagerHealth = RAVAGER_HEALTH.get();
        ravagerSpeedMultiplier = RAVAGER_SPEED_MULTIPLIER.get();
        ravagerStunTicks = RAVAGER_STUN_TICKS.get();
        ravagerAttackLockTicks = RAVAGER_ATTACK_LOCK_TICKS.get();

        enderManHealth = ENDERMAN_HEALTH.get();
        enderManProjectileVulnerable = ENDERMAN_PROJECTILE_VULNERABLE.get();

        enderDragonEnabled = ENDER_DRAGON_ENABLED.get();
        enderDragonHealth = ENDER_DRAGON_HEALTH.get();
        enderDragonArmor = ENDER_DRAGON_ARMOR.get();
        enderDragonLowHealthDamageMultiplier = ENDER_DRAGON_LOW_HEALTH_DAMAGE_MULTIPLIER.get();
        enderDragonRoarDamageSeconds = ENDER_DRAGON_ROAR_DAMAGE_SECONDS.get();
        enderDragonAttackCooldown = ENDER_DRAGON_ATTACK_COOLDOWN.get();
        enderDragonAltarBreathSeconds = ENDER_DRAGON_ALTAR_BREATH_SECONDS.get();

        witherEnabled = WITHER_ENABLED.get();
        witherHealth = WITHER_HEALTH.get();
        witherArmor = WITHER_ARMOR.get();
        witherLowHealthDamageReduction = WITHER_LOW_HEALTH_DAMAGE_REDUCTION.get();
        witherSkullDamageMultiplier = WITHER_SKULL_DAMAGE_MULTIPLIER.get();
        witherMotherSkullEnabled = WITHER_MOTHER_SKULL_ENABLED.get();
        witherMotherSkullFlightTicks = WITHER_MOTHER_SKULL_FLIGHT_TICKS.get();
        witherMotherSkullSplitCount = WITHER_MOTHER_SKULL_SPLIT_COUNT.get();
        witherDeathExplosionPower = WITHER_DEATH_EXPLOSION_POWER.get();
        witherSkeletonLowHealthDamageMultiplier = WITHER_SKELETON_LOW_HEALTH_DAMAGE_MULTIPLIER.get();
        witherExperience = WITHER_EXPERIENCE.get();
        hostileExperienceBonusPercent = HOSTILE_EXPERIENCE_BONUS_PERCENT.get();
        enderDragonFirstExperience = ENDER_DRAGON_FIRST_EXPERIENCE.get();
        enderDragonRespawnExperience = ENDER_DRAGON_RESPAWN_EXPERIENCE.get();
    }

    // ================================================================
    // 运行工具方法
    // ================================================================

    /** 僵尸系随机血量（区间被自动纠正，防止 min > max 时取到非法值） */
    public static double rollZombieHealth(net.minecraft.util.RandomSource random) {
        double min = Math.min(zombieMinHealth, zombieMaxHealth);
        double max = Math.max(zombieMinHealth, zombieMaxHealth);
        return min + random.nextDouble() * (max - min);
    }

    /** 骷髅系随机血量 */
    public static double rollSkeletonHealth(net.minecraft.util.RandomSource random) {
        double min = Math.min(skeletonMinHealth, skeletonMaxHealth);
        double max = Math.max(skeletonMinHealth, skeletonMaxHealth);
        return min + random.nextDouble() * (max - min);
    }

    /** 骷髅拉满弓所需 tick，钳制到 [1, 20] 防止 Mixin 中除零或反效果 */
    public static int getSkeletonBowDrawTicks() {
        return Math.max(1, Math.min(20, skeletonBowDrawTicks));
    }
}
