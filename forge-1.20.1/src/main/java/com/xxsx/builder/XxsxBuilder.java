package com.xxsx.builder;

import com.mojang.logging.LogUtils;
import com.xxsx.builder.ai.SessionManager;
import com.xxsx.builder.command.AICommand;
import com.xxsx.builder.ai.CommandExecutor;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.ModConfigScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod(XxsxBuilder.MODID)
public class XxsxBuilder {
    public static final String MODID = "xxsx_mc_smart_core";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicBoolean SHOWN_CONFIG = new AtomicBoolean(false);

    private static XxsxBuilder instance;
    private BuilderConfig config;
    private SessionManager sessionManager;
    private CommandExecutor commandExecutor;

    static final String SYSTEM_PROMPT = """
            你是 xxsx 的智建核心，运行在 Minecraft 1.20.1 Forge 中。

            工作方式：
            你在 Minecraft 1.20.1 Forge 中，玩家通过 /ai 与你对话。
            第一轮只收到玩家身份+需求。不确定时，用标签查询后再回复：

            [KNOWLEDGE]ftbquests[/KNOWLEDGE] — 查FTB任务书格式/章节/ID规则
            [KNOWLEDGE]bloodmagic[/KNOWLEDGE] — 查血魔法机制
            [QUERY]item 物品ID[/QUERY] — 查物品属性
            [QUERY]recipe 物品ID[/QUERY] — 查合成配方
            [QUERY]pmx 路径[/QUERY] — 查模型顶点/面/建议比例
            [QUERY]player|world|nearby|system[/QUERY] — 查游戏状态

            知识库位置：jar内置 + config/xxsx_builder/knowledge/（用户可放 .md 文件）
            AI 也可生成文档存入该目录，格式规范见 [KNOWLEDGE]ftbquests[/KNOWLEDGE]

            内置工具体系（可直接调用，AI也可通过[CMD]使用）：
            /ai build "路径" → PMX建筑 | /ai build y/n → 确认
            /ai build stop → 停止 | /ai build speed N → 调速
            [QUERY]pmx 路径[/QUERY] → 模型数据 | [QUERY]player|world|item|recipe|system → 游戏状态
            [KNOWLEDGE]名[/KNOWLEDGE] → 知识库（ftbquests/bloodmagic等）

            其他：用 [CMD]标签 执行指令。可多轮迭代。小操作直接执行，大操作先说明。

            禁止：/op /deop /ban /ban-ip /stop /kick /whitelist /pardon /debug /reload

            1.20.1 指令速查：
            /ai build "PMX路径" [比例] — 触发PMX体素建筑（不要自己算方块！）
            /give @s <物品>[{NBT}] [数量]
            /setblock <坐标> <方块>[属性]  /fill <从> <到> <方块>
            /summon <实体> [坐标] [{NBT}]  /kill <目标>
            /tp <目标> [x y z]  /time set day|night  /weather clear|rain|thunder
            /gamemode creative|survival|spectator  /xp add @s N levels
            /effect give <目标> <效果> [秒] [等级] [隐藏]
            /gamerule <规则> true|false  /difficulty peaceful|easy|normal|hard
            /title <目标> title|subtitle|actionbar <文本>
            /playsound <音效> master <目标> ~ ~ ~ 1 1
            /particle <粒子> <坐标> <dx> <dy> <dz> <速度> <数量>
            /locate structure|biome <ID>
            /data get|merge|remove block|entity <目标> [路径]
            /attribute <目标> <属性> get|base|set [值]
            /execute as <目标>|at <目标>|positioned <x y z>|if entity <目标>|if block <坐标> <方块> run <指令>

            方块状态:
            门: half=lower|upper,hinge=left|right,facing,open,powered
            活塞: facing,extended  观察者: facing,powered(无latched!)
            比较器: mode=compare|subtract,facing  中继器: delay=1-4,facing
            楼梯: half=top|bottom,facing,shape  台阶: type=top|bottom|double
            """;

    public XxsxBuilder() {
        instance = this;
        this.config = new BuilderConfig();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BuilderConfig.SPEC, MODID + "-common.toml");

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.addListener(this::onMainMenu);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("[xxsx_builder] 加载中...");
    }

    public void onMainMenu(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof TitleScreen && !SHOWN_CONFIG.getAndSet(true)) {
            event.setNewScreen(new ModConfigScreen());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[xxsx_builder] 服务启动 - 初始化AI系统...");
        this.sessionManager = new SessionManager(config, SYSTEM_PROMPT);
        this.sessionManager.setWorldName(
            event.getServer().getWorldData().getLevelName());
        this.commandExecutor = new CommandExecutor(event.getServer(), config);
        AICommand.register(event.getServer().getCommands().getDispatcher());
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("[xxsx_builder] 服务停止 - 保存会话...");
        if (sessionManager != null) sessionManager.saveSessions();
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        var player = event.getEntity();
        if (player == null || player.level().isClientSide) return;

        player.sendSystemMessage(Component.literal(
            "§b[xxsx的智能核心] §fv1.1.1 已装载"));
        boolean usingBuiltin = config.providerUrl.isEmpty();
        player.sendSystemMessage(Component.literal(
            usingBuiltin
                ? "§e当前使用内置 Kilo 免费模型（无需 Key，依次尝试 Beam → Auto → Flint）。建议添加自己的模型:"
                : "§a当前使用自定义 API"));
        player.sendSystemMessage(Component.literal(
            "  §b免费模型额度不足时，可添加自己的模型："));
        player.sendSystemMessage(Component.literal(
            "  §b获取 DeepSeek API Key: §nhttps://platform.deepseek.com/api_keys"));
        player.sendSystemMessage(Component.literal(
            "  §6/ai addmodel <地址> <Key> <模型名> [上下文长度]"));
        player.sendSystemMessage(Component.literal(
            "§7AI 回复折叠显示，悬停查看全文"));
    }

    public static XxsxBuilder getInstance() { return instance; }
    public BuilderConfig getConfig() { return config; }
    public SessionManager getSessionManager() { return sessionManager; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }
}
