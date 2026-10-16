package com.xxsx.builder.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** 单个玩家的会话状态 */
public class ChatSession {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChatSession.class);
    public final String playerName;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maxMessages;
    private final ChatConfig config;
    private final Supplier<AIProvider> defaultProviderSupplier;
    private boolean awaitingConfirm = false;
    private String pendingAction = "";
    private Runnable confirmAction;
    private CompletableFuture<?> pendingFuture;
    public String pendingBuildPath = null;
    public float pendingBuildBaseScale = 150f;
    public int pendingBuildSpeed = -1; // -1=未指定, 用配置默认值
    private String pendingContextAction = "";
    private static final int MIN_KEEP_RECENT = 8;

    public ChatSession(String playerName, int maxMessages) {
        this(playerName, maxMessages, null, null);
    }

    public ChatSession(String playerName, int maxMessages, ChatConfig config) {
        this(playerName, maxMessages, config, null);
    }

    public ChatSession(String playerName, int maxMessages, ChatConfig config, Supplier<AIProvider> defaultProviderSupplier) {
        this.playerName = playerName;
        this.maxMessages = maxMessages;
        this.config = config;
        this.defaultProviderSupplier = defaultProviderSupplier;
    }

    public void addMessage(String role, String content) {
        if (content == null) return;
        messages.add(new ChatMessage(role, content));
        if (messages.size() > maxMessages) {
            messages.remove(0);
        }
        compressIfNeeded();
    }

    /** 估算 token 数 */
    private int estimateTokens(List<ChatMessage> msgs) {
        int tokens = 0;
        for (ChatMessage m : msgs) {
            if (m.content == null) continue;
            int chars = 0;
            for (char c : (m.role + ": " + m.content).toCharArray()) {
                if (!Character.isWhitespace(c)) chars++;
            }
            tokens += Math.max(1, chars / 3);
        }
        return tokens;
    }

    /** 上下文压缩 */
    private void compressIfNeeded() {
        int maxTokens = (config != null) ? config.contextMaxTokens() : 70000;
        String method = (config != null) ? config.contextCompression() : "truncate";
        int tokens = estimateTokens(messages);
        if (tokens <= maxTokens) return;

        if ("summarize".equals(method)) {
            summarizeWithAI();
        } else {
            truncateToFit();
        }
    }

    private void truncateToFit() {
        if (messages.size() <= 3) return;
        List<ChatMessage> kept = new ArrayList<>();
        kept.add(messages.get(0)); // 首条（玩家上下文）
        int keepRecent = Math.min(messages.size() - 1, MIN_KEEP_RECENT);
        for (int i = messages.size() - keepRecent; i < messages.size(); i++) {
            if (i > 0 && i < messages.size()) kept.add(messages.get(i));
        }
        messages.clear();
        messages.addAll(kept);
        LOGGER.info("[压缩] 截断至 {} 条", messages.size());
    }

    private void summarizeWithAI() {
        if (messages.size() <= 4) return;
        if (defaultProviderSupplier == null) { truncateToFit(); return; }

        StringBuilder old = new StringBuilder();
        int mid = messages.size() - MIN_KEEP_RECENT;
        if (mid <= 1) { truncateToFit(); return; }
        for (int i = 1; i < mid; i++) {
            ChatMessage m = messages.get(i);
            old.append(m.role).append(": ").append(m.content).append("\n");
        }
        try {
            AIProvider provider = defaultProviderSupplier.get();
            if (provider == null) { truncateToFit(); return; }
            String summary = provider.chat("__system__",
                "请总结以下对话的关键信息（任务进度、玩家需求、已执行指令），保留所有重要细节：\n" + old.toString(),
                Collections.emptyList()).get(30000, TimeUnit.MILLISECONDS);

            if (summary != null && !summary.isEmpty() && !summary.startsWith("§c")) {
                List<ChatMessage> kept = new ArrayList<>();
                kept.add(messages.get(0));
                kept.add(new ChatMessage("system", "【历史摘要】" + summary));
                for (int i = messages.size() - MIN_KEEP_RECENT; i < messages.size(); i++) {
                    if (i > 0 && i < messages.size()) kept.add(messages.get(i));
                }
                messages.clear();
                messages.addAll(kept);
                LOGGER.info("[压缩] AI 总结完成");
                return;
            }
        } catch (Exception e) {
            LOGGER.warn("[压缩] AI 总结失败，改用截断: {}", e.getMessage());
        }
        truncateToFit();
    }

    public List<ChatMessage> getHistory() {
        return Collections.unmodifiableList(messages);
    }

    public boolean isAwaitingConfirm() { return awaitingConfirm; }
    public String getPendingAction() { return pendingAction; }

    public void setConfirmAction(String description, Runnable action) {
        this.awaitingConfirm = true;
        this.pendingAction = description;
        this.confirmAction = action;
    }

    public void confirm() {
        if (confirmAction != null) confirmAction.run();
        awaitingConfirm = false; pendingAction = ""; confirmAction = null;
    }

    public void cancel() {
        awaitingConfirm = false; pendingAction = ""; confirmAction = null;
    }

    public void setPendingFuture(CompletableFuture<?> future) {
        this.pendingFuture = future;
    }

    public boolean cancelPending() {
        if (pendingFuture != null && !pendingFuture.isDone()) {
            return pendingFuture.cancel(true);
        }
        return false;
    }

    public void clearHistory() {
        cancelPending(); messages.clear();
        awaitingConfirm = false; pendingAction = ""; confirmAction = null;
        pendingContextAction = "";
    }

    public boolean compactHistory() {
        int before = messages.size();
        if (before <= 2) return false;
        int beforeTokens = estimateTokens(messages);
        String method = (config != null) ? config.contextCompression() : "truncate";
        if ("summarize".equals(method)) {
            summarizeWithAI();
        } else {
            truncateToFit();
        }
        int afterTokens = estimateTokens(messages);
        return messages.size() < before || afterTokens < beforeTokens;
    }

    public String summarizeDocument(String title, String content) {
        return summarizeDocument(title, content, "");
    }

    public String summarizeDocument(String title, String content, String instruction) {
        if (content == null || content.isBlank()) return "没有可总结的文档内容";
        if (defaultProviderSupplier == null) return shortenDocument(content, 1200);
        try {
            AIProvider provider = defaultProviderSupplier.get();
            if (provider == null) return shortenDocument(content, 1200);
            String prompt = instruction == null || instruction.isBlank()
                ? "请把下面这份文档总结成给普通 Minecraft 玩家看的联动说明。"
                    + "重点保留：这是哪个模组/主题、能做什么、关键规则、常用物品/结构、容易踩坑的地方。"
                    + "如果文档内容是技术格式，也请翻成自然语言。\n\n"
                    + "主题: " + title + "\n\n文档内容:\n" + content
                : instruction + "\n\nTopic: " + title + "\n\nDocument:\n" + content;
            String summary = provider.chat("__system__", prompt, Collections.emptyList())
                .get(30000, TimeUnit.MILLISECONDS);
            if (summary != null && !summary.isBlank() && !summary.startsWith("§c")) {
                return summary;
            }
        } catch (Exception e) {
            LOGGER.warn("[知识库] 文档总结失败，返回截断原文: {}", e.getMessage());
        }
        return shortenDocument(content, 1200);
    }

    private String shortenDocument(String content, int maxChars) {
        if (content == null) return "";
        if (content.length() <= maxChars) return content;
        return content.substring(0, maxChars) + "\n...[截断]";
    }

    public void markPendingContextAction(String action) {
        this.pendingContextAction = action == null ? "" : action;
    }

    public String getPendingContextAction() {
        return pendingContextAction;
    }

    public boolean hasPendingContextAction() {
        return !pendingContextAction.isEmpty();
    }

    public void clearPendingContextAction() {
        this.pendingContextAction = "";
    }
}
