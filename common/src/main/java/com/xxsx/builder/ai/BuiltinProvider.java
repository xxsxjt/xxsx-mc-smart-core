package com.xxsx.builder.ai;

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Keyless provider backed by the Kilo AI gateway's free lane.
 *
 * The gateway serves an OpenAI-compatible Chat Completions API and accepts requests with no
 * credential at all; free models are the ones carrying the {@code :free} suffix plus the
 * {@code kilo-auto/free} alias. Reasoning models answer through a separate {@code reasoning}
 * field, so the reply is assembled from {@code content} and falls back to {@code reasoning}
 * when a model returns no visible content.
 */
public class BuiltinProvider implements AIProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(BuiltinProvider.class);
    private static final String API_URL = "https://api.kilo.ai/api/gateway/chat/completions";
    private static final String CLIENT_USER_AGENT = "xxsx-mc-smart-core/1.0";
    private static final int MAX_TOKENS = 4096;

    /** Free models tried in order; the first model is the default. */
    public static final List<String> MODELS = List.of(
        "reflection/beam-free",
        "kilo-auto/free",
        "recursive/flint-v1.0:free"
    );

    private final String systemPrompt;
    private final long timeoutMs;
    private final String preferredModel;
    private final HttpClient client;
    private final Gson gson = new Gson();

    public BuiltinProvider(String systemPrompt, int timeoutMs) {
        this(systemPrompt, timeoutMs, "");
    }

    public BuiltinProvider(String systemPrompt, int timeoutMs, String preferredModel) {
        this.systemPrompt = systemPrompt;
        this.timeoutMs = Math.max(15_000L, timeoutMs);
        this.preferredModel = preferredModel == null ? "" : preferredModel.trim();
        long connectTimeoutMs = Math.min(this.timeoutMs, 60_000L);
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public CompletableFuture<String> chat(String playerName, String message, List<ChatMessage> history) {
        return attempt(0, message, history);
    }

    private CompletableFuture<String> attempt(int index, String message, List<ChatMessage> history) {
        List<String> order = orderedModels();
        if (index >= order.size()) {
            return CompletableFuture.completedFuture(
                "§c内置免费模型暂时不可用（额度或网络受限），请稍后重试，或用 /ai addmodel 配置自己的 API。");
        }
        String model = order.get(index);
        return sendOnce(model, message, history).handle((result, throwable) -> {
            if (throwable == null && result != null && result.success) {
                return CompletableFuture.completedFuture(result.text);
            }
            Throwable cause = throwable == null ? null : unwrap(throwable);
            String detail = cause != null ? safeMessage(cause) : result.message;
            if (cause == null && result != null && result.contextOverflow) {
                return CompletableFuture.<String>failedFuture(new CompletionException(
                    new RuntimeException(result.message)));
            }
            LOGGER.warn("[Free Models] {} 失败: {}", model, detail);
            return attempt(index + 1, message, history);
        }).thenCompose(next -> next);
    }

    private List<String> orderedModels() {
        if (preferredModel.isEmpty() || !MODELS.contains(preferredModel)) return MODELS;
        java.util.ArrayList<String> order = new java.util.ArrayList<>(MODELS.size());
        order.add(preferredModel);
        for (String model : MODELS) {
            if (!model.equals(preferredModel)) order.add(model);
        }
        return order;
    }

    private CompletableFuture<Attempt> sendOnce(String model, String message, List<ChatMessage> history) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", CLIENT_USER_AGENT)
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(
                    buildBody(model, message, history), StandardCharsets.UTF_8))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> classify(model, response.statusCode(), response.body()))
                .exceptionally(throwable -> Attempt.error(safeMessage(unwrap(throwable))));
    }

    private Attempt classify(String model, int status, String rawBody) {
        if (status == 200) {
            String content = extractContent(rawBody);
            if (content != null && !content.isBlank()) return Attempt.ok(content);
            return Attempt.error("响应为空");
        }
        String detail = extractError(rawBody);
        String lower = detail.toLowerCase(Locale.ROOT);
        if (status == 400 && (lower.contains("context") || lower.contains("too long"))) {
            return new Attempt(false, true, "context length exceeded: " + detail);
        }
        String label = status == 429 ? "免费额度或上游限速" : "HTTP " + status;
        LOGGER.debug("[Free Models] {} {}: {}", model, label, detail);
        return Attempt.error(label + (detail.isEmpty() ? "" : " - " + detail));
    }

    private String buildBody(String model, String message, List<ChatMessage> history) {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.addProperty("stream", false);
        body.addProperty("max_tokens", MAX_TOKENS);

        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        for (ChatMessage entry : history) {
            JsonObject item = new JsonObject();
            item.addProperty("role", entry.role);
            item.addProperty("content", entry.content);
            messages.add(item);
        }
        JsonObject current = new JsonObject();
        current.addProperty("role", "user");
        current.addProperty("content", message);
        messages.add(current);

        body.add("messages", messages);
        return gson.toJson(body);
    }

    /**
     * Pulls the reply out of a completion. Reasoning models emit the visible answer in
     * {@code content}; when a model only reports {@code reasoning}, that text is used instead
     * so the player still receives an answer.
     */
    private String extractContent(String payload) {
        if (payload == null || payload.isBlank()) return null;
        try {
            JsonObject root = gson.fromJson(payload, JsonObject.class);
            if (root == null) return null;
            JsonArray choices = root.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject choice = choices.get(0).getAsJsonObject();
            JsonObject message = choice.has("message") ? choice.getAsJsonObject("message") : null;
            if (message != null) {
                String content = stringField(message, "content");
                if (content != null && !content.isBlank()) return content;
                String reasoning = stringField(message, "reasoning");
                if (reasoning != null && !reasoning.isBlank()) return reasoning;
            }
            return stringField(choice, "text");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String stringField(JsonObject object, String name) {
        if (!object.has(name)) return null;
        JsonElement value = object.get(name);
        return value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private String extractError(String body) {
        if (body == null || body.isBlank()) return "";
        try {
            JsonObject json = gson.fromJson(body, JsonObject.class);
            if (json != null && json.has("error")) {
                JsonElement error = json.get("error");
                if (error.isJsonObject()) {
                    JsonObject object = error.getAsJsonObject();
                    if (object.has("message") && !object.get("message").isJsonNull()) {
                        return object.get("message").getAsString();
                    }
                }
                if (error.isJsonPrimitive()) return error.getAsString();
            }
        } catch (Exception ignored) {
        }
        return body.length() > 200 ? body.substring(0, 200) : body;
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof java.util.concurrent.ExecutionException)
            && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    @Override
    public boolean isAvailable() { return true; }

    private static final class Attempt {
        final boolean success;
        final String text;
        final String message;
        final boolean contextOverflow;

        private Attempt(boolean success, boolean contextOverflow, String value) {
            this.success = success;
            this.contextOverflow = contextOverflow;
            this.text = success ? value : "";
            this.message = success ? "" : value;
        }

        static Attempt ok(String text) { return new Attempt(true, false, text); }

        static Attempt error(String message) { return new Attempt(false, false, message); }
    }
}
