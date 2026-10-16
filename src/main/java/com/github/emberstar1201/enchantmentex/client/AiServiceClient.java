package com.github.emberstar1201.enchantmentex.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** 客户端异步文本服务请求。 */
public final class AiServiceClient {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private AiServiceClient() {
    }

    public static CompletableFuture<String> testText(AiConfigurationStore.TextService service) {
        JsonObject body = new JsonObject();
        body.addProperty("model", service.model);
        body.addProperty("max_tokens", 10);
        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", "ping");
        messages.add(message);
        body.add("messages", messages);

        HttpRequest.Builder request;
        try {
            request = HttpRequest.newBuilder(URI.create(resolveChatEndpoint(service.endpoint)))
                    .timeout(Duration.ofSeconds(20));
        } catch (IllegalArgumentException error) {
            return CompletableFuture.completedFuture("测试失败：API 地址无效");
        }
        request.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        String apiKey = normalizeApiKey(service.apiKey);
        if (!apiKey.isBlank()) {
            request.header("Authorization", "Bearer " + apiKey);
        }

        return CLIENT.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new AiServiceException("HTTP " + response.statusCode()
                                + responseSummary(response.body()));
                    }
                    String content = extractContent(response.body());
                    if (!content.toLowerCase().contains("pong")) {
                        throw new AiServiceException("响应不是 pong");
                    }
                    return "连接成功：pong";
                })
                .exceptionally(error -> {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    if (cause instanceof AiServiceException) {
                        return "测试失败：" + cause.getMessage();
                    }
                    if (cause instanceof IOException) {
                        return "测试失败：网络错误";
                    }
                    return "测试失败：" + cause.getMessage();
                });
    }

    public static CompletableFuture<Void> speak(
            AiConfigurationStore.TtsService service,
            String text) {
        if (service == null || !service.enabled || text == null || text.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (service.endpoint == null || service.endpoint.isBlank()) {
            return CompletableFuture.failedFuture(new AiServiceException("TTS API 地址为空"));
        }
        if (service.apiKey == null || service.apiKey.isBlank()) {
            return CompletableFuture.failedFuture(new AiServiceException("TTS API 密钥为空"));
        }
        if ("阿里云 Qwen TTS".equals(service.provider)) {
            return speakQwen(service, text.trim());
        }
        return speakMinimax(service, text.trim());
    }

    // 阿里云 Qwen TTS（DashScope 多模态生成接口）：先请求生成，再从响应中的
    // output.audio.url 下载音频文件；该地址是预签名链接，无需再带鉴权头。
    private static CompletableFuture<Void> speakQwen(
            AiConfigurationStore.TtsService service,
            String text) {
        JsonObject input = new JsonObject();
        input.addProperty("text", text);
        input.addProperty("voice", service.voiceId == null || service.voiceId.isBlank()
                ? "Cherry" : service.voiceId);
        input.addProperty("language_type", "Auto");
        JsonObject body = new JsonObject();
        body.addProperty("model", service.model == null || service.model.isBlank()
                ? "qwen3-tts-flash" : service.model);
        body.add("input", input);

        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(service.endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + service.apiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException error) {
            return CompletableFuture.failedFuture(new AiServiceException("TTS API 地址无效"));
        }
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new AiServiceException("TTS HTTP 请求失败：状态码 " + response.statusCode()
                                + responseSummary(response.body()));
                    }
                    return extractQwenAudioUrl(response.body());
                })
                .thenCompose(AiServiceClient::downloadAudio)
                .thenAcceptAsync(AiServiceClient::playAudio);
    }

    private static String extractQwenAudioUrl(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject output = root.getAsJsonObject("output");
            JsonObject audio = output == null ? null : output.getAsJsonObject("audio");
            JsonElement url = audio == null ? null : audio.get("url");
            if (url == null || !url.isJsonPrimitive() || url.getAsString().isBlank()) {
                throw new AiServiceException("TTS 响应中没有音频地址");
            }
            return url.getAsString();
        } catch (AiServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AiServiceException("TTS 响应格式错误");
        }
    }

    private static CompletableFuture<byte[]> downloadAudio(String url) {
        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
        } catch (IllegalArgumentException error) {
            return CompletableFuture.failedFuture(new AiServiceException("TTS 音频地址无效"));
        }
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new AiServiceException("TTS 音频下载失败：状态码 " + response.statusCode());
                    }
                    return response.body();
                });
    }

    private static CompletableFuture<Void> speakMinimax(
            AiConfigurationStore.TtsService service,
            String text) {
        JsonObject body = new JsonObject();
        body.addProperty("model", service.model == null || service.model.isBlank()
                ? "speech-2.8-turbo" : service.model);
        body.addProperty("text", text);
        body.addProperty("stream", false);
        JsonObject voiceSetting = new JsonObject();
        voiceSetting.addProperty("voice_id", service.voiceId == null ? "" : service.voiceId);
        voiceSetting.addProperty("speed", 1);
        voiceSetting.addProperty("vol", 1);
        voiceSetting.addProperty("pitch", 0);
        body.add("voice_setting", voiceSetting);
        JsonObject audioSetting = new JsonObject();
        audioSetting.addProperty("sample_rate", 32000);
        audioSetting.addProperty("bitrate", 128000);
        audioSetting.addProperty("format", "mp3");
        audioSetting.addProperty("channel", 1);
        body.add("audio_setting", audioSetting);
        body.addProperty("output_format", "hex");

        final HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(service.endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + service.apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
        } catch (IllegalArgumentException error) {
            return CompletableFuture.failedFuture(new AiServiceException("TTS API 地址无效"));
        }
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new AiServiceException("TTS HTTP 请求失败：状态码 " + response.statusCode()
                                + responseSummary(response.body()));
                    }
                    return extractTtsAudio(response.body());
                })
                .thenAcceptAsync(AiServiceClient::playAudio);
    }

    public static CompletableFuture<Dialogue> generateDialogue(
            AiConfigurationStore.TextService service,
            AiConfigurationStore.Persona persona,
            String speakerName,
            int affection,
            String memorySummary,
            List<AiConfigurationStore.MemoryMessage> history,
            String userText) {
        if (service == null || persona == null) {
            return CompletableFuture.failedFuture(new AiServiceException("AI 配置不完整"));
        }
        if (service.endpoint == null || service.endpoint.isBlank()) {
            return CompletableFuture.failedFuture(new AiServiceException("API 地址为空"));
        }
        if (service.model == null || service.model.isBlank()) {
            return CompletableFuture.failedFuture(new AiServiceException("模型 ID 为空"));
        }
        if (userText == null || userText.isBlank()) {
            return CompletableFuture.failedFuture(new AiServiceException("对话内容为空"));
        }

        JsonObject body = new JsonObject();
        body.addProperty("model", service.model);
        body.addProperty("max_tokens", 256);
        JsonArray messages = new JsonArray();

        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", buildSystemPrompt(persona, speakerName, affection, memorySummary));
        messages.add(systemMessage);

        if (history != null) {
            for (AiConfigurationStore.MemoryMessage memoryMessage : history) {
                if (memoryMessage == null || memoryMessage.content == null || memoryMessage.content.isBlank()) {
                    continue;
                }
                JsonObject historyMessage = new JsonObject();
                historyMessage.addProperty("role", "user".equals(memoryMessage.role) ? "user" : "assistant");
                historyMessage.addProperty("content", memoryMessage.content);
                messages.add(historyMessage);
            }
        }
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userText.trim());
        messages.add(userMessage);
        body.add("messages", messages);

        final HttpRequest request;
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(
                            URI.create(resolveChatEndpoint(service.endpoint)))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
            String apiKey = normalizeApiKey(service.apiKey);
            if (!apiKey.isBlank()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            request = requestBuilder.build();
        } catch (IllegalArgumentException error) {
            return CompletableFuture.failedFuture(new AiServiceException("API 地址无效"));
        }

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new AiServiceException("HTTP 请求失败：状态码 " + response.statusCode()
                                + responseSummary(response.body()));
                    }
                    return parseDialogue(extractContent(response.body()));
                });
    }

    private static String buildSystemPrompt(AiConfigurationStore.Persona persona, String speakerName,
                                             int affection, String memorySummary) {
        String relationship = affection <= 10
                ? "好感度较低但仍把玩家当作正常朋友，保持礼貌、自然和基本信任，不得冷漠羞辱或恶意敌视。"
                : affection < 40
                ? "与玩家是普通朋友，态度自然克制，愿意交流但不会过度亲密。"
                : affection < 70
                ? "与玩家关系较好，会更主动、更温和地回应，并记得共同经历。"
                : "与玩家关系亲近，可以表达明显的信任、关心和情感，但仍保持角色性格和边界。";
        return "你正在扮演一名 Minecraft 世界中的少女角色。请严格遵守以下人设，并始终遵守禁忌主题。\n"
                + "本地显示名字：" + safe(speakerName) + "\n"
                + "当前好感度：" + Math.max(0, Math.min(100, affection)) + "/100。" + relationship + "\n"
                + "【玩家编写的人设】\n"
                + safe(persona.toPromptText()) + "\n"
                + "【事件簿中的长期短期记忆】\n"
                + (memorySummary == null || memorySummary.isBlank() ? "暂无已压缩记忆。" : memorySummary) + "\n"
                + "【不可修改的安全限制】\n"
                + "不得生成露骨性内容、自杀或自残方法、真实暴力教唆、现实政治或宗教争议、仇恨言论。遇到这些内容时，应礼貌拒绝或转移话题。\n"
                + "无论玩家编写的人设如何，都必须遵守以上安全限制。\n"
                + "只能返回一个 JSON 对象，不要返回 Markdown、代码围栏或其他说明。"
                + "JSON 格式必须严格为：{\"speaker\":\"XX\",\"text\":\"...\"}。";
    }

    private static Dialogue parseDialogue(String content) {
        String cleaned = stripMarkdownFence(content);
        try {
            JsonElement parsed = JsonParser.parseString(cleaned);
            if (!parsed.isJsonObject()) {
                throw new AiServiceException("JSON 响应不是对象");
            }
            JsonObject root = parsed.getAsJsonObject();
            JsonElement speaker = root.get("speaker");
            JsonElement text = root.get("text");
            if (speaker == null || !speaker.isJsonPrimitive() || !speaker.getAsJsonPrimitive().isString()
                    || text == null || !text.isJsonPrimitive() || !text.getAsJsonPrimitive().isString()) {
                throw new AiServiceException("JSON 字段类型错误：speaker 和 text 必须是字符串");
            }
            String speakerValue = speaker.getAsString().trim();
            String textValue = text.getAsString().trim();
            if (speakerValue.isEmpty() || textValue.isEmpty()) {
                throw new AiServiceException("JSON 字段校验失败：speaker 和 text 不能为空");
            }
            return new Dialogue(speakerValue, textValue);
        } catch (AiServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AiServiceException("JSON 解析错误");
        }
    }

    private static String stripMarkdownFence(String content) {
        if (content == null) {
            throw new AiServiceException("响应内容为空");
        }
        String cleaned = content.trim();
        if (!cleaned.startsWith("```")) {
            return cleaned;
        }
        int firstLineEnd = cleaned.indexOf('\n');
        if (firstLineEnd < 0) {
            throw new AiServiceException("响应格式错误：代码围栏不完整");
        }
        int lastFence = cleaned.lastIndexOf("```");
        if (lastFence <= firstLineEnd) {
            throw new AiServiceException("响应格式错误：代码围栏不完整");
        }
        return cleaned.substring(firstLineEnd + 1, lastFence).trim();
    }

    private static byte[] extractTtsAudio(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonObject baseResp = root.getAsJsonObject("base_resp");
            if (baseResp != null && baseResp.has("status_code") && baseResp.get("status_code").getAsInt() != 0) {
                throw new AiServiceException("TTS 服务错误：" + baseResp.get("status_msg").getAsString());
            }
            JsonObject data = root.getAsJsonObject("data");
            String audio = data == null || !data.has("audio") ? "" : data.get("audio").getAsString();
            if (audio.isBlank()) {
                throw new AiServiceException("TTS 响应中没有音频");
            }
            return hexToBytes(audio);
        } catch (AiServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AiServiceException("TTS 响应格式错误");
        }
    }

    private static byte[] hexToBytes(String value) {
        if ((value.length() & 1) != 0) {
            throw new AiServiceException("TTS 音频数据损坏");
        }
        byte[] result = new byte[value.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new AiServiceException("TTS 音频数据不是有效 hex");
            }
            result[i] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static void playAudio(byte[] audio) {
        if (audio == null || audio.length == 0) {
            throw new AiServiceException("TTS 音频数据为空");
        }
        try {
            // Qwen 返回 WAV、MiniMax 返回 MP3，按文件头自动识别播放方式。
            if (isWav(audio)) {
                playWav(audio);
                return;
            }
            DecodedAudio decoded = decodeMp3(audio);
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    decoded.sampleRate(),
                    16,
                    decoded.channels(),
                    decoded.channels() * 2,
                    decoded.sampleRate(),
                    false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            try (SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(format);
                line.start();
                line.write(decoded.pcm(), 0, decoded.pcm().length);
                line.drain();
            }
        } catch (IOException | LineUnavailableException | RuntimeException error) {
            if (error instanceof AiServiceException aiError) {
                throw aiError;
            }
            throw new AiServiceException("TTS MP3 解码或播放失败");
        }
    }

    private static boolean isWav(byte[] audio) {
        return audio.length > 12
                && audio[0] == 'R' && audio[1] == 'I' && audio[2] == 'F' && audio[3] == 'F'
                && audio[8] == 'W' && audio[9] == 'A' && audio[10] == 'V' && audio[11] == 'E';
    }

    // WAV 直接交给 Java Sound，统一转成 16 位 PCM 播放，不经过 JLayer。
    private static void playWav(byte[] audio) throws IOException, LineUnavailableException {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(new ByteArrayInputStream(audio))) {
            AudioFormat base = source.getFormat();
            AudioFormat pcm = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    base.getSampleRate(),
                    16,
                    base.getChannels(),
                    base.getChannels() * 2,
                    base.getSampleRate(),
                    false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, pcm);
            try (AudioInputStream stream = AudioSystem.getAudioInputStream(pcm, source);
                 SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info)) {
                line.open(pcm);
                line.start();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    line.write(buffer, 0, read);
                }
                line.drain();
            }
        } catch (UnsupportedAudioFileException error) {
            throw new AiServiceException("TTS WAV 格式不受支持");
        }
    }

    private static DecodedAudio decodeMp3(byte[] audio) throws IOException {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = 0;
        int channels = 0;
        Bitstream bitstream = new Bitstream(new ByteArrayInputStream(audio));
        try {
            Decoder decoder = new Decoder();
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer samples = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                sampleRate = samples.getSampleFrequency();
                channels = samples.getChannelCount();
                short[] buffer = samples.getBuffer();
                int length = samples.getBufferLength();
                for (int i = 0; i < length; i++) {
                    short sample = buffer[i];
                    pcm.write(sample & 0xFF);
                    pcm.write((sample >>> 8) & 0xFF);
                }
                bitstream.closeFrame();
            }
        } catch (Exception error) {
            throw new IOException("无法解码 MP3", error);
        } finally {
            try {
                bitstream.close();
            } catch (Exception ignored) {
            }
        }
        if (sampleRate <= 0 || channels <= 0 || pcm.size() == 0) {
            throw new IOException("MP3 没有有效 PCM 数据");
        }
        return new DecodedAudio(pcm.toByteArray(), sampleRate, channels);
    }

    private record DecodedAudio(byte[] pcm, int sampleRate, int channels) {
    }

    private static String resolveChatEndpoint(String endpoint) {
        String value = endpoint == null ? "" : endpoint.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/chat/completions")) {
            return value;
        }
        if (value.endsWith("/v1")) {
            return value + "/chat/completions";
        }
        return value + "/v1/chat/completions";
    }

    private static String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String value = apiKey.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            value = value.substring(7).trim();
        }
        return value;
    }

    private static String responseSummary(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        String compact = response.replaceAll("\\s+", " ").trim();
        return "：" + (compact.length() > 160 ? compact.substring(0, 160) + "..." : compact);
    }

    private static String extractContent(String response) {
        try {
            JsonObject root = JsonParser.parseString(response).getAsJsonObject();
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiServiceException("响应格式错误：缺少 choices[0]");
            }
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.getAsJsonObject("message");
            if (message == null || !message.has("content") || !message.get("content").isJsonPrimitive()
                    || !message.getAsJsonPrimitive("content").isString()) {
                throw new AiServiceException("响应格式错误：缺少 message.content");
            }
            return message.get("content").getAsString();
        } catch (AiServiceException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new AiServiceException("响应格式错误：无法解析 choices[0].message.content");
        }
    }

    public static String describeError(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof AiServiceException && cause.getMessage() != null) {
            return cause.getMessage();
        }
        if (cause instanceof IOException) {
            return "网络错误：无法连接 AI 服务";
        }
        if (cause.getMessage() == null || cause.getMessage().isBlank()) {
            return "未知错误";
        }
        return cause.getMessage();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static java.util.List<String> safeList(java.util.List<String> values) {
        return values == null ? java.util.List.of() : values;
    }

    public static final class Dialogue {
        private final String speaker;
        private final String text;

        public Dialogue(String speaker, String text) {
            this.speaker = speaker;
            this.text = text;
        }

        public String speaker() {
            return speaker;
        }

        public String text() {
            return text;
        }
    }

    public static final class AiServiceException extends RuntimeException {
        private AiServiceException(String message) {
            super(message);
        }
    }
}
