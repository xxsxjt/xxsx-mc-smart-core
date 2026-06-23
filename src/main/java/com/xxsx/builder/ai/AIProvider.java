package com.xxsx.builder.ai;

import java.util.concurrent.CompletableFuture;
import java.util.List;

/** AI 供应商接口 */
public interface AIProvider {
    /** 发送消息，返回 AI 回复文本 */
    CompletableFuture<String> chat(String playerName, String message, List<ChatMessage> history);

    /** 供应商是否可用 */
    boolean isAvailable();

    /** 重置会话（某些供应商需清除上下文） */
    default void reset() {}
}
