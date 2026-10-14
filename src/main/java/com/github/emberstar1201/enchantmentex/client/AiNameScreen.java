package com.github.emberstar1201.enchantmentex.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** 首次与某只少女 AI 对话时设置她的名字；名字由调用方写入该实体。 */
public final class AiNameScreen extends Screen {
    private final Screen parent;
    private final String defaultName;
    private final Consumer<String> onSaved;
    private EditBox name;
    private Component status = Component.empty();

    public AiNameScreen(Screen parent, String defaultName, Consumer<String> onSaved) {
        super(Component.translatable("gui.enchantment_expansion.ai.name.title"));
        this.parent = parent;
        this.defaultName = defaultName;
        this.onSaved = onSaved;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int left = this.width / 2 - 130;
        int y = this.height / 2 - 20;
        this.name = new EditBox(this.font, left, y, 260, 20,
                Component.translatable("gui.enchantment_expansion.ai.name.input"));
        this.name.setValue(this.defaultName == null ? "" : this.defaultName);
        this.name.setHint(Component.translatable("gui.enchantment_expansion.ai.name.input"));
        this.addRenderableWidget(this.name);
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.ai.name.save"),
                        button -> save())
                .bounds(left, y + 28, 126, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.ai.name.cancel"),
                        button -> this.minecraft.setScreen(this.parent))
                .bounds(left + 134, y + 28, 126, 20).build());
        this.setInitialFocus(this.name);
    }

    private void save() {
        String value = this.name.getValue().trim();
        if (value.isEmpty()) {
            this.status = Component.translatable("gui.enchantment_expansion.ai.name.empty");
            return;
        }
        // 不再写全局配置；名字交给调用方持久化到实体，保证每只独立、不跨存档。
        this.onSaved.accept(value);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 62, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
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
