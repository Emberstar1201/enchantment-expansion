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

    public static final RegistryObject<YunLaiSwordmanshipEnchantment> YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("yunlai_swordmanship", YunLaiSwordmanshipEnchantment::new);

    public static final RegistryObject<AncientYunLaiSwordmanshipEnchantment> ANCIENT_YUNLAI_SWORDMANSHIP =
            ENCHANTMENTS.register("ancient_yunlai_swordmanship", AncientYunLaiSwordmanshipEnchantment::new);

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

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
