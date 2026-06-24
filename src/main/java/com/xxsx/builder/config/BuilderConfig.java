package com.xxsx.builder.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = "xxsx_builder", bus = Mod.EventBusSubscriber.Bus.MOD)
public class BuilderConfig {
    private static final ForgeConfigSpec.Builder inner = new ForgeConfigSpec.Builder();

    // === AI Provider ===
    public static final ForgeConfigSpec.ConfigValue<String> PROVIDER_URL =
            inner.comment("AI API 地址，留空则使用内置 API").define("ai.provider_url", "");
    public static final ForgeConfigSpec.ConfigValue<String> PROVIDER_KEY =
            inner.comment("API Key，留空则使用内置 Key").define("ai.provider_key", "");
    public static final ForgeConfigSpec.ConfigValue<String> PROVIDER_MODEL =
            inner.comment("模型名称，留空则使用内置模型").define("ai.provider_model", "");

    // === Chat / Context ===
    public static final ForgeConfigSpec.BooleanValue MEMORY_ENABLED =
            inner.comment("启用会话记忆").define("chat.memory_enabled", true);
    public static final ForgeConfigSpec.BooleanValue SESSION_PERSISTENCE =
            inner.comment("会话持久化：重连后保留对话").define("chat.session_persistence", true);
    public static final ForgeConfigSpec.IntValue CONTEXT_MAX_TOKENS =
            inner.comment("上下文最大 token 数，超限后按压缩策略处理").defineInRange("chat.context_max_tokens", 70000, 1000, 1000000);
    public static final ForgeConfigSpec.ConfigValue<String> CONTEXT_COMPRESSION =
            inner.comment("上下文压缩策略: truncate(截断) / summarize(AI总结)").define("chat.context_compression", "truncate");
    public static final ForgeConfigSpec.IntValue MEMORY_MAX_MESSAGES =
            inner.comment("每会话保留最大消息数").defineInRange("chat.memory_max_messages", 100, 1, 500);
    public static final ForgeConfigSpec.IntValue API_TIMEOUT_MS =
            inner.comment("AI API 超时（毫秒）").defineInRange("chat.api_timeout_ms", 300000, 5000, 600000);

    // === Knowledge Base ===
    public static final ForgeConfigSpec.ConfigValue<String> KNOWLEDGE_PATH =
            inner.comment("知识库根目录，子目录名为知识库名").define("knowledge.path", "config/xxsx_builder/knowledge");

    // === Build ===
    public static final ForgeConfigSpec.BooleanValue BUILD_CONFIRMATION =
            inner.comment("大型建筑需要玩家确认").define("build.require_confirmation", true);
    public static final ForgeConfigSpec.IntValue CONFIRM_THRESHOLD =
            inner.comment("触发确认的最小方块数").defineInRange("build.confirm_threshold", 100, 10, 10000);
    public static final ForgeConfigSpec.BooleanValue BUILD_ASK_CLEAR =
            inner.comment("建筑前询问是否清除周围区域").define("build.ask_clear", true);
    public static final ForgeConfigSpec.IntValue BLOCKS_PER_TICK =
            inner.comment("每 tick 放置方块数（20 tick=1秒，默认50=1000方块/秒）").defineInRange("build.blocks_per_tick", 50, 5, 1000);
    public static final ForgeConfigSpec.IntValue MAX_VERTICES =
            inner.comment("PMX 最大顶点数").defineInRange("build.max_vertices", 500000, 10000, 5000000);
    public static final ForgeConfigSpec.IntValue DEFAULT_SCALE =
            inner.comment("默认缩放大小（方块）").defineInRange("build.default_scale", 300, 10, 2000);

    // === Safety ===
    public static final ForgeConfigSpec.ConfigValue<String> COMMAND_BLACKLIST =
            inner.comment("AI 禁止执行的命令前缀，逗号分隔")
                .define("safety.command_blacklist", "op,deop,ban-ip,ban,stop,kick,whitelist,pardon,debug,reload,save-all,save-off,save-on");

    public static final ForgeConfigSpec SPEC;
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
    public boolean buildConfirmation = true;
    public int confirmThreshold = 100;
    public boolean buildAskClear = true;  // 建造前询问是否清除区域
    public int blocksPerTick = 50;
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
        c.buildConfirmation = BUILD_CONFIRMATION.get();
        c.buildAskClear = BUILD_ASK_CLEAR.get();
        c.confirmThreshold = CONFIRM_THRESHOLD.get();
        c.blocksPerTick = BLOCKS_PER_TICK.get();
        c.maxVertices = MAX_VERTICES.get();
        c.defaultScale = DEFAULT_SCALE.get();
        c.commandBlacklist = COMMAND_BLACKLIST.get();
        if (com.xxsx.builder.XxsxBuilder.getInstance() != null) {
            com.xxsx.builder.XxsxBuilder.getInstance().getConfig().apply(c);
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
        this.buildConfirmation = other.buildConfirmation;
        this.buildAskClear = other.buildAskClear;
        this.confirmThreshold = other.confirmThreshold;
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
}
