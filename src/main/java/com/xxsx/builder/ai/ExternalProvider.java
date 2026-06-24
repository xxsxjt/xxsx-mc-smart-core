package com.xxsx.builder.ai;

import com.google.gson.*;
import com.xxsx.builder.XxsxBuilder;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ExternalProvider implements AIProvider {
    private final String apiUrl, apiKey, model;
    private final Gson gson = new Gson();
    private final int timeoutMs;

    private static final String SYSTEM_PROMPT = """
            你是 xxsx 的智建核心，运行在 Minecraft 1.20.1 Forge 中。
            用 [CMD]标签 执行指令，[QUERY] 查状态。[KNOWLEDGE] 查知识库。
            PMX建筑：优先用 [CMD]/ai build "路径"[/CMD] 触发系统功能。特殊需求可自行处理。
            """;

    public ExternalProvider(String apiUrl, String apiKey, String model, int timeoutMs) {
        this.apiUrl = apiUrl.endsWith("/") ? apiUrl + "chat/completions"
            : (apiUrl.contains("/chat/completions") ? apiUrl : apiUrl + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
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
        sys.addProperty("content", SYSTEM_PROMPT);
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
}
