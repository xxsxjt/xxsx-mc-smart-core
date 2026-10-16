package com.xxsx.builder.config;

import com.xxsx.builder.ai.ChatConfig;
import com.xxsx.builder.ai.SessionManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

@net.neoforged.fml.common.EventBusSubscriber(modid = "xxsx_builder", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class BuilderConfig implements ChatConfig {
    private static final ModConfigSpec.Builder inner = new ModConfigSpec.Builder();

    // === AI Provider ===
    public static final ModConfigSpec.ConfigValue<String> PROVIDER_URL =
            inner.comment("AI API 地址，留空则使用内置 API").define("ai.provider_url", "");
    public static final ModConfigSpec.ConfigValue<String> PROVIDER_KEY =
            inner.comment("API Key，留空则使用内置 Key").define("ai.provider_key", "");
    public static final ModConfigSpec.ConfigValue<String> PROVIDER_MODEL =
            inner.comment("模型名称，留空则使用内置模型").define("ai.provider_model", "");

    // === Chat / Context ===
    public static final ModConfigSpec.BooleanValue MEMORY_ENABLED =
            inner.comment("启用会话记忆").define("chat.memory_enabled", true);
    public static final ModConfigSpec.BooleanValue SESSION_PERSISTENCE =
            inner.comment("会话持久化：重连后保留对话").define("chat.session_persistence", true);
    public static final ModConfigSpec.IntValue CONTEXT_MAX_TOKENS =
            inner.comment("上下文最大 token 数，超限后按压缩策略处理").defineInRange("chat.context_max_tokens", 70000, 1000, 1000000);
    public static final ModConfigSpec.ConfigValue<String> CONTEXT_COMPRESSION =
            inner.comment("上下文压缩策略: truncate(截断) / summarize(AI总结)").define("chat.context_compression", "truncate");
    public static final ModConfigSpec.IntValue MEMORY_MAX_MESSAGES =
            inner.comment("每会话保留最大消息数").defineInRange("chat.memory_max_messages", 100, 1, 500);
    public static final ModConfigSpec.IntValue API_TIMEOUT_MS =
            inner.comment("AI API 超时（毫秒）").defineInRange("chat.api_timeout_ms", 300000, 5000, 600000);

    // === Knowledge Base ===
    public static final ModConfigSpec.ConfigValue<String> KNOWLEDGE_PATH =
            inner.comment("知识库根目录，子目录名为知识库名").define("knowledge.path", "config/xxsx_builder/knowledge");

    // === Build ===
    public static final ModConfigSpec.BooleanValue BUILD_ASK_CLEAR =
            inner.comment("建筑前询问是否清除周围区域").define("build.ask_clear", true);
    public static final ModConfigSpec.IntValue BUILD_SPEED =
            inner.comment("建造速度（每tick方块数，-1=使用默认blocks_per_tick）").defineInRange("build.speed", -1, -1, 10000);
    public static final ModConfigSpec.IntValue BLOCKS_PER_TICK =
            inner.comment("每 tick 放置方块数（20tick=1秒，1000=20000方块/秒）").defineInRange("build.blocks_per_tick", 1000, 10, 10000);
    public static final ModConfigSpec.IntValue MAX_VERTICES =
            inner.comment("PMX 最大顶点数").defineInRange("build.max_vertices", 500000, 10000, 5000000);
    public static final ModConfigSpec.IntValue DEFAULT_SCALE =
            inner.comment("默认缩放大小（方块）").defineInRange("build.default_scale", 300, 10, 2000);

    // === Safety ===
    public static final ModConfigSpec.ConfigValue<String> COMMAND_BLACKLIST =
            inner.comment("AI 禁止执行的命令前缀，逗号分隔")
                .define("safety.command_blacklist", "op,deop,ban-ip,ban,stop,kick,whitelist,pardon,debug,reload,save-all,save-off,save-on");

    public static final ModConfigSpec SPEC;
    static { SPEC = inner.build(); }

    public BuilderConfig() {}

    // Runtime holders
    public String providerUrl = "";
    public String providerKey = "";
    public String providerModel = "";
    public boolean memoryEnabled = true;
    public boolean sessionPersistence = true;
    public int contextMaxTokens = 70000;
    public String contextCompression = "truncate";
    public int memoryMaxMessages = 100;
    public int apiTimeoutMs = 300000;
    public String knowledgePath = "config/xxsx_builder/knowledge";
    public boolean buildAskClear = true;
    public int buildSpeed = -1;  // -1=未设置, 用blocks_per_tick
    public int blocksPerTick = 1000;
    public int maxVertices = 500000;
    public int defaultScale = 300;
    public String commandBlacklist = "op,deop,ban-ip,ban,stop,kick,whitelist,pardon,debug,reload,save-all,save-off,save-on";

    /** 游戏内运行时覆盖的模型名（/ai model 设置） */
    public String runtimeModel = "";

    @SubscribeEvent
    public static void onLoad(ModConfigEvent.Loading event) { bake(); }
    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) { bake(); }

    public static void bake() {
        BuilderConfig c = new BuilderConfig();
        c.providerUrl = PROVIDER_URL.get();
        c.providerKey = PROVIDER_KEY.get();
        c.providerModel = PROVIDER_MODEL.get();
        c.memoryEnabled = MEMORY_ENABLED.get();
        c.sessionPersistence = SESSION_PERSISTENCE.get();
        c.contextMaxTokens = CONTEXT_MAX_TOKENS.get();
        c.contextCompression = CONTEXT_COMPRESSION.get();
        c.memoryMaxMessages = MEMORY_MAX_MESSAGES.get();
        c.apiTimeoutMs = API_TIMEOUT_MS.get();
        c.knowledgePath = KNOWLEDGE_PATH.get();
        c.buildAskClear = BUILD_ASK_CLEAR.get();
        c.buildSpeed = BUILD_SPEED.get();
        c.blocksPerTick = BLOCKS_PER_TICK.get();
        c.maxVertices = MAX_VERTICES.get();
        c.defaultScale = DEFAULT_SCALE.get();
        c.commandBlacklist = COMMAND_BLACKLIST.get();
        if (com.xxsx.builder.XxsxBuilder.getInstance() != null) {
            com.xxsx.builder.XxsxBuilder.getInstance().getConfig().apply(c);
            // build_speed.txt 优先级高于 TOML（/ai build speed 持久化值）
            int savedSpeed = com.xxsx.builder.command.AICommand.loadBuildSpeed();
            if (savedSpeed > 0) {
                com.xxsx.builder.XxsxBuilder.getInstance().getConfig().buildSpeed = savedSpeed;
            }
        }
    }

    public void apply(BuilderConfig other) {
        this.providerUrl = other.providerUrl;
        this.providerKey = other.providerKey;
        this.providerModel = other.providerModel;
        this.memoryEnabled = other.memoryEnabled;
        this.sessionPersistence = other.sessionPersistence;
        this.contextMaxTokens = other.contextMaxTokens;
        this.contextCompression = other.contextCompression;
        this.memoryMaxMessages = other.memoryMaxMessages;
        this.apiTimeoutMs = other.apiTimeoutMs;
        this.knowledgePath = other.knowledgePath;
        this.buildAskClear = other.buildAskClear;
        this.buildSpeed = other.buildSpeed;
        this.blocksPerTick = other.blocksPerTick;
        this.maxVertices = other.maxVertices;
        this.defaultScale = other.defaultScale;
        this.commandBlacklist = other.commandBlacklist;
        // 不覆盖 runtimeModel
    }

    /** 获取当前生效的模型名（优先运行时覆盖） */
    public String getEffectiveModel() {
        return !runtimeModel.isEmpty() ? runtimeModel : providerModel;
    }

    // === ChatConfig 接口实现 ===
    @Override public String providerUrl() { return providerUrl; }
    @Override public String providerKey() { return providerKey; }
    @Override public String providerModel() { return providerModel; }
    @Override public boolean memoryEnabled() { return memoryEnabled; }
    @Override public boolean sessionPersistence() { return sessionPersistence; }
    @Override public int contextMaxTokens() { return contextMaxTokens; }
    @Override public String contextCompression() { return contextCompression; }
    @Override public int memoryMaxMessages() { return memoryMaxMessages; }
    @Override public int apiTimeoutMs() { return apiTimeoutMs; }
    @Override public String knowledgePath() { return knowledgePath; }
    @Override public boolean buildAskClear() { return buildAskClear; }
    @Override public int buildSpeed() { return buildSpeed; }
    @Override public int blocksPerTick() { return blocksPerTick; }
    @Override public int maxVertices() { return maxVertices; }
    @Override public int defaultScale() { return defaultScale; }
    @Override public String commandBlacklist() { return commandBlacklist; }
    @Override public String mcVersionLabel() { return "Minecraft 1.21.1 NeoForge"; }

    /** 切换 API 供应商 */
    public void switchToProvider(String providerName) {
        String[] p = SessionManager.PROVIDERS.get(providerName);
        if (p == null) return;
        this.providerUrl = p[0];
        this.providerKey = p[1];
        var models = SessionManager.PROVIDER_MODELS.get(providerName);
        if (models != null && !models.isEmpty() && !models.get(0).startsWith("(")) {
            this.runtimeModel = models.get(0);
        }
    }

    /** 切换模型 */
    public void switchToModel(String modelName) {
        this.runtimeModel = modelName;
    }

    /** UI 一键应用：更新 URL/Key/Model/Context 四个字段 */
    public void applyConfig(String url, String key, String model, int contextMaxTokens) {
        this.providerUrl = url;
        this.providerKey = key;
        this.providerModel = model;
        this.runtimeModel = model;
        this.contextMaxTokens = Math.max(1000, Math.min(contextMaxTokens, 1000000));
    }

    /** 将当前 API 配置写回 TOML 文件 */
    public void saveToFile() {
        try {
            var path = java.nio.file.Paths.get("config", "xxsx_builder-common.toml");
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.writeString(path, buildTomlContent());
        } catch (Exception e) {
            com.xxsx.builder.XxsxBuilder.LOGGER.warn("保存配置失败: {}", e.getMessage());
        }
    }

    private String buildTomlContent() {
        return "\n[ai]\n" +
            "\t#AI API 地址，留空则使用内置 API\n" +
            "\tprovider_url = \"" + providerUrl + "\"\n" +
            "\t#API Key，留空则使用内置 Key\n" +
            "\tprovider_key = \"" + providerKey + "\"\n" +
            "\t#模型名称，留空则使用内置模型\n" +
            "\tprovider_model = \"" + (runtimeModel.isEmpty() ? providerModel : runtimeModel) + "\"\n" +
            "\n[chat]\n" +
            "\tmemory_enabled = " + memoryEnabled + "\n" +
            "\tmemory_max_messages = " + memoryMaxMessages + "\n" +
            "\tapi_timeout_ms = " + apiTimeoutMs + "\n" +
            "\tsession_persistence = " + sessionPersistence + "\n" +
            "\tcontext_max_tokens = " + contextMaxTokens + "\n" +
            "\tcontext_compression = \"" + contextCompression + "\"\n" +
            "\n[build]\n" +
            "\task_clear = " + buildAskClear + "\n" +
            "\tspeed = " + buildSpeed + "\n" +
            "\tblocks_per_tick = " + blocksPerTick + "\n" +
            "\tmax_vertices = " + maxVertices + "\n" +
            "\tdefault_scale = " + defaultScale + "\n" +
            "\n[safety]\n" +
            "\tcommand_blacklist = \"" + commandBlacklist + "\"\n" +
            "\n[knowledge]\n" +
            "\tpath = \"" + knowledgePath + "\"\n";
    }
}
