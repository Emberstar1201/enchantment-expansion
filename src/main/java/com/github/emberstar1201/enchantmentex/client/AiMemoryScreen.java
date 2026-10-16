package com.github.emberstar1201.enchantmentex.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

/** 展示当前少女的 AI 压缩事件簿。 */
public final class AiMemoryScreen extends Screen {
    private final Screen parent;
    private final UUID girlId;
    private final String girlName;
    private List<AiConfigurationStore.MemoryEntry> entries;
    private int scroll;

    public AiMemoryScreen(Screen parent, UUID girlId, String girlName) {
        super(Component.translatable("gui.enchantment_expansion.ai.memory.title"));
        this.parent = parent;
        this.girlId = girlId;
        this.girlName = girlName;
    }

    @Override
    protected void init() {
        this.entries = AiConfigurationStore.memory(this.girlId).entries;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.ai.memory.back"),
                        button -> this.minecraft.setScreen(this.parent))
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 18, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(this.girlName), this.width / 2, 34, 0xA0A0A0);
        if (this.entries == null || this.entries.isEmpty()) {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.enchantment_expansion.ai.memory.empty"),
                    this.width / 2, this.height / 2, 0xC0C0C0);
        } else {
            int y = 54;
            int left = Math.max(12, this.width / 2 - 150);
            for (int index = this.entries.size() - 1 - this.scroll; index >= 0 && y < this.height - 42; index--) {
                AiConfigurationStore.MemoryEntry entry = this.entries.get(index);
                graphics.drawString(this.font,
                        Component.translatable("gui.enchantment_expansion.ai.memory.entry", this.entries.size() - index),
                        left, y, 0xE0C070);
                y += 12;
                for (net.minecraft.util.FormattedCharSequence line : this.font.split(
                        Component.literal(entry.text == null ? "" : entry.text), 300)) {
                    if (y >= this.height - 42) break;
                    graphics.drawString(this.font, line, left, y, 0xFFFFFF);
                    y += 10;
                }
                y += 8;
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int max = Math.max(0, (this.entries == null ? 0 : this.entries.size()) - 1);
        this.scroll = Math.max(0, Math.min(max, this.scroll - (int) Math.signum(delta)));
        return true;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
