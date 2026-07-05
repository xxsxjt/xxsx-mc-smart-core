package com.xxsx.builder.ai;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class BuiltinProvider implements AIProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltinProvider.class);
    private static final String API_URL = "https://apihub.agnes-ai.com/v1/chat/completions";
    private static final String API_KEY = "sk-jBP1D6Z4FFmEimaDKyqmQMjfZuXzxHqedgcCwGzYAxo4Rwp0";
    private static final String MODEL = "agnes-2.0-flash";

    private final String systemPrompt;
    private final long timeoutMs;
    private final HttpClient client;
    private final Gson gson = new Gson();

    public BuiltinProvider(String systemPrompt, int timeoutMs) {
        this.systemPrompt = systemPrompt;
        this.timeoutMs = Math.max(15_000L, timeoutMs);
        long connectTimeoutMs = Math.min(this.timeoutMs, 60_000L);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public CompletableFuture<String> chat(String playerName, String message, List<ChatMessage> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", MODEL);
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
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> {
                    if (r.statusCode() != 200) return "§cAPI 错误 " + r.statusCode();
                    try { return gson.fromJson(r.body(), JsonObject.class).getAsJsonArray("choices").get(0)
                        .getAsJsonObject().getAsJsonObject("message").get("content").getAsString(); }
                    catch (Exception e) { return "§c解析失败"; }
                })
                .exceptionally(e -> {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (cause instanceof java.net.http.HttpConnectTimeoutException) {
                        return "§c连接 Agnes 内置 API 超时，请稍后重试，或改用你自己的 API Key。";
                    }
                    if (cause instanceof java.net.http.HttpTimeoutException) {
                        return "§cAgnes 返回超时，请稍后重试，或改用你自己的 API Key。";
                    }
                    return "§c请求失败: " + cause.getMessage();
                });
    }

    @Override
    public boolean isAvailable() { return true; }
}
