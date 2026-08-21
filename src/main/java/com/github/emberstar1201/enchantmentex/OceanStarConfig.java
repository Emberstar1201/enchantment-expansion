package com.github.emberstar1201.enchantmentex;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

// ========================================================================
// 「海洋之星」独立配置文件
//
// 为所有海洋之星效果提供独立开关/数值，服务器管理员可单独调整，
// 不污染主 Config.java（主配置已较庞大）。
//
// 配置路径：config/enchantment_expansion-ocean_star.toml
// （在 mods.toml 中设置 configPath 由 Forge 自动生成）
// ========================================================================
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class OceanStarConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // ================================================================
    // 水下挖掘
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_MINING_PENALTY_IMMUNITY = BUILDER
            .comment("海洋之星：免疫水下挖掘惩罚（恢复原版在水中/不在地面时被削弱的挖掘速度，默认 true）")
            .define("oceanStar.enableMiningPenaltyImmunity", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_MINING_FATIGUE_IMMUNITY = BUILDER
            .comment("海洋之星：免疫挖掘疲劳（远古守卫者释放的『挖掘疲劳』效果，默认 true）")
            .define("oceanStar.enableMiningFatigueImmunity", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_UNDERWATER_MINING_BOOST = BUILDER
            .comment("海洋之星：水下挖掘速度加成（默认 true）")
            .define("oceanStar.enableUnderwaterMiningBoost", true);

    private static final ForgeConfigSpec.DoubleValue UNDERWATER_MINING_BOOST_MULTIPLIER = BUILDER
            .comment("海洋之星：水下挖掘速度倍率（1.25 = 正常陆地挖掘速度的 125%，默认 1.25）")
            .defineInRange("oceanStar.underwaterMiningBoostMultiplier", 1.25, 1.0, 10.0);

    // ================================================================
    // 水流减速免疫
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_WATER_MOVEMENT_IMMUNITY = BUILDER
            .comment("海洋之星：免疫水流减速（水中移动接近陆地速度，且不受水流推力影响，默认 true）")
            .define("oceanStar.enableWaterMovementImmunity", true);

    private static final ForgeConfigSpec.DoubleValue SWIM_SPEED_MULTIPLIER = BUILDER
            .comment("海洋之星：水中游泳速度倍率（补偿水中拖拽减速，2.0 ≈ 接近陆地步行速度，默认 2.0）")
            .defineInRange("oceanStar.swimSpeedMultiplier", 2.0, 1.0, 10.0);

    // ================================================================
    // 水下氧气 / 窒息免疫
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_OXYGEN_IMMUNITY = BUILDER
            .comment("海洋之星：水下不扣氧气（保持氧气值满，默认 true）",
                    "同时免疫溺水窒息伤害（氧气耗尽导致的 DROWN 伤害将完全无效）")
            .define("oceanStar.enableOxygenImmunity", true);

    // ================================================================
    // 守卫者中立化
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_GUARDIAN_NEUTRAL = BUILDER
            .comment("海洋之星：守卫者中立化（守卫者/远古守卫者不主动攻击持有者，但被玩家攻击后会正常反击，默认 true）")
            .define("oceanStar.enableGuardianNeutral", true);

    // ================================================================
    // 海洋神殿强制宝箱生成
    // ================================================================
    private static final ForgeConfigSpec.BooleanValue ENABLE_MONUMENT_CHEST_GENERATE = BUILDER
            .comment("海洋之星：在海洋神殿强制生成宝箱（默认 true）",
                    "原版海洋神殿本身没有宝箱；开启后，每个神殿实例生成时会在其顶部中央自动放置一个宝箱，",
                    "宝箱内 100% 直接含有一个海洋之星，并保证『每个神殿实例仅一个，不随时间或区块刷新重复生成』",
                    "（已存在的旧世界在玩家首次接近加载神殿区块时也会补上宝箱）。")
            .define("oceanStar.enableMonumentChestGenerate", true);

    private static final ForgeConfigSpec.BooleanValue ENABLE_MONUMENT_CHEST_LOOT = BUILDER
            .comment("海洋之星：海洋神殿容器内容注入（默认 true）",
                    "当容器本身处于海洋神殿结构内且该神殿尚未发放过海洋之星时，首次打开会 100% 放入一个。",
                    "强制宝箱生成已关闭时，该机制可配合数据包/其他模组在神殿内放置的容器使用。",
                    "无论哪种来源，均以『神殿实例』为唯一标识持久化去重，保证每神殿仅一个海洋之星。")
            .define("oceanStar.enableMonumentChestLoot", true);

    // 配置 SPEC 实例（供 registerConfig 注册）
    static final ForgeConfigSpec SPEC = BUILDER.build();

    // ================================================================
    // 缓存到静态字段的配置值（运行时读取这些字段）
    // ================================================================
    public static boolean enableMiningPenaltyImmunity;
    public static boolean enableMiningFatigueImmunity;
    public static boolean enableUnderwaterMiningBoost;
    public static double underwaterMiningBoostMultiplier;
    public static boolean enableWaterMovementImmunity;
    public static double swimSpeedMultiplier;
    public static boolean enableOxygenImmunity;
    public static boolean enableGuardianNeutral;
    public static boolean enableMonumentChestGenerate;
    public static boolean enableMonumentChestLoot;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        // ★ 关键过滤：本类会被自动注册到所有 MOD 配置的加载事件上，
        //   只有当事件对应的就是「海洋之星」这份配置时才读取数值。
        //   否则在主配置（enchantment_expansion-common.toml）加载时，
        //   本类尚未加载的 ConfigValue 直接 .get() 会抛
        //   "Cannot get config value before config is loaded"。
        if (event.getConfig() == null || event.getConfig().getSpec() != OceanStarConfig.SPEC) {
            return;
        }

        enableMiningPenaltyImmunity = ENABLE_MINING_PENALTY_IMMUNITY.get();
        enableMiningFatigueImmunity = ENABLE_MINING_FATIGUE_IMMUNITY.get();
        enableUnderwaterMiningBoost = ENABLE_UNDERWATER_MINING_BOOST.get();
        underwaterMiningBoostMultiplier = UNDERWATER_MINING_BOOST_MULTIPLIER.get();
        enableWaterMovementImmunity = ENABLE_WATER_MOVEMENT_IMMUNITY.get();
        swimSpeedMultiplier = SWIM_SPEED_MULTIPLIER.get();
        enableOxygenImmunity = ENABLE_OXYGEN_IMMUNITY.get();
        enableGuardianNeutral = ENABLE_GUARDIAN_NEUTRAL.get();
        enableMonumentChestGenerate = ENABLE_MONUMENT_CHEST_GENERATE.get();
        enableMonumentChestLoot = ENABLE_MONUMENT_CHEST_LOOT.get();
    }
}