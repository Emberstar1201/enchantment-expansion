package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 附魔注册类：统一管理模组所有自定义附魔的注册
public class ModEnchantments {
    // 创建附魔的延迟注册器，所有附魔都将通过它注册到 "enchantment_expansion" 命名空间下
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnchantmentExpansion.MODID);

    public static final RegistryObject<AncientYunLaiEnchantment> ANCIENT_YUNLAI =
            ENCHANTMENTS.register("ancient_yunlai", AncientYunLaiEnchantment::new);

    public static final RegistryObject<YunLaiArcheryEnchantment> YUNLAI_ARCHERY =
            ENCHANTMENTS.register("yunlai_archery", YunLaiArcheryEnchantment::new);

    public static final RegistryObject<WindRippleEnchantment> WIND_RIPPLE =
            ENCHANTMENTS.register("wind_ripple", WindRippleEnchantment::new);

    public static final RegistryObject<CreationFromNothingEnchantment> CREATION_FROM_NOTHING =
            ENCHANTMENTS.register("creation_from_nothing", CreationFromNothingEnchantment::new);

    public static final RegistryObject<ElegantCatwalkEnchantment> ELEGANT_CATWALK =
            ENCHANTMENTS.register("elegant_catwalk", ElegantCatwalkEnchantment::new);

    public static final RegistryObject<SnatchEnchantment> SNATCH =
            ENCHANTMENTS.register("snatch", SnatchEnchantment::new);

    public static final RegistryObject<PlunderEnchantment> PLUNDER =
            ENCHANTMENTS.register("plunder", PlunderEnchantment::new);

    public static final RegistryObject<DawnEnchantment> DAWN =
            ENCHANTMENTS.register("dawn", DawnEnchantment::new);

    public static final RegistryObject<EndApproachesEnchantment> END_APPROACHES =
            ENCHANTMENTS.register("end_approaches", EndApproachesEnchantment::new);

    public static final RegistryObject<GlacialArrowEnchantment> GLACIAL_ARROW =
            ENCHANTMENTS.register("glacial_arrow", GlacialArrowEnchantment::new);

    public static final RegistryObject<AutoSmeltEnchantment> AUTO_SMELT =
            ENCHANTMENTS.register("auto_smelt", AutoSmeltEnchantment::new);

    public static final RegistryObject<ArtisanLegacyEnchantment> ARTISAN_LEGACY =
            ENCHANTMENTS.register("artisan_legacy", ArtisanLegacyEnchantment::new);

    public static final RegistryObject<LevisEchoEnchantment> LEVIS_ECHO =
            ENCHANTMENTS.register("levis_echo", LevisEchoEnchantment::new);

    public static final RegistryObject<BloodthirstEnchantment> BLOODTHIRST =
            ENCHANTMENTS.register("bloodthirst", BloodthirstEnchantment::new);

    // ================================================================
    // 熵增（Entropy）
    // 适用物品：武器（WEAPON） | 最高等级：III | 获取：附魔台/宝箱/村民
    // ================================================================
    public static final RegistryObject<EntropyEnchantment> ENTROPY =
            ENCHANTMENTS.register("entropy", EntropyEnchantment::new);

    public static final RegistryObject<FlywheelEffectEnchantment> FLYWHEEL_EFFECT =
            ENCHANTMENTS.register("flywheel_effect", FlywheelEffectEnchantment::new);

    // ================================================================
    // 无中生有·重制（Ex Nihilo）
    // 适用物品：镐 + 剪刀 | 最高等级：III | 获取：附魔台/宝箱/村民
    // ================================================================
    public static final RegistryObject<ExNihiloEnchantment> EX_NIHILO =
            ENCHANTMENTS.register("ex_nihilo", ExNihiloEnchantment::new);

    // ================================================================
    // 雷电之戟（Lightning Spear）
    // 统一引雷 II/III 为两等级附魔。
    // 适用物品：三叉戟（TRIDENT） | 最高等级：II
    //  - I 级：雨天（无需雷暴）击中召唤闪电。
    //  - II 级：任意天气与维度（下界、末地）击中召唤闪电。
    // 获取方式：钓鱼 / 沙漠神殿 / 废弃矿洞（II 级钓鱼更稀有）。
    // ================================================================
    public static final RegistryObject<LightningSpearEnchantment> LIGHTNING_SPEAR =
            ENCHANTMENTS.register("lightning_spear", LightningSpearEnchantment::new);

    // ================================================================
    // 云来剑法 & 古·云来剑法
    // 适用物品：剑（WEAPON） | 最高等级：I | 获取：附魔台 / 仅宝箱+村民
    // ================================================================
    public static final RegistryObject<YunLaiSwordmanshipEnchantment> YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("yunlai_swordmanship", YunLaiSwordmanshipEnchantment::new);

    public static final RegistryObject<AncientYunLaiSwordmanshipEnchantment> ANCIENT_YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("ancient_yunlai_swordmanship", AncientYunLaiSwordmanshipEnchantment::new);

    // ================================================================
    // 千破·青溟剑（Qianpo Qingming Sword）
    // 适用物品：剑（WEAPON） | 最高等级：V | 获取：附魔台（概率较低）
    // ================================================================
    public static final RegistryObject<QianpoQingMingSwordEnchantment> QIANPO_QINGMING_SWORD =
            ENCHANTMENTS.register("qianpo_qingming_sword", QianpoQingMingSwordEnchantment::new);

    // ================================================================
    // 星火不灭（Eternal Spark）
    // 适用物品：剑/斧/三叉戟（WEAPON） | 最高等级：I | 获取：合成配方（下界之星×1+烈焰棒×4+书×4）
    // ================================================================
    public static final RegistryObject<EternalSparkEnchantment> ETERNAL_SPARK =
            ENCHANTMENTS.register("eternal_spark", EternalSparkEnchantment::new);

    // ================================================================
    // 无烟冲击（Smokeless Dash）
    // 适用物品：鞘翅（ARMOR_CHEST，运行时仅鞘翅生效） | 最高等级：I | 获取：附魔台/宝箱/村民
    // ================================================================
    public static final RegistryObject<SmokelessDashEnchantment> SMOKELESS_DASH =
            ENCHANTMENTS.register("smokeless_dash", SmokelessDashEnchantment::new);

    // ================================================================
    // 迅捷之弩（Swift Crossbow）
    // 适用物品：弩（CROSSBOW） | 最高等级：IV | 获取：仅宝箱
    // 效果：弩箭附加魔法伤害（I:+2/II:+4/III:+6/IV:+8，无视护甲）+ 装填加速
    // ================================================================
    public static final RegistryObject<SwiftCrossbowEnchantment> SWIFT_CROSSBOW =
            ENCHANTMENTS.register("swift_crossbow", SwiftCrossbowEnchantment::new);

    // ================================================================
    // 农业生产系附魔（三个一起注册）
    //   spring_harvest   春华秋实：锄头，一键开垦 + 一键收获补种（I/II）
    //   all_nature_revive 万物回春：锄头，右键范围催熟（I/II）
    //   fertile_bounty   丰饶之息：锄头，生长光环 + 收获翻倍（I/II）
    // ================================================================
    public static final RegistryObject<SpringHarvestEnchantment> SPRING_HARVEST =
            ENCHANTMENTS.register("spring_harvest", SpringHarvestEnchantment::new);

    public static final RegistryObject<AllNatureReviveEnchantment> ALL_NATURE_REVIVE =
            ENCHANTMENTS.register("all_nature_revive", AllNatureReviveEnchantment::new);

    public static final RegistryObject<FertileBountyEnchantment> FERTILE_BOUNTY =
            ENCHANTMENTS.register("fertile_bounty", FertileBountyEnchantment::new);

    // ================================================================
    // 幽匿行者（Dark Walker）
    // 适用物品：靴子（ARMOR_FEET） | 最高等级：I | 获取：附魔台 + 遗迹宝箱
    // 效果：深暗之域中不触发幽匿方块、不产生振动、监守者无法检测
    // ================================================================
    public static final RegistryObject<DarkWalkerEnchantment> DARK_WALKER =
            ENCHANTMENTS.register("dark_walker", DarkWalkerEnchantment::new);

    // ================================================================
    // 爆破箭矢（Explosive Arrow）
    // 适用物品：弓 + 弩（BOW/弩） | 最高等级：III | 获取：附魔台/宝箱/村民
    // 效果：箭命中后产生小型爆炸（不破坏地形），等级提升半径与伤害
    // ================================================================
    public static final RegistryObject<ExplosiveArrowEnchantment> EXPLOSIVE_ARROW =
            ENCHANTMENTS.register("explosive_arrow", ExplosiveArrowEnchantment::new);

    // ================================================================
    // 贯穿链条（Chain Arrow）
    // 适用物品：弓 + 弩（BOW/弩） | 最高等级：III | 获取：附魔台/宝箱/村民
    // 效果：箭命中后弹射到附近下一个敌人，等级提升弹射次数
    // ================================================================
    public static final RegistryObject<ChainArrowEnchantment> CHAIN_ARROW =
            ENCHANTMENTS.register("chain_arrow", ChainArrowEnchantment::new);

    // ================================================================
    // 温度恒定（Temperature Constant）
    // 适用物品：护甲任意部位（ARMOR） | 最高等级：I | 获取：附魔台/宝箱/村民
    // 效果：免疫冰冻（细雪）与高温（火焰/岩浆/灼热地面）伤害
    // ================================================================
    public static final RegistryObject<TemperatureConstantEnchantment> TEMPERATURE_CONSTANT =
            ENCHANTMENTS.register("temperature_constant", TemperatureConstantEnchantment::new);

    // ================================================================
    // 坠落缓冲（Fall Cushion）
    // 适用物品：靴子（ARMOR_FEET） | 最高等级：II | 获取：附魔台/宝箱/村民
    // 效果：高坠落地减伤 + 冲击波击退周围敌人
    // ================================================================
    public static final RegistryObject<FallCushionEnchantment> FALL_CUSHION =
            ENCHANTMENTS.register("fall_cushion", FallCushionEnchantment::new);

    // ================================================================
    // 连锁挖掘（Chain Breaker）
    // 适用物品：镐/斧/锹（DIGGER） | 最高等级：III | 获取：附魔台/宝箱/村民
    // 效果：按住 ~ 键挖掘时连锁破坏周围同类型方块（3×3×3 / 5×5×5 / 9×9×9）
    // ================================================================
    public static final RegistryObject<ChainBreakerEnchantment> CHAIN_BREAKER =
            ENCHANTMENTS.register("chain_breaker", ChainBreakerEnchantment::new);

    // ================================================================
    // 难度馈赠（Difficulty Gift）
    // 适用物品：武器（剑/斧/三叉戟）+ 盔甲 | 最高等级：I | 获取：附魔台/宝箱/村民
    // 效果：根据当前游戏难度提供固定数值加成（伤害/攻速/护甲/韧性）
    // ================================================================
    public static final RegistryObject<DifficultyGiftEnchantment> DIFFICULTY_GIFT =
            ENCHANTMENTS.register("difficulty_gift", DifficultyGiftEnchantment::new);

    // ================================================================
    // 蓄积（Accumulate）
    // 适用物品：剑/斧/三叉戟（WEAPON） | 最高等级：III | 获取：附魔台/宝箱/村民
    // 效果：长按右键蓄力，每 5 秒完成一阶（上限=附魔等级），
    //       蓄满后下一次攻击造成阶级倍数伤害 + 击退（中断条件详见 Handler）
    // ================================================================
    public static final RegistryObject<AccumulateEnchantment> ACCUMULATE =
            ENCHANTMENTS.register("accumulate", AccumulateEnchantment::new);

    // 末影箭（Ender Arrow）：弓/弩命中后将射手传送至命中位置。
    public static final RegistryObject<EnderArrowEnchantment> ENDER_ARROW =
            ENCHANTMENTS.register("ender_arrow", EnderArrowEnchantment::new);

    // 画饼充饥（Illusory Feast）：武器命中生物时恢复饥饿值和饱食度。
    public static final RegistryObject<IllusoryFeastEnchantment> ILLUSORY_FEAST =
            ENCHANTMENTS.register("illusory_feast", IllusoryFeastEnchantment::new);

    // 自动修复（Auto Repair）：宝藏附魔，每隔一段时间自动恢复武器/工具/盔甲耐久。
    // I级: 100 ticks, II级: 80 ticks, III级: 60 ticks
    public static final RegistryObject<AutoRepairEnchantment> AUTO_REPAIR =
            ENCHANTMENTS.register("auto_repair", AutoRepairEnchantment::new);

    // ================================================================
    // 深海的涟漪（Deep Sea Ripple）
    // 适用物品：靴子（ARMOR_FEET） | 最高等级：I | 获取：附魔台/宝箱/村民
    // 效果：Shift + 鼠标滚轮调节水中游泳速度倍率（默认上限 3.0，可配置）
    // ================================================================
    public static final RegistryObject<DeepSeaRippleEnchantment> DEEP_SEA_RIPPLE =
            ENCHANTMENTS.register("deep_sea_ripple", DeepSeaRippleEnchantment::new);

    // ================================================================
    // 经验馈赠（Experience Gift）
    // 适用物品：剑/斧/三叉戟/弓/弩（WEAPON） | 最高等级：I
    // 效果：击杀怪物后额外掉落 50% 经验值
    // 获取：附魔台/宝箱/钓鱼/村民交易
    // ================================================================
    public static final RegistryObject<ExperienceGiftEnchantment> EXPERIENCE_GIFT =
            ENCHANTMENTS.register("experience_gift", ExperienceGiftEnchantment::new);

    // ================================================================
    // 耐久强化（Durability boost）
    // 适用物品：所有可损耗耐久物品（BREAKABLE） | 最高等级：III
    // 效果：I级 50%/II级 66%/III级 75% 概率不消耗耐久（等效耐久 ×2/×3/×4）
    // 获取：附魔台/宝箱/村民交易 | 与原版耐久互斥
    // ================================================================
    public static final RegistryObject<DurabilityBoostEnchantment> DURABILITY_BOOST =
            ENCHANTMENTS.register("durability_boost", DurabilityBoostEnchantment::new);

    // ================================================================
    // 中国制造（Made in China）
    // 恶搞附魔：耐久强化 + 匠心传承 + 原版耐久 三者混合体（耐用、耐操、经济实惠于一身）
    // 适用物品：所有可损耗耐久物品（BREAKABLE） | 最高等级：I
    // 效果：每 3 秒自动恢复 1 点耐久（融合原版耐久/耐久强化/自动修复概念）
    //       镐/斧/锹额外触发"匠心传承"效果（记忆方块、加速、额外掉落）
    // 获取：附魔台 + 村民交易 + 遗迹宝箱（非宝藏附魔，三种途径均可获得）
    // 互斥：与原版耐久 Unbreaking / 耐久强化 / 匠心传承 三者互斥
    // ================================================================
    public static final RegistryObject<MadeInChinaEnchantment> MADE_IN_CHINA =
            ENCHANTMENTS.register("made_in_china", MadeInChinaEnchantment::new);

    // ================================================================
    // 羽翼（Feather Wing）
    // 适用物品：鞘翅（ARMOR_CHEST） | 最高等级：I
    // 效果：鞘翅飞行时免疫撞墙伤害
    // 获取：仅末地船/末地城宝箱（20% 概率），宝藏附魔
    // 冲突：无（不与任何鞘翅附魔冲突）
    // ================================================================
    public static final RegistryObject<FeatherWingEnchantment> FEATHER_WING =
            ENCHANTMENTS.register("feather_wing", FeatherWingEnchantment::new);

    // ================================================================
    // 狙击（Sniper）
    // 适用物品：弓/弩（BOW） | 最高等级：I
    // 效果：箭矢每飞行 10 格伤害 +20%，上限 +100%（50 格满额）
    // 获取：附魔台/村民交易/钓鱼（非宝藏）
    // ================================================================
    public static final RegistryObject<SniperEnchantment> SNIPER =
            ENCHANTMENTS.register("sniper", SniperEnchantment::new);

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
