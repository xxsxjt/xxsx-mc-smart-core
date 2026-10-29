package com.xxsx.builder.ai;

import com.xxsx.builder.config.PlayerConfig;
import com.xxsx.builder.config.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

/** 管理所有玩家会话和 AI 供应商。纯逻辑，不依赖 MC API。 */
public class SessionManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionManager.class);
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, AIProvider> playerProviders = new ConcurrentHashMap<>();
    private AIProvider defaultProvider;
    private final ChatConfig config;
    private final String systemPrompt;

    public SessionManager(ChatConfig config, String systemPrompt) {
        this.config = config;
        this.systemPrompt = systemPrompt;
        loadPersistedSessions();
        initProvider();
    }

    /** 根据玩家配置获取 API 设置（优先用个人配置） */
    private AIProvider createProviderFor(String playerName) {
        var pc = PlayerConfig.get(playerName);
        String url, key, model;
        if (pc.hasCustomApi()) {
            url = pc.apiUrl; key = pc.apiKey; model = pc.apiModel;
            LOGGER.info("[智能核心] {} 使用个人API: {}", playerName, url);
        } else {
            url = config.providerUrl(); key = config.providerKey(); model = config.getEffectiveModel();
        }
        if (!url.isEmpty() && !key.isEmpty()) {
            return new ExternalProvider(url, key, model, systemPrompt, config.apiTimeoutMs());
        }
        return new BuiltinProvider(systemPrompt, config.apiTimeoutMs(), model);
    }

    /** 初始化或重建默认供应商 */
    public void initProvider() {
        defaultProvider = createProviderFor("__default__");
    }

    /** 切换 API/模型后调用：清除玩家缓存，重建默认供应商 */
    public void reloadProvider() {
        if (defaultProvider != null) defaultProvider.reset();
        playerProviders.clear();
        loadPersistedSessions();
        initProvider();
    }

    /** 获取指定玩家的供应商 */
    public AIProvider getProviderFor(String playerName) {
        return playerProviders.computeIfAbsent(playerName, k -> createProviderFor(k));
    }

    /** 获取默认供应商 */
    public AIProvider getDefaultProvider() { return defaultProvider; }

    /** 清除所有玩家供应商缓存（切换 API 后需要重建） */
    public void clearPlayerProviders() { playerProviders.clear(); }

    /** 根据 URL 判断供应商名称 */
    public static String guessProviderName(String url) {
        if (url.isEmpty()) return "Free Models (内置)";
        if (url.contains("kilo.ai")) return "Free Models";
        if (url.contains("deepseek")) return "DeepSeek";
        if (url.contains("agnes")) return "Agnes";
        if (url.contains("stepfun")) return "StepFun";
        return "Custom";
    }

    // === Provider 定义 ===
    public static final Map<String, String[]> PROVIDERS = new LinkedHashMap<>();
    public static final Map<String, List<String>> PROVIDER_MODELS = new LinkedHashMap<>();
    static {
        PROVIDERS.put("Free Models", new String[]{"", ""});
        PROVIDER_MODELS.put("Free Models", BuiltinProvider.MODELS);
        PROVIDERS.put("Custom", new String[]{"", ""});
        PROVIDER_MODELS.put("Custom", List.of("(手动输入)"));
    }

    private void loadPersistedSessions() {
        if (!config.sessionPersistence()) return;
        Map<String, List<ChatMessage>> saved = SessionStore.load();
        for (var entry : saved.entrySet()) {
            ChatSession session = new ChatSession(entry.getKey(), config.memoryMaxMessages(), config,
                () -> getDefaultProvider());
            for (ChatMessage msg : entry.getValue()) {
                session.addMessage(msg.role, msg.content);
            }
            sessions.put(entry.getKey(), session);
        }
    }

    public void saveSessions() {
        if (!config.sessionPersistence()) return;
        Map<String, List<ChatMessage>> data = new HashMap<>();
        for (var entry : sessions.entrySet()) {
            data.put(entry.getKey(), entry.getValue().getHistory());
        }
        SessionStore.save(data);
    }

    private String worldName = "";

    public ChatSession getSession(String playerName) {
        String key = (worldName.isEmpty() ? "" : worldName + "/") + playerName;
        return sessions.computeIfAbsent(key,
                k -> new ChatSession(playerName, config.memoryMaxMessages(), config,
                    () -> getDefaultProvider()));
    }

    public CompletableFuture<String> handleInput(String playerName, String input) {
        ChatSession session = getSession(playerName);
        if (session.isAwaitingConfirm()) {
            if (input.equalsIgnoreCase("y") || input.equals("yes")) {
                session.confirm();
                return CompletableFuture.completedFuture("§aok");
            } else if (input.equalsIgnoreCase("n") || input.equals("no")) {
                session.cancel();
                session.clearPendingContextAction();
                return CompletableFuture.completedFuture("§ccancelled");
            }
        }
        session.addMessage("user", input);
        return getProviderFor(playerName).chat(playerName, input, session.getHistory())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        return handleChatFailure(session, throwable);
                    }
                    session.clearPendingContextAction();
                    session.addMessage("assistant", response);
                    return response;
                });
    }

    private String handleChatFailure(ChatSession session, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String message = cause.getMessage();
        if (message == null) message = cause.toString();
        if (isContextOverflow(message)) {
            session.markPendingContextAction("compact");
            session.setConfirmAction("压缩旧对话后继续", () -> {
                boolean changed = session.compactHistory();
                LOGGER.info("[智能核心] 上下文过长，执行 compact: changed={}", changed);
                session.clearPendingContextAction();
            });
            LOGGER.warn("[智能核心] 检测到上下文超限: {}", message);
            return "§e对话太长了，模型装不下当前上下文。\n"
                + "§a输入 /ai compact§7：压缩旧对话，尽量保留重点后继续\n"
                + "§c输入 /ai clear§7：清空旧对话，重新开始\n"
                + "§7也可以直接回复 y 立即压缩，回复 n 取消这次操作。";
        }
        throw new CompletionException(cause);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof java.util.concurrent.CompletionException
            || current instanceof java.util.concurrent.ExecutionException) {
            if (current.getCause() == null) break;
            current = current.getCause();
        }
        return current;
    }

    private boolean isContextOverflow(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("context")
            && (lower.contains("limit") || lower.contains("length") || lower.contains("window") || lower.contains("exceed"))
            || lower.contains("maximum context length")
            || lower.contains("context_length_exceeded")
            || lower.contains("上下文") && (lower.contains("超") || lower.contains("过长") || lower.contains("上限"));
    }

    public void addSystemMessage(String playerName, String content) {
        getSession(playerName).addMessage("system", content);
    }

    public String summarizeKnowledge(String playerName, String title, String content) {
        return getSession(playerName).summarizeDocument(title, content);
    }

    /** Allows a version-specific client surface to select the player-facing summary language. */
    public String summarizeKnowledge(String playerName, String title, String content, String instruction) {
        return getSession(playerName).summarizeDocument(title, content, instruction);
    }

    public void setWorldName(String name) { this.worldName = name; }
}
