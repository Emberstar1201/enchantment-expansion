package com.github.emberstar1201.enchantmentex.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 当前少女的客户端 AI 文本对话界面。 */
public final class AiDialogueScreen extends Screen {
    /** 未发送草稿只保存在客户端内存：按少女 UUID 区分，切屏 / 窗口重建后恢复。 */
    private static final Map<UUID, String> UNSENT_DRAFTS = new HashMap<>();

    private final Screen parent;
    private final UUID girlId;
    private final AiConfigurationStore.TextService service;
    private final AiConfigurationStore.Persona persona;
    private final String speakerName;
    private final int affection;
    private EditBox input;
    private Button sendButton;
    private Component status = Component.empty();
    private boolean requesting;

    public AiDialogueScreen(Screen parent, UUID girlId, AiConfigurationStore.TextService service,
                             AiConfigurationStore.Persona persona, String speakerName, int affection) {
        super(Component.translatable("gui.enchantment_expansion.ai.dialogue.title"));
        this.parent = parent;
        this.girlId = girlId;
        this.service = service;
        this.persona = persona;
        this.speakerName = speakerName;
        this.affection = affection;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int left = Math.max(12, this.width / 2 - 130);
        int y = this.height / 2 - 34;
        this.input = new EditBox(this.font, left, y, 260, 20,
                Component.translatable("gui.enchantment_expansion.ai.dialogue.input"));
        this.input.setMaxLength(2048);
        this.input.setTextColor(0xFFFFFF);
        this.input.setHint(Component.translatable("gui.enchantment_expansion.ai.dialogue.input"));
        // 先回填草稿再监听输入：发送成功后清空输入框时会顺带清掉对应草稿。
        this.input.setValue(UNSENT_DRAFTS.getOrDefault(this.girlId, ""));
        this.input.setResponder(text -> UNSENT_DRAFTS.put(this.girlId, text));
        this.addRenderableWidget(this.input);
        this.sendButton = Button.builder(
                        Component.translatable("gui.enchantment_expansion.ai.dialogue.send"),
                        button -> send())
                .bounds(left, y + 28, 126, 20).build();
        this.sendButton.active = !this.requesting;
        this.addRenderableWidget(this.sendButton);
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.enchantment_expansion.ai.dialogue.back"),
                        button -> closeScreen())
                .bounds(left + 134, y + 28, 126, 20).build());
        this.setInitialFocus(this.input);
    }

    private void send() {
        if (this.requesting) {
            return;
        }
        String userText = this.input.getValue().trim();
        if (userText.isEmpty()) {
            showError("gui.enchantment_expansion.ai.dialogue.empty");
            return;
        }

        this.requesting = true;
        this.sendButton.active = false;
        this.status = Component.translatable("gui.enchantment_expansion.ai.dialogue.requesting");
        AiConfigurationStore.GirlMemory memory = AiConfigurationStore.memory(this.girlId);
        String memorySummary = memory.entries.stream()
                .map(entry -> entry.text == null ? "" : entry.text)
                .filter(text -> !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
        AiServiceClient.generateDialogue(this.service, this.persona, this.speakerName, this.affection,
                        memorySummary, memory.history, userText)
                .whenComplete((dialogue, error) -> this.minecraft.execute(() -> {
                    this.requesting = false;
                    if (this.sendButton != null) {
                        this.sendButton.active = true;
                    }
                    if (error != null) {
                        this.status = Component.empty();
                        displayChat(Component.literal("AI 对话失败：" + AiServiceClient.describeError(error)));
                        return;
                    }
                    this.status = Component.empty();
                    displayChat(Component.literal(this.speakerName + "：" + dialogue.text()));
                    AiConfigurationStore.addConversation(this.girlId, userText, dialogue.text());
                    AiServiceClient.speak(AiConfigurationStore.get().tts, dialogue.text())
                            .exceptionally(ttsError -> {
                                this.minecraft.execute(() -> displayChat(Component.literal(
                                        "TTS 播放失败：" + AiServiceClient.describeError(ttsError))));
                                return null;
                            });
                    this.input.setValue("");
                }));
    }

    private void showError(String key) {
        Component message = Component.translatable(key);
        this.status = message;
        displayChat(message);
    }

    private void displayChat(Component message) {
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(message, false);
        }
    }

    private void closeScreen() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        closeScreen();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            if (this.input != null && this.input.isFocused()) {
                send();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal(this.speakerName), this.width / 2, 38, 0xA0A0A0);
        String enteredText = this.input == null ? "" : this.input.getValue();
        if (!enteredText.isEmpty()) {
            graphics.drawString(this.font, Component.literal("已确认输入：" + enteredText),
                    this.width / 2 - 130, this.height / 2 - 52, 0xFFFFFF);
        }
        if (!this.status.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, this.status, this.width / 2, this.height / 2 + 62, 0xFFFFFF);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
