package com.github.emberstar1201.enchantmentex.client.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.item.ModItems;
import com.github.emberstar1201.enchantmentex.network.AnnihilationOrbPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端监听镰刀技能键和物品 tooltip；服务端仍是能量与施法的最终判定方。 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ResidualScytheClientHandler {
    private ResidualScytheClientHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && ResidualScytheKeybindings.ANNIHILATION_ORB_KEY.consumeClick()) {
            NetworkHandler.CHANNEL.sendToServer(new AnnihilationOrbPacket());
        }
    }

    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(ModItems.RESIDUAL_SCYTHE.get())) {
            int energy = Math.min(ResidualScytheEnergyClient.getEnergy(stack), 80);
            event.getToolTip().add(Component.translatable(
                            "tooltip.enchantment_expansion.residual_scythe.energy", energy, 80)
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED));
        }
    }

    /** 客户端只从当前物品 NBT 读显示值，能量实际由服务端写入并同步。 */
    private static final class ResidualScytheEnergyClient {
        private static int getEnergy(ItemStack stack) {
            return stack.getOrCreateTag().getInt("ResidualScytheEnergy");
        }
    }
}
