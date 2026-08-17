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
    // 引雷 II（Channeling II）
    // 适用物品：三叉戟（TRIDENT） | 最高等级：1 | 获取：钓鱼/沙漠神殿/废弃矿洞
    // ================================================================
    public static final RegistryObject<ChannelingIIEnchantment> CHANNELING_II =
            ENCHANTMENTS.register("channeling_ii", ChannelingIIEnchantment::new);

    // ================================================================
    // 引雷 III（Channeling III）
    // 适用物品：三叉戟（TRIDENT） | 最高等级：1 | 获取：仅限钓鱼
    // ================================================================
    public static final RegistryObject<ChannelingIIIEnchantment> CHANNELING_III =
            ENCHANTMENTS.register("channeling_iii", ChannelingIIIEnchantment::new);

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

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
