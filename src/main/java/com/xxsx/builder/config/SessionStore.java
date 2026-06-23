package com.xxsx.builder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ChatMessage;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * 会话持久化 — 同一世界同一玩家重连后保留对话记录。
 * 数据存于 config/xxsx_builder_sessions.json
 */
public class SessionStore {
    private static final Path FILE = Paths.get("config", "xxsx_builder_sessions.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 每个玩家的会话记录 */
    public static class SessionData {
        public String playerName;
        public List<ChatMessage> messages = new ArrayList<>();
    }

    /** 保存所有会话到文件 */
    public static void save(Map<String, List<ChatMessage>> sessions) {
        try {
            Files.createDirectories(FILE.getParent());
            List<SessionData> data = new ArrayList<>();
            for (var entry : sessions.entrySet()) {
                SessionData sd = new SessionData();
                sd.playerName = entry.getKey();
                sd.messages = entry.getValue();
                data.add(sd);
            }
            String json = GSON.toJson(data);
            Files.writeString(FILE, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            XxsxBuilder.LOGGER.info("[SessionStore] 已保存 {} 个会话", data.size());
        } catch (IOException e) {
            XxsxBuilder.LOGGER.error("[SessionStore] 保存失败", e);
        }
    }

    /** 从文件加载所有会话 */
    public static Map<String, List<ChatMessage>> load() {
        Map<String, List<ChatMessage>> result = new HashMap<>();
        if (!Files.exists(FILE)) return result;

        try {
            String json = Files.readString(FILE);
            List<SessionData> data = GSON.fromJson(json, new TypeToken<List<SessionData>>(){}.getType());
            if (data == null) return result;
            for (SessionData sd : data) {
                if (sd.playerName != null && sd.messages != null && !sd.messages.isEmpty()) {
                    result.put(sd.playerName, sd.messages);
                }
            }
            XxsxBuilder.LOGGER.info("[SessionStore] 已加载 {} 个会话", result.size());
        } catch (Exception e) {
            XxsxBuilder.LOGGER.error("[SessionStore] 加载失败", e);
        }
        return result;
    }
}
