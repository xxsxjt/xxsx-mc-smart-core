package com.xxsx.builder.ai;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** 单个玩家的会话状态 */
public class ChatSession {
    public final String playerName;
    private final List<ChatMessage> messages = new ArrayList<>();
    private final int maxMessages;
    private final BuilderConfig config;
    private boolean awaitingConfirm = false;
    private String pendingAction = "";
    private Runnable confirmAction;
    private CompletableFuture<?> pendingFuture;
    public String pendingBuildPath = null;
    public int pendingBuildBaseScale = 150;
    public int pendingBuildSpeed = -1; // -1=未指定, 用配置默认值
    private static final int MIN_KEEP_RECENT = 8;

    public ChatSession(String playerName, int maxMessages) {
        this(playerName, maxMessages, null);
    }

    public ChatSession(String playerName, int maxMessages, BuilderConfig config) {
        this.playerName = playerName;
        this.maxMessages = maxMessages;
        this.config = config;
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
        int maxTokens = (config != null) ? config.contextMaxTokens : 70000;
        String method = (config != null) ? config.contextCompression : "truncate";
        int tokens = estimateTokens(messages);
        if (tokens <= maxTokens) return;

        if ("summarize".equals(method)) {
            // AI 总结式压缩：调用 AI 总结旧的对话
            summarizeWithAI();
        } else {
            // 截断式压缩：保留首条+最近 N 条
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
        XxsxBuilder.LOGGER.info("[压缩] 截断至 {} 条", messages.size());
    }

    private void summarizeWithAI() {
        if (messages.size() <= 4) return;
        // 摘取旧消息让 AI 总结
        StringBuilder old = new StringBuilder();
        int mid = messages.size() - MIN_KEEP_RECENT;
        if (mid <= 1) { truncateToFit(); return; }
        for (int i = 1; i < mid; i++) {
            ChatMessage m = messages.get(i);
            old.append(m.role).append(": ").append(m.content).append("\n");
        }
        // 调用供应商 API 总结（同步进行）
        try {
            AIProvider provider = XxsxBuilder.getInstance().getSessionManager().getDefaultProvider();
            String summary = provider.chat("__system__",
                "请总结以下对话的关键信息（任务进度、玩家需求、已执行指令），保留所有重要细节：\n" + old.toString(),
                Collections.emptyList()).get(30000, java.util.concurrent.TimeUnit.MILLISECONDS);

            if (summary != null && !summary.isEmpty() && !summary.startsWith("§c")) {
                List<ChatMessage> kept = new ArrayList<>();
                kept.add(messages.get(0));
                kept.add(new ChatMessage("system", "【历史摘要】" + summary));
                for (int i = messages.size() - MIN_KEEP_RECENT; i < messages.size(); i++) {
                    if (i > 0 && i < messages.size()) kept.add(messages.get(i));
                }
                messages.clear();
                messages.addAll(kept);
                XxsxBuilder.LOGGER.info("[压缩] AI 总结完成: {} tokens → {} tokens", estimateTokens(messages) * 3, estimateTokens(messages));
                return;
            }
        } catch (Exception e) {
            XxsxBuilder.LOGGER.warn("[压缩] AI 总结失败，改用截断: {}", e.getMessage());
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
    }
}
