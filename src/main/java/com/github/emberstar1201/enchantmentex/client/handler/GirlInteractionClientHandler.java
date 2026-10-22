package com.github.emberstar1201.enchantmentex.client.handler;

import com.github.emberstar1201.enchantmentex.EnchantmentExpansion;
import com.github.emberstar1201.enchantmentex.client.GirlInteractionScreen;
import com.github.emberstar1201.enchantmentex.entity.FriendlyGirlInventory;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import com.github.emberstar1201.enchantmentex.network.GirlInteractionPacket;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 少女交互按键、准心提示和面板入口。 */
@Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT)
public final class GirlInteractionClientHandler {
    public static final KeyMapping GIRL_INTERACTION_KEY = new KeyMapping(
            "key." + EnchantmentExpansion.MODID + ".girl_interaction",
            GLFW.GLFW_KEY_E,
            "key.categories." + EnchantmentExpansion.MODID);

    private GirlInteractionClientHandler() {
    }

    @Mod.EventBusSubscriber(modid = EnchantmentExpansion.MODID, value = Dist.CLIENT,
            bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class KeyRegistration {
        private KeyRegistration() {
        }

        @SubscribeEvent
        public static void register(RegisterKeyMappingsEvent event) {
            event.register(GIRL_INTERACTION_KEY);
        }
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || minecraft.screen != null || !GIRL_INTERACTION_KEY.consumeClick()) {
            return;
        }
        Entity target = getTarget(minecraft);
        if (target instanceof FriendlyGirlInventory girl
                && target instanceof LivingEntity living
                && living.isAlive()
                && minecraft.player.distanceToSqr(target) <= 256.0D) {
            minecraft.setScreen(new GirlInteractionScreen(target.getId(), girl.isTamed()));
        }
    }

    @SubscribeEvent
    public static void renderPrompt(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.CROSSHAIR.id())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        Entity target = getTarget(minecraft);
        if (!(target instanceof FriendlyGirlInventory)
                || !(target instanceof LivingEntity living)
                || !living.isAlive()
                || minecraft.player.distanceToSqr(target) > 256.0D) {
            return;
        }
        var text = net.minecraft.network.chat.Component.translatable(
                "gui.enchantment_expansion.girl_interaction.hint",
                GIRL_INTERACTION_KEY.getTranslatedKeyMessage());
        int x = (minecraft.getWindow().getGuiScaledWidth() - minecraft.font.width(text)) / 2;
        int y = minecraft.getWindow().getGuiScaledHeight() / 2 + 12;
        event.getGuiGraphics().drawString(minecraft.font, text, x, y, 0xFFFFFF);
    }

    private static Entity getTarget(Minecraft minecraft) {
        if (minecraft.hitResult instanceof EntityHitResult entityHit) {
            return entityHit.getEntity();
        }
        return null;
    }
}
