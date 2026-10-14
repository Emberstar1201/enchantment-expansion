package com.github.emberstar1201.enchantmentex.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 客户端专用 AI 配置，不通过网络同步。 */
public final class AiConfigurationStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "enchantment_expansion_ai.json";
    public static final String QWEN_TTS_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private static AiConfiguration configuration;

    private AiConfigurationStore() {
    }

    public static synchronized AiConfiguration get() {
        if (configuration == null) {
            configuration = load();
        }
        return configuration;
    }

    public static synchronized void save() {
        if (configuration == null) {
            return;
        }
        Path path = getPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(configuration), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static AiConfiguration load() {
        Path path = getPath();
        if (!Files.isRegularFile(path)) {
            return defaults();
        }
        try {
            AiConfiguration loaded = GSON.fromJson(
                    Files.readString(path, StandardCharsets.UTF_8), AiConfiguration.class);
            return loaded == null ? defaults() : loaded.withDefaults();
        } catch (IOException | RuntimeException ignored) {
            return defaults();
        }
    }

    private static Path getPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    private static AiConfiguration defaults() {
        AiConfiguration value = new AiConfiguration();
        value.text.provider = "OpenAI";
        value.text.model = "gpt-4o-mini";
        value.text.endpoint = "https://api.openai.com/v1/chat/completions";
        value.tts.enabled = false;
        value.tts.provider = "阿里云 Qwen TTS";
        value.tts.endpoint = QWEN_TTS_ENDPOINT;
        value.tts.voiceId = "Cherry";
        value.tts.model = "qwen3-tts-flash";
        value.personas.put("zombie_girl", Persona.zombie());
        value.personas.put("drowned_girl", Persona.drowned());
        value.personas.put("survivor_girl", Persona.survivor());
        return value;
    }

    public static final class AiConfiguration {
        public int version = 1;
        public TextService text = new TextService();
        public TtsService tts = new TtsService();
        public Map<String, Persona> personas = new LinkedHashMap<>();

        private AiConfiguration withDefaults() {
            if (text == null) text = new TextService();
            if (tts == null) tts = new TtsService();
            if (tts.provider == null || tts.provider.isBlank()) {
                tts.provider = "阿里云 Qwen TTS";
            }
            if ("阿里云 Qwen TTS".equals(tts.provider)) {
                if (tts.endpoint == null || tts.endpoint.isBlank()) tts.endpoint = QWEN_TTS_ENDPOINT;
                if (tts.model == null || tts.model.isBlank() || "speech-2.8-turbo".equals(tts.model)) {
                    tts.model = "qwen3-tts-flash";
                }
                if (tts.voiceId == null || tts.voiceId.isBlank()) tts.voiceId = "Cherry";
            } else if (tts.model == null || tts.model.isBlank()) {
                tts.model = "speech-2.8-turbo";
            }
            if (personas == null) personas = new LinkedHashMap<>();
            personas.putIfAbsent("zombie_girl", Persona.zombie());
            personas.putIfAbsent("drowned_girl", Persona.drowned());
            personas.putIfAbsent("survivor_girl", Persona.survivor());
            return this;
        }
    }

    public static final class TextService {
        public String provider = "OpenAI";
        public String endpoint = "https://api.openai.com/v1/chat/completions";
        public String apiKey = "";
        public String model = "";
        public String providerName = "";
    }

    public static final class TtsService {
        public boolean enabled;
        public String provider = "阿里云 Qwen TTS";
        public String endpoint = QWEN_TTS_ENDPOINT;
        public String apiKey = "";
        public String voiceId = "Cherry";
        public String model = "qwen3-tts-flash";
        public String providerName = "";
    }

    public static final class Persona {
        private static final List<String> PROTECTED_TABOO_TOPICS = List.of(
                "露骨性内容", "自杀/自残方法", "真实暴力教唆", "现实政治/宗教争议", "仇恨言论");

        public String name = "";
        public String personality = "";
        public String speech_style = "";
        public String background = "";
        public List<String> default_lines = new ArrayList<>();
        public List<String> facts = new ArrayList<>();
        // 纯文本人设由玩家自由编写；旧字段保留用于兼容已有配置文件。
        public String prompt = "";

        public List<String> tabooTopics() {
            return PROTECTED_TABOO_TOPICS;
        }

        private static Persona base(String name, String personality, String style, String background,
                                    String... lines) {
            Persona value = new Persona();
            value.name = name;
            value.personality = personality;
            value.speech_style = style;
            value.background = background;
            value.default_lines.addAll(Arrays.asList(lines));
            value.prompt = value.toPromptText();
            return value;
        }

        public Persona copy() {
            Persona value = new Persona();
            value.name = this.name;
            value.personality = this.personality;
            value.speech_style = this.speech_style;
            value.background = this.background;
            value.default_lines = this.default_lines == null
                    ? new ArrayList<>() : new ArrayList<>(this.default_lines);
            value.facts = this.facts == null ? new ArrayList<>() : new ArrayList<>(this.facts);
            value.prompt = this.prompt;
            return value;
        }

        public String toPromptText() {
            String text = prompt != null && !prompt.isBlank()
                    ? prompt
                    : "名字：" + name + "\n"
                    + "性格：" + personality + "\n"
                    + "说话方式：" + speech_style + "\n"
                    + "背景：" + background + "\n"
                    + "常用语：" + String.join("；", default_lines == null ? List.of() : default_lines);
            return facts == null || facts.isEmpty()
                    ? text
                    : text + "\n" + String.join("\n", facts);
        }

        private static Persona zombie() {
            return base("丧尸娘", "保留人类记忆、警惕而温柔，害怕失控", "停顿较多，语气低落但会认真回应",
                    "曾在村庄灾难中感染，仍记得家人和梦里的星空，如今与同伴在营地生活。",
                    "谢谢你……没把我当怪物。", "你说，我这样算活着，还是算死了？");
        }

        private static Persona drowned() {
            return base("溺尸娘", "安静、敏感，熟悉河流和海洋，偶尔怀念家人", "声音平静，常用水、雾和月光作比喻",
                    "曾在暴雨或河流事故中沉入水底，醒来后成为能在深水中生活的少女。",
                    "水底很安静……你要小心脚下。", "月光落进水里时，像有人在打招呼。");
        }

        private static Persona survivor() {
            return base("幸存者少女", "谨慎、善良、重视同伴，愿意相信证据和行动", "说话清楚直接，紧张时会先确认安全",
                    "曾被亡灵少女救助并带回营地，后来选择与她们同行，记录她们保留理智和善意的故事。",
                    "先确认周围安全，再继续走。", "她们不是传闻里那样，我亲眼见过她们救人。");
        }
    }
}
