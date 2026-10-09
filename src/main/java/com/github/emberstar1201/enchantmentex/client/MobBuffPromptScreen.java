package com.github.emberstar1201.enchantmentex.client;

import com.github.emberstar1201.enchantmentex.network.MobBuffChoicePacket;
import com.github.emberstar1201.enchantmentex.network.NetworkHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 开局确认原版怪物强化的暂停界面。 */
public final class MobBuffPromptScreen extends Screen {
    private static final Component TITLE = Component.literal("世界醒来前，有一件事需要你决定");
    private static final Component LINE_1 = Component.literal("夜色会把獠牙磨得更锋利，骨头也会记住力量。");
    private static final Component LINE_2 = Component.literal("若让它们越过那道门，原版怪物与Boss都将不再是原来的模样。");
    private static final Component LINE_3 = Component.literal("唯有那群不该被归入其中的少女，不在这场变化之内。");
    private static final Component ENABLE = Component.literal("让门打开");
    private static final Component DISABLE = Component.literal("把门关上");

    public MobBuffPromptScreen() {
        super(TITLE);
    }

    public static void open() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new MobBuffPromptScreen());
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        int y = this.height / 2 + 42;
        this.addRenderableWidget(Button.builder(ENABLE, button -> choose(true))
                .bounds(center - 105, y, 100, 20).build());
        this.addRenderableWidget(Button.builder(DISABLE, button -> choose(false))
                .bounds(center + 5, y, 100, 20).build());
    }

    private void choose(boolean enabled) {
        NetworkHandler.CHANNEL.sendToServer(new MobBuffChoicePacket(enabled));
        this.minecraft.setScreen(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        int center = this.width / 2;
        int textColor = 0xE8E8E8;
        graphics.drawCenteredString(this.font, this.title, center, this.height / 2 - 55, 0xFFFFFF);
        graphics.drawCenteredString(this.font, LINE_1, center, this.height / 2 - 25, textColor);
        graphics.drawCenteredString(this.font, LINE_2, center, this.height / 2 - 8, textColor);
        graphics.drawCenteredString(this.font, LINE_3, center, this.height / 2 + 9, 0xB8D8FF);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
