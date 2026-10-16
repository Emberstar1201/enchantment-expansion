package com.github.emberstar1201.enchantmentex.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.function.Consumer;

/** AI 服务、本地人设和少女名称配置界面。 */
public final class AiConfigurationScreen extends Screen {
    private static final String[] TEXT_PROVIDERS = {
            "OpenAI", "DeepSeek", "Kimi", "Qwen", "New API", "Google (Gemini)", "自定义服务商"
    };
    private static final String[] TTS_PROVIDERS = {"MiniMax", "阿里云 Qwen TTS"};
    private static final String[] PERSONAS = {"zombie_girl", "drowned_girl", "survivor_girl"};

    private final Screen parent;
    private final AiConfigurationStore.AiConfiguration configuration;
    private int textProvider;
    private int ttsProvider;
    private int personaIndex;
    private String newApiEndpoint;
    private String customTextEndpoint;
    private boolean textKeyVisible;
    private boolean ttsKeyVisible;
    private EditBox textKey;
    private EditBox textModel;
    private EditBox textEndpoint;
    private EditBox textProviderName;
    private EditBox ttsKey;
    private EditBox ttsVoice;
    private EditBox ttsModel;
    private MultiLineEditBox personaText;
    private int section;
    private Component status = Component.empty();
    // 长报错最多画 3 行；超出部分通过悬停 tooltip 与点击复制查看，避免直接画出屏幕。
    private static final int STATUS_MAX_LINES = 3;
    private static final int STATUS_LINE_HEIGHT = 10;
    private int statusBoxX;
    private int statusBoxY;
    private int statusBoxWidth;
    private int statusBoxHeight;
    private boolean statusTruncated;

    public AiConfigurationScreen(Screen parent, String selectedPersona) {
        super(Component.translatable("gui.enchantment_expansion.ai.title"));
        this.parent = parent;
        this.configuration = AiConfigurationStore.get();
        this.personaIndex = indexOf(PERSONAS, selectedPersona);
        this.textProvider = indexOf(TEXT_PROVIDERS, configuration.text.provider);
        this.ttsProvider = indexOf(TTS_PROVIDERS, configuration.tts.provider);
        this.newApiEndpoint = textProvider == 4 ? configuration.text.endpoint : "";
        this.customTextEndpoint = textProvider == 6 ? configuration.text.endpoint : "";
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int left = Math.max(8, this.width / 2 - 150);
        int right = this.width / 2 + 8;
        int footerY = Math.max(44, this.height - 26);
        int tabWidth = 96;
        int tabGap = 4;
        int tabStart = (this.width - tabWidth * 3 - tabGap * 2) / 2;

        this.addRenderableWidget(Button.builder(Component.literal("文本配置"), button -> switchSection(0))
                .bounds(tabStart, 24, tabWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("TTS 配置"), button -> switchSection(1))
                .bounds(tabStart + tabWidth + tabGap, 24, tabWidth, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("人设编辑"), button -> switchSection(2))
                .bounds(tabStart + (tabWidth + tabGap) * 2, 24, tabWidth, 20).build());

        if (section == 0) {
            initTextSection(left, right, footerY);
        } else if (section == 1) {
            initTtsSection(left, right, footerY);
        } else {
            initPersonaSection(left, right, footerY);
        }

        this.addRenderableWidget(Button.builder(Component.literal("返回"), button -> closeScreen())
                .bounds(right, footerY, 142, 20).build());
        this.setInitialFocus(section == 0 && this.textKey != null
                ? this.textKey
                : section == 1 && this.ttsVoice != null
                ? this.ttsVoice
                : this.personaText);
    }

    private void initTextSection(int left, int right, int footerY) {
        int y = 52;
        this.addRenderableWidget(Button.builder(Component.literal("服务商：" + TEXT_PROVIDERS[textProvider]),
                        button -> cycleTextProvider())
                .bounds(left, y, 300, 20).build());
        y += 26;

        boolean customText = textProvider == 6;
        boolean textEndpointVisible = textProvider == 4 || customText;
        if (customText) {
            this.textProviderName = edit(left, y, "服务商名称（可选）", configuration.text.providerName);
            this.textEndpoint = edit(right, y, "API 地址", customTextEndpoint);
            y += 24;
        } else {
            this.textProviderName = null;
            this.textEndpoint = textEndpointVisible ? edit(left, y, "New API 地址", configuration.text.endpoint) : null;
            if (textEndpointVisible) y += 24;
        }
        this.textKey = editSecret(left, y, "API 密钥", configuration.text.apiKey, textKeyVisible,
                visible -> {
                    saveFields();
                    textKeyVisible = visible;
                    rebuildWidgets();
                });
        this.textModel = edit(right, y, "模型 ID", configuration.text.model);
        y += 30;
        this.addRenderableWidget(Button.builder(Component.literal("测试"), button -> testText())
                .bounds(left, footerY, 68, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
                .bounds(left + 74, footerY, 68, 20).build());
        this.ttsKey = null;
        this.ttsVoice = null;
        this.ttsModel = null;
        this.personaText = null;
    }

    private void initTtsSection(int left, int right, int footerY) {
        int y = 52;
        this.addRenderableWidget(Button.builder(Component.literal(configuration.tts.enabled ? "TTS：开启" : "TTS：关闭"),
                        button -> {
                            saveFields();
                            configuration.tts.enabled = !configuration.tts.enabled;
                            this.rebuildWidgets();
                        })
                .bounds(left, y, 142, 20).build());
        this.addRenderableWidget(Button.builder(Component.literal("服务商：" + TTS_PROVIDERS[ttsProvider]),
                        button -> cycleTtsProvider()).bounds(right, y, 142, 20).build());
        y += 26;

        this.ttsKey = editSecret(left, y, "TTS API 密钥", configuration.tts.apiKey, ttsKeyVisible,
                visible -> {
                    saveFields();
                    ttsKeyVisible = visible;
                    rebuildWidgets();
                });
        this.ttsVoice = edit(right, y, "音色 ID（可选）", configuration.tts.voiceId);
        y += 24;
        this.ttsModel = edit(left, y, "TTS 模型", configuration.tts.model);
        this.textKey = null;
        this.textModel = null;
        this.textEndpoint = null;
        this.textProviderName = null;
        this.personaText = null;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
                .bounds(left, footerY, 68, 20).build());
    }

    private void initPersonaSection(int left, int right, int footerY) {
        int y = 52;
        this.addRenderableWidget(Button.builder(Component.literal("少女：" + personaLabel()),
                        button -> cyclePersona())
                .bounds(left, y, 300, 20).build());
        y += 26;

        // 给底部最多三行状态文字预留空间，人设正文使用普通自然语言，不要求 JSON 格式。
        y += 18;
        int statusY = footerY - 38;
        int textHeight = Math.max(36, statusY - y - 8);
        this.personaText = editPersona(y, textHeight, persona().toPromptText());
        this.personaText.setCharacterLimit(8192);
        this.textKey = null;
        this.textModel = null;
        this.textEndpoint = null;
        this.textProviderName = null;
        this.ttsKey = null;
        this.ttsVoice = null;
        this.ttsModel = null;
        this.addRenderableWidget(Button.builder(Component.literal("保存"), button -> save())
                .bounds(left, footerY, 68, 20).build());
    }

    private EditBox edit(int x, int y, String hint, String value) {
        EditBox box = new EditBox(this.font, x, y, 142, 20, Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        this.addRenderableWidget(box);
        return box;
    }

    private EditBox editSecret(int x, int y, String hint, String value, boolean visible,
                               Consumer<Boolean> onToggle) {
        int fieldWidth = 118;
        EditBox box = new EditBox(this.font, x, y, fieldWidth, 20, Component.literal(hint));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        box.setHint(Component.literal(hint));
        if (!visible) {
            box.setFormatter((text, cursor) -> FormattedCharSequence.forward(
                    "•".repeat(text.length()), Style.EMPTY));
        }
        this.addRenderableWidget(box);
        this.addRenderableWidget(Button.builder(Component.literal(visible ? "◉" : "◌"), button ->
                        onToggle.accept(!visible))
                .bounds(x + fieldWidth + 4, y, 20, 20)
                .tooltip(Tooltip.create(Component.literal(visible ? "隐藏 API 密钥" : "显示 API 密钥")))
                .build());
        return box;
    }

    private MultiLineEditBox editPersona(int y, int height, String value) {
        int width = Math.min(300, this.width - 16);
        int x = (this.width - width) / 2;
        MultiLineEditBox box = new MultiLineEditBox(
                this.font,
                x,
                y,
                width,
                height,
                Component.empty(),
                Component.empty());
        box.setValue(value == null ? "" : value);
        this.addRenderableWidget(box);
        return box;
    }

    private void switchSection(int value) {
        saveFields();
        this.section = value;
        this.rebuildWidgets();
    }

    private void cycleTextProvider() {
        saveFields();
        if (textProvider == 4 && textEndpoint != null) {
            newApiEndpoint = textEndpoint.getValue();
        }
        if (textProvider == 6 && textEndpoint != null) {
            customTextEndpoint = textEndpoint.getValue();
        }
        textProvider = (textProvider + 1) % TEXT_PROVIDERS.length;
        configuration.text.provider = TEXT_PROVIDERS[textProvider];
        if (textProviderName != null) {
            configuration.text.providerName = textProviderName.getValue();
        }
        if (textProvider == 0) {
            configuration.text.endpoint = "https://api.openai.com/v1/chat/completions";
        } else if (textProvider == 1) {
            configuration.text.endpoint = "https://api.deepseek.com/chat/completions";
        } else if (textProvider == 2) {
            configuration.text.endpoint = "https://api.moonshot.cn/v1/chat/completions";
        } else if (textProvider == 3) {
            configuration.text.endpoint = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";
        } else if (textProvider == 4) {
            configuration.text.endpoint = newApiEndpoint;
        } else if (textProvider == 5) {
            configuration.text.endpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
        } else {
            configuration.text.endpoint = customTextEndpoint;
        }
        this.rebuildWidgets();
    }

    private void cycleTtsProvider() {
        saveFields();
        ttsProvider = (ttsProvider + 1) % TTS_PROVIDERS.length;
        configuration.tts.provider = TTS_PROVIDERS[ttsProvider];
        if (ttsProvider == 0) {
            configuration.tts.endpoint = "https://api.minimax.io/v1/t2a_v2";
            if (configuration.tts.model == null || configuration.tts.model.isBlank()
                    || configuration.tts.model.startsWith("qwen")) {
                configuration.tts.model = "speech-2.8-turbo";
            }
        } else {
            configuration.tts.endpoint = AiConfigurationStore.QWEN_TTS_ENDPOINT;
            configuration.tts.model = "qwen3-tts-flash";
            if (configuration.tts.voiceId == null || configuration.tts.voiceId.isBlank()) {
                configuration.tts.voiceId = "Cherry";
            }
        }
        this.rebuildWidgets();
    }

    private void cyclePersona() {
        saveFields();
        personaIndex = (personaIndex + 1) % PERSONAS.length;
        this.rebuildWidgets();
    }

    private AiConfigurationStore.Persona persona() {
        return configuration.personas.get(PERSONAS[personaIndex]);
    }

    private String personaLabel() {
        return persona().name;
    }

    private void saveFields() {
        // 每个分区重建界面后，只有当前分区的控件存在；不能用某一个控件作为总开关。
        if (textKey != null) {
            configuration.text.apiKey = textKey.getValue();
        }
        if (textModel != null) {
            configuration.text.model = textModel.getValue();
        }
        if (textEndpoint != null) {
            configuration.text.endpoint = textEndpoint.getValue();
            if (textProvider == 4) newApiEndpoint = textEndpoint.getValue();
            if (textProvider == 6) customTextEndpoint = textEndpoint.getValue();
        }
        if (textProviderName != null) {
            configuration.text.providerName = textProviderName.getValue();
        }
        if (ttsKey != null) {
            configuration.tts.apiKey = ttsKey.getValue();
        }
        if (ttsVoice != null) {
            configuration.tts.voiceId = ttsVoice.getValue();
        }
        if (ttsModel != null) {
            configuration.tts.model = ttsModel.getValue();
        }
        if (personaText != null) {
            persona().prompt = personaText.getValue();
        }
    }

    private void save() {
        saveFields();
        AiConfigurationStore.save();
        status = Component.translatable("gui.enchantment_expansion.ai.saved");
    }

    private void testText() {
        saveFields();
        status = Component.translatable("gui.enchantment_expansion.ai.test_pending");
        AiServiceClient.testText(configuration.text).thenAccept(result ->
                this.minecraft.execute(() -> this.status = Component.literal(result)));
    }

    private void closeScreen() {
        saveFields();
        AiConfigurationStore.save();
        this.minecraft.setScreen(parent);
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) return i;
        }
        return 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        int footerY = Math.max(44, this.height - 26);
        int statusWidth = Math.min(300, this.width - 16);
        this.statusBoxX = (this.width - statusWidth) / 2;
        this.statusBoxWidth = statusWidth;
        List<FormattedCharSequence> statusLines = this.font.split(this.status, statusWidth);
        int visibleLines = Math.min(statusLines.size(), STATUS_MAX_LINES);
        this.statusTruncated = statusLines.size() > STATUS_MAX_LINES;
        this.statusBoxHeight = visibleLines * STATUS_LINE_HEIGHT;
        this.statusBoxY = Math.max(34, footerY - 6 - this.statusBoxHeight);

        int statusColor = this.status.getString().startsWith("测试失败") ? 0xFF8080 : 0xA0FFA0;
        for (int i = 0; i < visibleLines; i++) {
            graphics.drawString(this.font, statusLines.get(i), this.statusBoxX,
                    this.statusBoxY + i * STATUS_LINE_HEIGHT, statusColor);
        }
        if (this.statusTruncated && visibleLines > 0) {
            String more = "…";
            graphics.drawString(this.font, more, this.statusBoxX + statusWidth - this.font.width(more),
                    this.statusBoxY + (visibleLines - 1) * STATUS_LINE_HEIGHT, statusColor);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.statusTruncated && isMouseOverStatus(mouseX, mouseY)) {
            graphics.renderTooltip(this.font,
                    this.font.split(this.status, Math.max(180, this.width - 80)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && this.statusTruncated && isMouseOverStatus(mouseX, mouseY)) {
            // 屏幕内只放摘要；完整报错交给剪贴板，方便直接发给服务商或日志排查。
            this.minecraft.keyboardHandler.setClipboard(this.status.getString());
            this.status = Component.translatable("gui.enchantment_expansion.ai.copied");
            this.statusTruncated = false;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverStatus(double mouseX, double mouseY) {
        return this.statusBoxHeight > 0
                && mouseX >= this.statusBoxX && mouseX < this.statusBoxX + this.statusBoxWidth
                && mouseY >= this.statusBoxY && mouseY < this.statusBoxY + this.statusBoxHeight;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
