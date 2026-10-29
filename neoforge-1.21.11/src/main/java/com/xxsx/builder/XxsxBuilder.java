package com.xxsx.builder;

import com.mojang.logging.LogUtils;
import com.xxsx.builder.ai.SessionManager;
import com.xxsx.builder.command.AICommand;
import com.xxsx.builder.ai.CommandExecutor;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.ModConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
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
            你是 xxsx 的智建核心，运行在 Minecraft 1.21.11 NeoForge 21.11.42 中。
            定位：客户端便携建筑辅助，只面向本地客户端/单机集成服使用；专用服务端安装无效。

            工作方式：
            你在 Minecraft 1.21.11 NeoForge 21.11.42 中，玩家通过 /ai 与你对话。
            第一轮只收到玩家身份+需求。不确定时，用标签查询后再回复：

            [QUERY]item 物品ID[/QUERY] — 查原版/模组物品属性
            [QUERY]player|world|nearby|system[/QUERY] — 查当前游戏状态
            [QUERY]mod modid[/QUERY] — 查某个模组是否安装/版本
            [QUERY]doc 主题名[/QUERY] — 读取某个主题的联动文档
            [QUERY]summarize 主题名[/QUERY] — 把联动文档总结成玩家能看懂的话
            [QUERY]search 主题名 关键词[/QUERY] — 在联动文档中检索关键词
            [QUERY]pmx 路径[/QUERY] — 查模型顶点/面/建议比例
            [QUERY]recipe 物品ID[/QUERY] — 查合成配方
            [QUERY]jei 物品ID[/QUERY] — 查 JEI 是否可用，并用服务端配方索引列出合成

            知识库位置：jar内置 + config/xxsx_builder/knowledge/（用户可放 .md 文件）
            AI 也可生成文档存入该目录；[KNOWLEDGE]主题名[/KNOWLEDGE] 用于加载某个主题的知识文档。

            内置工具体系（可直接调用，AI也可通过[CMD]使用）：
            /ai build "路径" → PMX建筑 | /ai build y/n → 确认
            /ai build stop → 停止 | /ai build speed N → 调速
            /ai compact → 压缩旧对话继续 | /ai clear → 清空旧对话重开
            [QUERY]...[/QUERY] → 通用原版查询 / 联动查询 / 文档总结
            [KNOWLEDGE]主题名[/KNOWLEDGE] → 加载指定主题知识（核心知识或模组联动文档）

            其他：用 [CMD]标签 执行指令。可多轮迭代。小操作直接执行，大操作先说明。
            只有确实需要根据执行结果继续判断时，才输出 [CONTINUE]；简单任务完成后不要继续尝试更多同类命令。
            当对话太长或模型提示上下文超限时，可用 [CMD]/ai compact[/CMD]；只有确定旧上下文没用了，才用 [CMD]/ai clear[/CMD]。
            在 1.21.11 这类新版本里，/give 的复杂物品请优先使用方括号组件格式，不要再输出旧的 {Enchantments:[...]} NBT 拼法。
            例如附魔物品应优先写成：/give @s minecraft:diamond_sword[minecraft:enchantments={levels:{"minecraft:sharpness":5,"minecraft:unbreaking":3}}] 1
            如需自定义名字，可继续在同一组组件里加 minecraft:custom_name='{"text":"名字","color":"gold","italic":false}'。

            禁止：/op /deop /ban /ban-ip /stop /kick /whitelist /pardon /debug /reload

            1.21.11 指令速查：
            /ai build "PMX路径" [比例] — 触发PMX体素建筑（不要自己算方块！）
            /give @s <物品ID>[minecraft:enchantments={levels:{"minecraft:sharpness":5}},minecraft:custom_name='{"text":"名字","italic":false}'] [数量]
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
            活塞: facing,extended  观察者: facing,powered
            比较器: mode=compare|subtract,facing  中继器: delay=1-4,facing
            楼梯: half=top|bottom,facing,shape  台阶: type=top|bottom|double
            """;

    public XxsxBuilder(IEventBus modBus, ModContainer modContainer) {
        instance = this;
        this.config = new BuilderConfig();
        modBus.addListener(this::setup);
        modContainer.registerConfig(ModConfig.Type.COMMON, BuilderConfig.SPEC);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.addListener(this::onMainMenu);
        NeoForge.EVENT_BUS.addListener(this::onMainMenuInit);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("[xxsx_builder] 加载中...");
    }

    public void onMainMenu(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof TitleScreen && !SHOWN_CONFIG.getAndSet(true)) {
            event.setNewScreen(new ModConfigScreen());
        }
    }

    public void onMainMenuInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof TitleScreen screen)) return;

        int buttonW = 82;
        int x = Math.max(4, Math.min(screen.width - buttonW - 4, screen.width / 2 + 104));
        int y = screen.height / 4 + 72;
        event.addListener(Button.builder(Component.literal("智能核心设置"), b -> openConfigScreen())
            .bounds(x, y, buttonW, 20)
            .build());
    }

    private void openConfigScreen() {
        Minecraft.getInstance().setScreen(new ModConfigScreen());
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
        if (player == null || player.level().isClientSide()) return;

        player.displayClientMessage(Component.literal(
            "[xxsx智能核心] v1.1.2 客户端辅助已装载 (MC 1.21.11)"), false);
        player.displayClientMessage(Component.literal(
            "定位：客户端便携建筑辅助；专用服务端安装无效。"), false);
        boolean usingBuiltin = config.providerUrl.isEmpty();
        player.displayClientMessage(Component.literal(
            usingBuiltin
                ? "当前使用内置 Kilo 免费模型（无需 Key，依次尝试 Beam → Auto → Flint）。建议添加自己的模型:"
                : "当前使用自定义 API"), false);
        player.displayClientMessage(Component.literal(
            "  免费模型额度不足时，可添加自己的模型："), false);
        player.displayClientMessage(Component.literal(
            "  获取 DeepSeek API Key: https://platform.deepseek.com/api_keys"), false);
        player.displayClientMessage(Component.literal(
            "  /ai addmodel <地址> <Key> <模型名> [上下文长度]"), false);
        player.displayClientMessage(Component.literal(
            "想查模组联动资料时，可直接让 AI 总结某个主题文档或查询某个 mod"), false);
        player.displayClientMessage(Component.literal(
            "对话太长可用 /ai compact，想重新开始可用 /ai clear"), false);
        player.displayClientMessage(Component.literal(
            "若提示上下文过长，可直接回复 y 压缩，回复 n 取消"), false);
        player.displayClientMessage(Component.literal(
            "AI 回复折叠显示，悬停查看全文"), false);
    }

    public static XxsxBuilder getInstance() { return instance; }
    public BuilderConfig getConfig() { return config; }
    public SessionManager getSessionManager() { return sessionManager; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }
}
