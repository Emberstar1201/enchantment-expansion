package com.github.emberstar1201.enchantmentex.client;

import com.github.emberstar1201.enchantmentex.network.GiftPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 珍贵物品送出前的二次确认。 */
public final class GiftPromptScreen extends Screen {
    private final int entityId;
    private final String itemName;

    private GiftPromptScreen(int entityId, String itemName) {
        super(Component.translatable("gui.enchantment_expansion.gift.precious_title"));
        this.entityId = entityId;
        this.itemName = itemName;
    }

    public static void open(int entityId, String itemName) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new GiftPromptScreen(entityId, itemName));
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = this.height / 2 + 28;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.gift.confirm"),
                        button -> confirm())
                .bounds(center - 105, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.gift.cancel"),
                        button -> this.minecraft.setScreen(null))
                .bounds(center + 5, y, 100, 20).build());
    }

    private void confirm() {
        NetworkHandler.CHANNEL.sendToServer(new GiftPacket(this.entityId, true));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int center = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, center, this.height / 2 - 58, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.enchantment_expansion.gift.precious", this.itemName),
                center, this.height / 2 - 20, 0xFFD27F);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.enchantment_expansion.gift.ask"),
                center, this.height / 2 - 3, 0xE8E8E8);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
