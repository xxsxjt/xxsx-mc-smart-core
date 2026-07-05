package com.xxsx.builder.ai;

/** AI 通信中的一条消息 */
public class ChatMessage {
    public final String role;    // "system", "user", "assistant"
    public final String content;

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
