package com.xxsx.builder.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.xxsx.builder.XxsxBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每个玩家的个人配置（API key、模型等），不依赖服务器配置。
 * 数据存于 config/xxsx_builder/players/<name>.json
 */
public class PlayerConfig {
    private static final Path DIR = Paths.get("config", "xxsx_builder", "players");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, PlayerConfig> cache = new ConcurrentHashMap<>();

    public String apiUrl = "";
    public String apiKey = "";
    public String apiModel = "";
    public int contextMaxTokens = 0; // 0 = 使用全局默认

    public boolean hasCustomApi() {
        return !apiUrl.isEmpty() && !apiKey.isEmpty();
    }

    public static PlayerConfig get(String playerName) {
        return cache.computeIfAbsent(playerName, PlayerConfig::load);
    }

    public static void set(String playerName, String apiUrl, String apiKey, String apiModel, int contextTokens) {
        var c = new PlayerConfig();
        c.apiUrl = apiUrl; c.apiKey = apiKey; c.apiModel = apiModel; c.contextMaxTokens = contextTokens;
        cache.put(playerName, c);
        save(playerName, c);
    }

    private static PlayerConfig load(String playerName) {
        Path file = DIR.resolve(playerName + ".json");
        if (!Files.exists(file)) return new PlayerConfig();
        try {
            return GSON.fromJson(Files.readString(file), PlayerConfig.class);
        } catch (Exception e) {
            return new PlayerConfig();
        }
    }

    private static void save(String playerName, PlayerConfig config) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(playerName + ".json"), GSON.toJson(config));
        } catch (IOException e) {
            XxsxBuilder.LOGGER.error("[PlayerConfig] 保存失败: {}", e.getMessage());
        }
    }
}
