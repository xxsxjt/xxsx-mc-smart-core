package com.xxsx.builder.ai;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.PlayerConfig;
import com.xxsx.builder.config.SessionStore;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.LinkedHashMap;

/** 管理所有玩家会话和 AI 供应商 */
public class SessionManager {
    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, AIProvider> playerProviders = new ConcurrentHashMap<>();
    private AIProvider defaultProvider;
    private final BuilderConfig config;

    public SessionManager(BuilderConfig config) {
        this.config = config;
        loadPersistedSessions();
        initProvider();
    }

    /** 根据玩家配置获取 API 设置（优先用个人配置） */
    private AIProvider createProviderFor(String playerName) {
        var pc = PlayerConfig.get(playerName);
        String url, key, model;
        if (pc.hasCustomApi()) {
            url = pc.apiUrl; key = pc.apiKey; model = pc.apiModel;
            XxsxBuilder.LOGGER.info("[智能核心] " + playerName + " 使用个人API: " + url);
        } else {
            url = config.providerUrl; key = config.providerKey; model = config.getEffectiveModel();
        }
        if (!url.isEmpty() && !key.isEmpty()) {
            return new ExternalProvider(url, key, model, config.apiTimeoutMs);
        }
        return new BuiltinProvider(config.apiTimeoutMs);
    }

    /** 初始化或切换供应商（全局默认） */
    public void initProvider() {
        defaultProvider = createProviderFor("__default__");
    }

    /** 获取指定玩家的供应商 */
    public AIProvider getProviderFor(String playerName) {
        return playerProviders.computeIfAbsent(playerName, k -> createProviderFor(k));
    }

    /** 获取默认供应商 */
    public AIProvider getDefaultProvider() { return defaultProvider; }

    /** Provider 定义: 名字 → (URL, Key) */
    public static final Map<String, String[]> PROVIDERS = new LinkedHashMap<>();
    /** 每个 Provider 的模型列表: Provider名字 → [模型名...] */
    public static final Map<String, List<String>> PROVIDER_MODELS = new LinkedHashMap<>();
    static {
        // 内置：Agnes（URL+Key在BuiltinProvider中）
        PROVIDERS.put("Agnes", new String[]{"", ""});
        PROVIDER_MODELS.put("Agnes", List.of("agnes-2.0-flash"));

        // 自定义：玩家通过 /ai addmodel 或配置文件添加
        PROVIDERS.put("Custom", new String[]{"", ""});
        PROVIDER_MODELS.put("Custom", List.of("(手动输入)"));
    }

    /** 两步切换：先选 provider */
    public void switchProvider(String providerName) {
        String[] p = PROVIDERS.get(providerName);
        if (p == null) return;
        config.providerUrl = p[0];
        config.providerKey = p[1];
        // 自动选该 provider 的第一个模型
        var models = PROVIDER_MODELS.get(providerName);
        if (models != null && !models.isEmpty() && !models.get(0).startsWith("(")) {
            config.runtimeModel = models.get(0);
        }
        playerProviders.clear(); // 清除缓存的 provider
    }

    /** 两步切换：再选模型 */
    public void switchModel(String modelName) {
        config.runtimeModel = modelName;
        playerProviders.clear();
    }

    /** 获取当前 provider 名 */
    public String getCurrentProviderName() {
        String url = config.providerUrl;
        if (url.isEmpty()) return "Agnes (内置)";
        if (url.contains("deepseek")) return "DeepSeek";
        if (url.contains("agnes")) return "Agnes";
        return "Custom";
    }

    /** 获取当前模型名 */
    public String getCurrentModel() {
        return config.getEffectiveModel();
    }

    private void loadPersistedSessions() {
        if (!config.sessionPersistence) return;
        Map<String, List<ChatMessage>> saved = SessionStore.load();
        for (var entry : saved.entrySet()) {
            ChatSession session = new ChatSession(entry.getKey(), config.memoryMaxMessages, config);
            for (ChatMessage msg : entry.getValue()) {
                session.addMessage(msg.role, msg.content);
            }
            sessions.put(entry.getKey(), session);
        }
    }

    public void saveSessions() {
        if (!config.sessionPersistence) return;
        Map<String, List<ChatMessage>> data = new HashMap<>();
        for (var entry : sessions.entrySet()) {
            data.put(entry.getKey(), entry.getValue().getHistory());
        }
        SessionStore.save(data);
    }

    public ChatSession getSession(String playerName) {
        return sessions.computeIfAbsent(playerName,
                k -> new ChatSession(playerName, config.memoryMaxMessages, config));
    }

    public CompletableFuture<String> handleInput(String playerName, String input) {
        ChatSession session = getSession(playerName);
        if (session.isAwaitingConfirm()) {
            if (input.equals("确定") || input.equals("确认") || input.equals("yes") || input.equals("y")) {
                session.confirm();
                return CompletableFuture.completedFuture("§a已确认");
            } else if (input.equals("取消") || input.equals("no") || input.equals("n") || input.equals("cancel")) {
                session.cancel();
                return CompletableFuture.completedFuture("§c已取消");
            }
        }
        session.addMessage("user", input);
        return getProviderFor(playerName).chat(playerName, input, session.getHistory())
                .thenApply(response -> {
                    session.addMessage("assistant", response);
                    return response;
                });
    }

    public void addSystemMessage(String playerName, String content) {
        getSession(playerName).addMessage("system", content);
    }

    public void reloadProvider() {
        if (defaultProvider != null) defaultProvider.reset();
        playerProviders.clear();
        sessions.clear();
        loadPersistedSessions();
        initProvider();
    }
}
