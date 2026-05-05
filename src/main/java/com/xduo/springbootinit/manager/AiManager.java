package com.xduo.springbootinit.manager;

import cn.hutool.json.JSONUtil;
import com.xduo.springbootinit.common.ErrorCode;
import com.xduo.springbootinit.exception.BusinessException;
import com.xduo.springbootinit.model.entity.User;
import com.xduo.springbootinit.service.UserService;
import com.xduo.springbootinit.utils.NetUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 服务管理
 */
@Component
@Slf4j
public class AiManager {

    @Value("${ai.api-key:empty}")
    private String legacyApiKey;

    @Value("${ai.chat.api-key:}")
    private String chatApiKey;

    @Value("${ai.chat.host:${ai.host:https://api.deepseek.com/v1}}")
    private String chatHost;

    @Value("${ai.chat.model:${ai.model:deepseek-chat}}")
    private String chatModel;

    @Value("${ai.chat.timeout-seconds:300}")
    private long chatTimeoutSeconds;

    @Value("${ai.speech.provider:openai}")
    private String speechProvider;

    @Value("${ai.speech.api-key:}")
    private String speechApiKey;

    @Value("${ai.speech.host:${ai.host:https://api.openai.com/v1}}")
    private String speechHost;

    @Value("${ai.speech.model:${ai.speech-model:whisper-1}}")
    private String speechModel;

    @Value("${ai.speech.endpoint:https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash}")
    private String speechEndpoint;

    @Value("${ai.speech.resource-id:volc.bigasr.auc_turbo}")
    private String speechResourceId;

    @Value("${ai.speech.model-name:bigmodel}")
    private String speechModelName;

    @Value("${ai.speech.user-id:intelliface}")
    private String speechUserId;

    @Value("${ai.speech.timeout-seconds:120}")
    private long speechTimeoutSeconds;

    @Value("${ai.tts.provider:doubao}")
    private String ttsProvider;

    @Value("${ai.tts.app-id:}")
    private String ttsAppId;

    @Value("${ai.tts.token:}")
    private String ttsToken;

    @Value("${ai.tts.api-key:}")
    private String ttsApiKey;

    @Value("${ai.tts.cluster:volcano_tts}")
    private String ttsCluster;

    @Value("${ai.tts.endpoint:https://openspeech.bytedance.com/api/v3/tts/submit}")
    private String ttsEndpoint;

    @Value("${ai.tts.query-endpoint:https://openspeech.bytedance.com/api/v3/tts/query}")
    private String ttsQueryEndpoint;

    @Value("${ai.tts.resource-id:seed-tts-2.0}")
    private String ttsResourceId;

    @Value("${ai.tts.voice-type:zh_female_vv_uranus_bigtts}")
    private String ttsVoiceType;

    @Value("${ai.tts.encoding:mp3}")
    private String ttsEncoding;

    @Value("${ai.tts.rate:24000}")
    private int ttsRate;

    @Value("${ai.tts.speed-ratio:1.0}")
    private double ttsSpeedRatio;

    @Value("${ai.tts.volume-ratio:1.0}")
    private double ttsVolumeRatio;

    @Value("${ai.tts.pitch-ratio:1.0}")
    private double ttsPitchRatio;

    @Value("${ai.tts.model:}")
    private String ttsModel;

    @Value("${ai.tts.emotion:}")
    private String ttsEmotion;

    @Value("${ai.tts.language:}")
    private String ttsLanguage;

    @Value("${ai.tts.user-id:intelliface}")
    private String ttsUserId;

    @Value("${ai.tts.timeout-seconds:60}")
    private long ttsTimeoutSeconds;

    @Value("${ai.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${ai.rate-limit.chat.max-per-minute:10}")
    private int chatMaxPerMinute;

    @Value("${ai.rate-limit.chat.max-per-day:100}")
    private int chatMaxPerDay;

    @Value("${ai.rate-limit.audio.max-per-minute:3}")
    private int audioMaxPerMinute;

    @Value("${ai.rate-limit.audio.max-per-day:30}")
    private int audioMaxPerDay;

    @Value("${ai.rate-limit.tts.max-per-minute:12}")
    private int ttsMaxPerMinute;

    @Value("${ai.rate-limit.tts.max-per-day:200}")
    private int ttsMaxPerDay;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    private static final ZoneId RATE_LIMIT_ZONE_ID = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String LIMIT_LUA = "local current = redis.call('incr', KEYS[1])\n" +
            "if current == 1 then\n" +
            "    redis.call('expire', KEYS[1], ARGV[1])\n" +
            "end\n" +
            "return current;";
    private static final RedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>(LIMIT_LUA, Long.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(60))
            .build();

    /**
     * 发送 AI 请求
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @return AI 回复
     */
    public String doChat(String systemPrompt, String userPrompt) {
        return doChat(systemPrompt, userPrompt, (String) null);
    }

    /**
     * 发送 AI 请求，并按指定用户维度限流。
     */
    public String doChat(String systemPrompt, String userPrompt, Long userId) {
        return doChat(systemPrompt, userPrompt, buildUserRateLimitActor(userId));
    }

    private String doChat(String systemPrompt, String userPrompt, String rateLimitActor) {
        String resolvedChatApiKey = resolveChatApiKey();
        if (!hasConfiguredApiKey(resolvedChatApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先配置 AI API Key");
        }
        checkAiRateLimit("chat", chatMaxPerMinute, 60, chatMaxPerDay, secondsUntilTomorrow(), rateLimitActor);

        // 构造请求参数
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("model", chatModel);
        
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        requestMap.put("messages", messages);
        requestMap.put("temperature", 0.7);

        String json = JSONUtil.toJsonStr(requestMap);
        try {
            String responseBody = sendJsonRequest(chatHost, resolvedChatApiKey, "/chat/completions", json);
            return extractAssistantContent(responseBody);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("AI 通信异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 通信失败");
        }
    }

    /**
     * 调用音频转写接口
     *
     * @param fileName    文件名
     * @param fileBytes   文件内容
     * @param contentType 文件类型
     * @param language    语言
     * @param prompt      转写提示词
     * @return 转写文本
     */
    public String transcribeAudio(String fileName, byte[] fileBytes, String contentType, String language, String prompt) {
        return transcribeAudio(fileName, fileBytes, contentType, language, prompt, null);
    }

    /**
     * 调用音频转写接口，并按指定用户维度限流。
     */
    public String transcribeAudio(String fileName, byte[] fileBytes, String contentType,
                                  String language, String prompt, Long userId) {
        if (fileBytes == null || fileBytes.length == 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "音频内容不能为空");
        }
        checkAiRateLimit("audio", audioMaxPerMinute, 60, audioMaxPerDay, secondsUntilTomorrow(),
                buildUserRateLimitActor(userId));
        String provider = StringUtils.defaultIfBlank(speechProvider, "openai").trim().toLowerCase();
        return switch (provider) {
            case "volcengine", "volc", "doubao" ->
                    transcribeAudioByVolcengine(fileBytes);
            case "openai", "openai-compatible" ->
                    transcribeAudioByOpenAi(fileName, fileBytes, contentType, language, prompt);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语音转写服务商: " + speechProvider);
        };
    }

    /**
     * 调用语音合成接口，并按指定用户维度限流。
     */
    public byte[] synthesizeSpeech(String text, Long userId) {
        String normalizedText = normalizeSpeechText(text);
        if (StringUtils.isBlank(normalizedText)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "播报内容不能为空");
        }
        int textBytes = normalizedText.getBytes(StandardCharsets.UTF_8).length;
        if (textBytes > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "单次语音播报文本过长，请控制在 1024 字节以内");
        }
        checkAiRateLimit("tts", ttsMaxPerMinute, 60, ttsMaxPerDay, secondsUntilTomorrow(),
                buildUserRateLimitActor(userId));
        String provider = StringUtils.defaultIfBlank(ttsProvider, "doubao").trim().toLowerCase();
        return switch (provider) {
            case "volcengine", "volc", "doubao" -> synthesizeSpeechByDoubao(normalizedText);
            default -> throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的语音播报服务商: " + ttsProvider);
        };
    }

    public String getTtsContentType() {
        return switch (StringUtils.defaultIfBlank(ttsEncoding, "mp3").trim().toLowerCase()) {
            case "wav" -> "audio/wav";
            case "ogg", "ogg_opus" -> "audio/ogg";
            case "pcm" -> "audio/pcm";
            default -> "audio/mpeg";
        };
    }

    private String transcribeAudioByOpenAi(String fileName, byte[] fileBytes, String contentType,
                                           String language, String prompt) {
        String resolvedSpeechApiKey = resolveOpenAiSpeechApiKey();
        if (!hasConfiguredApiKey(resolvedSpeechApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先配置 AI 语音转写 API Key");
        }
        String safeFileName = StringUtils.defaultIfBlank(fileName, "mock-interview-audio.webm");
        String safeContentType = StringUtils.defaultIfBlank(contentType, "application/octet-stream");
        String boundary = "----CodexBoundary" + UUID.randomUUID().toString().replace("-", "");

        try {
            byte[] requestBody = buildMultipartBody(boundary, safeFileName, fileBytes, safeContentType, language, prompt);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(buildUri(speechHost, "/audio/transcriptions"))
                    .timeout(Duration.ofSeconds(speechTimeoutSeconds))
                    .header("Authorization", "Bearer " + resolvedSpeechApiKey)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.error("AI 音频转写失败: {}", response.body());
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 音频转写失败");
            }
            String responseBody = response.body() == null ? "" : response.body();
            return extractTranscriptionText(responseBody);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("AI 音频转写通信异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 音频转写通信失败");
        }
    }

    private String transcribeAudioByVolcengine(byte[] fileBytes) {
        if (!hasConfiguredApiKey(speechApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先配置豆包语音 X-Api-Key");
        }
        String requestId = UUID.randomUUID().toString();
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", StringUtils.defaultIfBlank(speechUserId, "intelliface"));
        Map<String, Object> audioMap = new HashMap<>();
        audioMap.put("data", Base64.getEncoder().encodeToString(fileBytes));
        Map<String, Object> asrRequestMap = new HashMap<>();
        asrRequestMap.put("model_name", StringUtils.defaultIfBlank(speechModelName, "bigmodel"));
        requestMap.put("user", userMap);
        requestMap.put("audio", audioMap);
        requestMap.put("request", asrRequestMap);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(StringUtils.defaultIfBlank(speechEndpoint,
                            "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash")))
                    .timeout(Duration.ofSeconds(speechTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", speechApiKey)
                    .header("X-Api-Resource-Id", StringUtils.defaultIfBlank(speechResourceId, "volc.bigasr.auc_turbo"))
                    .header("X-Api-Request-Id", requestId)
                    .header("X-Api-Sequence", "-1")
                    .POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(requestMap), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            validateVolcengineResponse(response);
            String responseBody = response.body() == null ? "" : response.body();
            return extractTranscriptionText(responseBody);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("豆包语音转写通信异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音转写通信失败");
        }
    }

    private byte[] synthesizeSpeechByDoubao(String text) {
        String resolvedTtsApiKey = resolveTtsApiKey();
        if (!hasConfiguredApiKey(resolvedTtsApiKey)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请先配置豆包语音播报的 API Key");
        }
        String requestId = UUID.randomUUID().toString();
        String resourceId = StringUtils.defaultIfBlank(ttsResourceId, "seed-tts-2.0").trim();
        String speaker = resolveDoubaoTtsSpeaker(resourceId);
        Map<String, Object> requestMap = new HashMap<>();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("uid", StringUtils.defaultIfBlank(ttsUserId, "intelliface"));
        requestMap.put("user", userMap);
        requestMap.put("unique_id", requestId);
        requestMap.put("namespace", "BidirectionalTTS");

        Map<String, Object> reqParamsMap = new HashMap<>();
        reqParamsMap.put("text", text);
        reqParamsMap.put("speaker", speaker);
        if (StringUtils.isNotBlank(ttsModel)) {
            reqParamsMap.put("model", ttsModel.trim());
        }

        Map<String, Object> audioParamsMap = new HashMap<>();
        audioParamsMap.put("format", StringUtils.defaultIfBlank(ttsEncoding, "mp3"));
        audioParamsMap.put("sample_rate", ttsRate > 0 ? ttsRate : 24000);
        if (StringUtils.isNotBlank(ttsEmotion)) {
            audioParamsMap.put("emotion", ttsEmotion.trim());
        }
        int speechRate = toDoubaoRate(ttsSpeedRatio);
        if (speechRate != 0) {
            audioParamsMap.put("speech_rate", speechRate);
        }
        int loudnessRate = toDoubaoRate(ttsVolumeRatio);
        if (loudnessRate != 0) {
            audioParamsMap.put("loudness_rate", loudnessRate);
        }
        reqParamsMap.put("audio_params", audioParamsMap);

        Map<String, Object> additionsMap = new HashMap<>();
        if (StringUtils.isNotBlank(ttsLanguage)) {
            additionsMap.put("explicit_language", ttsLanguage.trim());
        }
        Integer pitch = toDoubaoPitch(ttsPitchRatio);
        if (pitch != null) {
            Map<String, Object> postProcessMap = new HashMap<>();
            postProcessMap.put("pitch", pitch);
            additionsMap.put("post_process", postProcessMap);
        }
        if (!additionsMap.isEmpty()) {
            reqParamsMap.put("additions", JSONUtil.toJsonStr(additionsMap));
        }
        requestMap.put("req_params", reqParamsMap);

        try {
            submitDoubaoTtsTask(requestMap, resourceId, requestId, resolvedTtsApiKey);
            String audioUrl = queryDoubaoTtsAudioUrl(requestId, resourceId, resolvedTtsApiKey);
            return downloadDoubaoTtsAudio(audioUrl);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("豆包语音播报通信异常", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音播报通信失败");
        }
    }

    private String sendJsonRequest(String baseUrl, String apiKey, String path, String json)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildUri(baseUrl, path))
                .timeout(Duration.ofSeconds(chatTimeoutSeconds > 0 ? chatTimeoutSeconds : 300))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("AI 请求失败: {}", response.body());
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 服务调用异常");
        }
        return response.body() == null ? "" : response.body();
    }

    private void submitDoubaoTtsTask(Map<String, Object> requestMap, String resourceId, String requestId,
                                     String apiKey)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(StringUtils.defaultIfBlank(ttsEndpoint, "https://openspeech.bytedance.com/api/v3/tts/submit")))
                .timeout(Duration.ofSeconds(ttsTimeoutSeconds > 0 ? ttsTimeoutSeconds : 60))
                .header("Content-Type", "application/json")
                .header("X-Api-Key", apiKey)
                .header("X-Api-Resource-Id", resourceId)
                .header("X-Api-Request-Id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(requestMap), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        validateDoubaoTtsTaskResponse(response,
                String.format("豆包语音播报任务提交失败(resourceId=%s)", resourceId));
        Map<?, ?> responseMap = JSONUtil.toBean(StringUtils.defaultString(response.body()), Map.class);
        Integer code = parseInteger(responseMap.get("code"));
        if (code == null || code != 20000000) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    StringUtils.defaultIfBlank(getStringValue(responseMap.get("message")),
                            String.format("豆包语音播报任务提交失败(resourceId=%s)", resourceId)));
        }
    }

    private String queryDoubaoTtsAudioUrl(String taskId, String resourceId, String apiKey)
            throws IOException, InterruptedException {
        long timeoutMillis = Math.max(10_000L, (ttsTimeoutSeconds > 0 ? ttsTimeoutSeconds : 60) * 1000L);
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> queryBody = new HashMap<>();
            queryBody.put("task_id", taskId);
            String requestId = UUID.randomUUID().toString();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(StringUtils.defaultIfBlank(ttsQueryEndpoint, "https://openspeech.bytedance.com/api/v3/tts/query")))
                    .timeout(Duration.ofSeconds(ttsTimeoutSeconds > 0 ? ttsTimeoutSeconds : 60))
                    .header("Content-Type", "application/json")
                    .header("X-Api-Key", apiKey)
                    .header("X-Api-Resource-Id", resourceId)
                    .header("X-Api-Request-Id", requestId)
                    .POST(HttpRequest.BodyPublishers.ofString(JSONUtil.toJsonStr(queryBody), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            validateDoubaoTtsTaskResponse(response,
                    String.format("豆包语音播报任务查询失败(resourceId=%s)", resourceId));
            Map<?, ?> responseMap = JSONUtil.toBean(StringUtils.defaultString(response.body()), Map.class);
            Integer code = parseInteger(responseMap.get("code"));
            if (code == null || code != 20000000) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        StringUtils.defaultIfBlank(getStringValue(responseMap.get("message")),
                                String.format("豆包语音播报任务查询失败(resourceId=%s)", resourceId)));
            }
            Map<?, ?> dataMap = responseMap.get("data") instanceof Map<?, ?> data ? data : Collections.emptyMap();
            Integer taskStatus = parseInteger(dataMap.get("task_status"));
            if (taskStatus != null && taskStatus == 2) {
                String audioUrl = getStringValue(dataMap.get("audio_url"));
                if (StringUtils.isBlank(audioUrl)) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音播报任务已完成，但未返回音频地址");
                }
                return audioUrl;
            }
            if (taskStatus != null && taskStatus == 3) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                        StringUtils.defaultIfBlank(getStringValue(responseMap.get("message")), "豆包语音播报任务处理失败"));
            }
            Thread.sleep(350L);
        }
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音播报超时，请稍后重试");
    }

    private byte[] downloadDoubaoTtsAudio(String audioUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(audioUrl))
                .timeout(Duration.ofSeconds(ttsTimeoutSeconds > 0 ? ttsTimeoutSeconds : 60))
                .GET()
                .build();
        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null || response.body().length == 0) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音音频下载失败");
        }
        return response.body();
    }

    private void validateDoubaoTtsTaskResponse(HttpResponse<String> response, String fallbackMessage) {
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return;
        }
        log.error("{}: {}", fallbackMessage, response.body());
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, fallbackMessage);
    }

    private URI buildUri(String baseUrl, String path) {
        String safeBaseUrl = StringUtils.removeEnd(StringUtils.defaultString(baseUrl), "/");
        String safePath = StringUtils.startsWith(path, "/") ? path : "/" + path;
        return URI.create(safeBaseUrl + safePath);
    }

    private byte[] buildMultipartBody(String boundary, String fileName, byte[] fileBytes, String contentType,
                                      String language, String prompt) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        writeFormField(outputStream, boundary, "model", StringUtils.defaultIfBlank(speechModel, "whisper-1"));
        if (StringUtils.isNotBlank(language)) {
            writeFormField(outputStream, boundary, "language", language);
        }
        if (StringUtils.isNotBlank(prompt)) {
            writeFormField(outputStream, boundary, "prompt", prompt);
        }
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(fileBytes);
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
        outputStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return outputStream.toByteArray();
    }

    private void writeFormField(ByteArrayOutputStream outputStream, String boundary, String name, String value)
            throws IOException {
        outputStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        outputStream.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        outputStream.write(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8));
        outputStream.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String resolveChatApiKey() {
        return StringUtils.defaultIfBlank(chatApiKey, legacyApiKey);
    }

    private String resolveOpenAiSpeechApiKey() {
        return StringUtils.defaultIfBlank(speechApiKey, legacyApiKey);
    }

    private boolean hasConfiguredApiKey(String apiKey) {
        String lowerApiKey = StringUtils.defaultString(apiKey).toLowerCase();
        return StringUtils.isNotBlank(apiKey)
                && !"empty".equalsIgnoreCase(apiKey)
                && !"sk-xxxx".equalsIgnoreCase(apiKey)
                && !lowerApiKey.startsWith("your_")
                && !lowerApiKey.contains("_api_key");
    }

    private void checkAiRateLimit(String operation, int maxPerMinute, long minuteWindowSeconds,
                                  int maxPerDay, long dayWindowSeconds) {
        checkAiRateLimit(operation, maxPerMinute, minuteWindowSeconds, maxPerDay, dayWindowSeconds, null);
    }

    private void checkAiRateLimit(String operation, int maxPerMinute, long minuteWindowSeconds,
                                  int maxPerDay, long dayWindowSeconds, String rateLimitActor) {
        if (!rateLimitEnabled) {
            return;
        }
        String actor = StringUtils.defaultIfBlank(rateLimitActor, resolveRateLimitActor());
        String today = LocalDate.now(RATE_LIMIT_ZONE_ID).format(DAY_FORMATTER);
        long minuteCount = incrementAndExpire(
                "ai_rate_limit:" + operation + ":minute:" + actor,
                minuteWindowSeconds
        );
        if (minuteCount > Math.max(1, maxPerMinute)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "AI 请求过于频繁，请稍后再试");
        }
        long dayCount = incrementAndExpire(
                "ai_rate_limit:" + operation + ":day:" + today + ":" + actor,
                dayWindowSeconds
        );
        if (dayCount > Math.max(1, maxPerDay)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "今日 AI 使用次数已达上限，请明天再试");
        }
    }

    private long incrementAndExpire(String key, long expireSeconds) {
        Long currentCount = stringRedisTemplate.execute(
                LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(Math.max(1, expireSeconds))
        );
        return currentCount == null ? 0 : currentCount;
    }

    private String buildUserRateLimitActor(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }
        return "user:" + userId;
    }

    private String resolveRateLimitActor() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "system";
        }
        HttpServletRequest request = attributes.getRequest();
        try {
            User loginUser = userService.getLoginUserPermitNull(request);
            if (loginUser != null && loginUser.getId() != null) {
                return "user:" + loginUser.getId();
            }
        } catch (Exception e) {
            log.debug("获取 AI 限流用户失败，降级为 IP 维度", e);
        }
        String ip = NetUtils.getIpAddress(request);
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return "ip:" + StringUtils.defaultIfBlank(ip, "unknown");
    }

    private long secondsUntilTomorrow() {
        LocalDateTime now = LocalDateTime.now(RATE_LIMIT_ZONE_ID);
        LocalDateTime tomorrowStart = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIN);
        return Math.max(60, Duration.between(now, tomorrowStart).getSeconds());
    }

    private String extractAssistantContent(String responseBody) {
        try {
            Map<?, ?> responseMap = JSONUtil.toBean(responseBody, Map.class);
            Object choicesObj = responseMap.get("choices");
            if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回结果为空");
            }
            Object firstChoice = choices.get(0);
            if (!(firstChoice instanceof Map<?, ?> firstChoiceMap)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回结果格式错误");
            }
            Object messageObj = firstChoiceMap.get("message");
            if (!(messageObj instanceof Map<?, ?> messageMap)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回结果格式错误");
            }
            Object contentObj = messageMap.get("content");
            String content = contentObj == null ? null : String.valueOf(contentObj).trim();
            if (StringUtils.isBlank(content)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回内容为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 AI 响应失败，responseBody={}", responseBody, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 返回结果格式错误");
        }
    }

    private String normalizeSpeechText(String text) {
        String normalized = StringUtils.defaultString(text)
                .replace("\r", "\n")
                .replaceAll("\\n{2,}", "\n")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .trim();
        return normalized;
    }

    private double clampRatio(double ratio) {
        if (ratio < 0.2D) {
            return 0.2D;
        }
        if (ratio > 3.0D) {
            return 3.0D;
        }
        return ratio;
    }

    private int toDoubaoRate(double ratio) {
        double safeRatio = clampRatio(ratio);
        int mapped = (int) Math.round((safeRatio - 1.0D) * 100.0D);
        return Math.max(-50, Math.min(100, mapped));
    }

    private Integer toDoubaoPitch(double ratio) {
        double safeRatio = clampRatio(ratio);
        int mapped = (int) Math.round((safeRatio - 1.0D) * 12.0D);
        if (mapped == 0) {
            return null;
        }
        return Math.max(-12, Math.min(12, mapped));
    }

    private String resolveDoubaoTtsSpeaker(String resourceId) {
        String speaker = StringUtils.defaultIfBlank(ttsVoiceType, "zh_female_vv_uranus_bigtts").trim();
        if (StringUtils.containsIgnoreCase(resourceId, "seed-tts-2.0")
                && (!speaker.contains("_bigtts") || speaker.startsWith("BV"))) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR,
                    "豆包语音合成模型2.0 需要使用 2.0 音色，例如 zh_female_vv_uranus_bigtts");
        }
        return speaker;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String getStringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String resolveTtsApiKey() {
        if (hasConfiguredApiKey(ttsApiKey)) {
            return ttsApiKey;
        }
        if (hasConfiguredApiKey(ttsToken)) {
            return ttsToken;
        }
        return speechApiKey;
    }

    private String extractTranscriptionText(String responseBody) {
        try {
            Map<?, ?> responseMap = JSONUtil.toBean(responseBody, Map.class);
            String text = extractTextFromObject(responseMap);
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 转写内容为空");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("解析 AI 转写结果失败，responseBody={}", responseBody, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 转写结果格式错误");
        }
    }

    private void validateVolcengineResponse(HttpResponse<String> response) {
        String responseBody = response.body() == null ? "" : response.body();
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.error("豆包语音转写失败, statusCode={}, body={}", response.statusCode(), responseBody);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音转写失败");
        }
        String apiStatusCode = response.headers().firstValue("X-Api-Status-Code").orElse("");
        if (StringUtils.isNotBlank(apiStatusCode) && !"20000000".equals(apiStatusCode)) {
            String apiMessage = response.headers().firstValue("X-Api-Message").orElse("");
            log.error("豆包语音转写失败, apiStatusCode={}, apiMessage={}, body={}",
                    apiStatusCode, apiMessage, responseBody);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音转写失败");
        }
        try {
            Map<?, ?> responseMap = JSONUtil.toBean(responseBody, Map.class);
            Object codeObj = responseMap.get("code");
            if (codeObj != null) {
                String code = String.valueOf(codeObj);
                if (!"0".equals(code) && !"20000000".equals(code)) {
                    log.error("豆包语音转写失败, code={}, message={}, body={}",
                            code, responseMap.get("message"), responseBody);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "豆包语音转写失败");
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.debug("解析豆包语音状态失败，继续按转写内容解析, responseBody={}", responseBody, e);
        }
    }

    private String extractTextFromObject(Object value) {
        if (value instanceof Map<?, ?> map) {
            String text = valueToText(map.get("text"));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
            text = extractTextFromObject(map.get("result"));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
            text = extractTextFromObject(map.get("data"));
            if (StringUtils.isNotBlank(text)) {
                return text;
            }
            return extractTextFromObject(map.get("utterances"));
        }
        if (value instanceof List<?> list) {
            List<String> textList = new ArrayList<>();
            for (Object item : list) {
                String text = extractTextFromObject(item);
                if (StringUtils.isNotBlank(text)) {
                    textList.add(text);
                }
            }
            return textList.isEmpty() ? null : String.join("", textList);
        }
        return valueToText(value);
    }

    private String valueToText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.isBlank(text) ? null : text;
    }
}
