package com.example.examplemod.enchantment;

import com.example.examplemod.ExampleMod;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

// 附魔注册类：统一管理模组所有自定义附魔的注册
public class ModEnchantments {
    // 创建附魔的延迟注册器，所有附魔都将通过它注册到 "examplemod" 命名空间下
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ExampleMod.MODID);

    // 注册"速射"附魔
    // RegistryObject 是 Forge 提供的延迟引用对象，在注册完成后才会持有实际附魔实例
    public static final RegistryObject<QuickDrawEnchantment> QUICK_DRAW =
            ENCHANTMENTS.register("quick_draw", QuickDrawEnchantment::new);

    // 注册"风踏涟漪"附魔 - 靴子专属：陆地加速+水面行走
    public static final RegistryObject<WindRippleEnchantment> WIND_RIPPLE =
            ENCHANTMENTS.register("wind_ripple", WindRippleEnchantment::new);

    // 在主类构造函数中调用此方法，将注册器绑定到模组事件总线
    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
