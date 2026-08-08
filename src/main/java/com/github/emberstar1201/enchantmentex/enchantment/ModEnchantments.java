package com.github.emberstar1201.enchantmentex.enchantment;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 附魔注册类：统一管理模组所有自定义附魔的注册
public class ModEnchantments {
    // 创建附魔的延迟注册器，所有附魔都将通过它注册到 "examplemod" 命名空间下
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnchantmentExpansion.MODID);

    // ========================================================================
    // 注册"古·云来弓法"附魔（原"速射"重命名）
    // 弓专属：蓄力加速 + 箭矢飞行加速，10级上限
    // 注册ID：ancient_yunlai
    // ========================================================================
    public static final RegistryObject<AncientYunLaiEnchantment> ANCIENT_YUNLAI =
            ENCHANTMENTS.register("ancient_yunlai", AncientYunLaiEnchantment::new);

    // ========================================================================
    // 注册"云来弓法（基础版）"附魔
    // 弓专属：仅蓄力加速，不改变箭矢速度，10级上限
    // 与古·云来弓法不冲突（两者可叠加）
    // 注册ID：yunlai_archery
    // ========================================================================
    public static final RegistryObject<YunLaiArcheryEnchantment> YUNLAI_ARCHERY =
            ENCHANTMENTS.register("yunlai_archery", YunLaiArcheryEnchantment::new);

    // ========================================================================
    // 注册"风踏涟漪"附魔 - 靴子专属：陆地加速+水面行走
    // 注册ID：wind_ripple
    // ========================================================================
    public static final RegistryObject<WindRippleEnchantment> WIND_RIPPLE =
            ENCHANTMENTS.register("wind_ripple", WindRippleEnchantment::new);

    // 注册ID：creation_from_nothing
    public static final RegistryObject<CreationFromNothingEnchantment> CREATION_FROM_NOTHING =
            ENCHANTMENTS.register("creation_from_nothing", CreationFromNothingEnchantment::new);

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
