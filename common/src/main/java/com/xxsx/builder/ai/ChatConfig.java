package com.xxsx.builder.ai;

/**
 * 配置接口 — 各 MC 版本的 BuilderConfig 实现此接口。
 * common 模块不依赖任何 MC/Forge/NeoForge API。
 */
public interface ChatConfig {
    String providerUrl();
    String providerKey();
    String providerModel();
    String getEffectiveModel();
    boolean memoryEnabled();
    boolean sessionPersistence();
    int contextMaxTokens();
    String contextCompression();
    int memoryMaxMessages();
    int apiTimeoutMs();
    String knowledgePath();
    int blocksPerTick();
    int maxVertices();
    int defaultScale();
    boolean buildAskClear();
    int buildSpeed();
    String commandBlacklist();
    /** 获取当前 MC 版本的标识，用于 System Prompt */
    String mcVersionLabel();
}
