package com.github.emberstar1201.enchantmentex.client.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 注册湮灭之镰的大招按键，玩家可以在控制设置中重新绑定。 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ResidualScytheKeybindings {
    public static final KeyMapping ANNIHILATION_ORB_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".annihilation_orb",
            GLFW.GLFW_KEY_V,
            "key.categories." + EnchantmentExpansion.MODID
    );

    private ResidualScytheKeybindings() {
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ANNIHILATION_ORB_KEY);
    }
}
