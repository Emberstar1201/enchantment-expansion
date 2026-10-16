package com.github.emberstar1201.enchantmentex.client;

import com.github.emberstar1201.enchantmentex.network.GiftPacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 送礼界面：打开后手持物品右键即可提交礼物。 */
public final class GiftScreen extends Screen {
    private final Screen parent;
    private final int entityId;

    public GiftScreen(Screen parent, int entityId) {
        super(Component.translatable("gui.enchantment_expansion.gift.title"));
        this.parent = parent;
        this.entityId = entityId;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.gift.back"),
                        button -> this.minecraft.setScreen(this.parent))
                .bounds(center - 50, this.height / 2 + 48, 100, 20).build());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1 && this.minecraft.player != null) {
            NetworkHandler.CHANNEL.sendToServer(new GiftPacket(this.entityId, false));
            this.minecraft.setScreen(null);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int center = this.width / 2;
        graphics.drawCenteredString(this.font, this.title, center, this.height / 2 - 58, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.enchantment_expansion.gift.instruction"),
                center, this.height / 2 - 22, 0xE8E8E8);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.enchantment_expansion.gift.hint"),
                center, this.height / 2 - 5, 0xB8D8FF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
