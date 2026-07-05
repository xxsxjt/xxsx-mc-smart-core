package com.xxsx.builder.ai;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExternalProvider implements AIProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ExternalProvider.class);
    private final String apiUrl, apiKey, model;
    private final String systemPrompt;
    private final Gson gson = new Gson();
    private final int timeoutMs;

    public ExternalProvider(String apiUrl, String apiKey, String model, String systemPrompt, int timeoutMs) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl + "chat/completions"
            : (apiUrl.contains("/chat/completions") ? apiUrl : apiUrl + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.systemPrompt = systemPrompt;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public CompletableFuture<String> chat(String playerName, String message, List<ChatMessage> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("max_tokens", 4096);

        JsonArray msgs = new JsonArray();
        JsonObject sys = new JsonObject();
        sys.addProperty("role", "system");
        sys.addProperty("content", systemPrompt);
        msgs.add(sys);
        for (ChatMessage h : history) {
            JsonObject m = new JsonObject();
            m.addProperty("role", h.role);
            m.addProperty("content", h.content);
            msgs.add(m);
        }
        body.add("messages", msgs);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() != 200) return "§cAPI 错误 " + r.statusCode();
                    try { return gson.fromJson(r.body(), JsonObject.class).getAsJsonArray("choices").get(0)
                        .getAsJsonObject().getAsJsonObject("message").get("content").getAsString(); }
                    catch (Exception e) { return "§c解析失败"; }
                })
                .exceptionally(e -> "§c请求失败: " + e.getMessage());
    }

    @Override
    public boolean isAvailable() { return true; }

    /** 从 API 获取可用模型列表。web 类模型排在末尾，显示名中去掉 deepseek- 前缀 */
    public static List<String> fetchModels(String baseUrl, String apiKey) {
        try {
            String url = baseUrl;
            if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
            if (url.endsWith("/chat/completions")) url = url.substring(0, url.length() - "/chat/completions".length());
            if (!url.endsWith("/models")) url = url + "/models";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiKey)
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            String body = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            JsonObject json = new Gson().fromJson(body, JsonObject.class);
            JsonArray data = json.getAsJsonArray("data");
            if (data == null) return List.of();

            List<String> normal = new java.util.ArrayList<>();
            List<String> web = new java.util.ArrayList<>();
            for (JsonElement e : data) {
                String id = e.getAsJsonObject().get("id").getAsString();
                if (id == null || id.isBlank()) continue;
                if (id.toLowerCase().contains("-web")) {
                    web.add(id.replaceFirst("^(?i)deepseek-?", "").replace("-web", " (web)"));
                } else {
                    normal.add(id);
                }
            }
            normal.addAll(web);
            return normal;
        } catch (Exception e) {
            LOGGER.warn("获取模型列表失败: {}", e.getMessage());
            return List.of();
        }
    }
}
