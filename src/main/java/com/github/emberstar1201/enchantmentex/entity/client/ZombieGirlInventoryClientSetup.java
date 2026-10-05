package com.github.emberstar1201.enchantmentex.entity.client;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.entity.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 丧尸娘背包界面的客户端注册。
 *
 * 必须用 value = Dist.CLIENT 隔离：本类引用了客户端专属的 Screen 类，
 * 若在专用服务端加载会触发 invalid dist 崩溃。
 * 1.20.1（Forge 47.x）尚无 RegisterMenuScreensEvent，
 * 因此在 FMLClientSetupEvent 的 enqueueWork 中注册菜单屏幕工厂。
 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZombieGirlInventoryClientSetup {

    private ZombieGirlInventoryClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // MenuScreens 注册非线程安全，必须放到并行工作队列中执行
        event.enqueueWork(() -> MenuScreens.register(
                ModMenuTypes.ZOMBIE_GIRL_INVENTORY.get(),
                ZombieGirlInventoryScreen::new));
    }
}
