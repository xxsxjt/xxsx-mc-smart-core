package com.xxsx.builder;

import com.mojang.logging.LogUtils;
import com.xxsx.builder.ai.SessionManager;
import com.xxsx.builder.command.AICommand;
import com.xxsx.builder.ai.CommandExecutor;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.voxel.VoxelBuildManager;
import net.minecraft.network.chat.Component;
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

@Mod(XxsxBuilder.MODID)
public class XxsxBuilder {
    public static final String MODID = "xxsx_builder";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static XxsxBuilder instance;
    private BuilderConfig config;
    private SessionManager sessionManager;
    private CommandExecutor commandExecutor;
    private VoxelBuildManager buildManager;

    public XxsxBuilder() {
        instance = this;
        this.config = new BuilderConfig();
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(this::setup);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BuilderConfig.SPEC, MODID + "-common.toml");

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void setup(final FMLCommonSetupEvent event) {
        LOGGER.info("[xxsx_builder] 加载中...");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("[xxsx_builder] 服务启动 - 初始化AI系统...");
        this.sessionManager = new SessionManager(config);
        this.commandExecutor = new CommandExecutor(event.getServer(), config);
        this.buildManager = new VoxelBuildManager(event.getServer());
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

        boolean usingBuiltin = config.providerUrl.isEmpty();
        player.sendSystemMessage(Component.literal(
            "§b[xxsx的智能核心] §fv1.0.0 已装载"));
        player.sendSystemMessage(Component.literal(
            usingBuiltin
                ? "§e当前使用开发者内置 API，多人共用可能卡顿。建议添加自己的模型:"
                : "§a当前使用自定义 API"));

        player.sendSystemMessage(Component.literal(
            "  §b获取 Agnes API Key: §nhttps://apihub.agnes-ai.com"));
        player.sendSystemMessage(Component.literal(
            "  §b获取 DeepSeek API Key: §nhttps://platform.deepseek.com/api_keys"));
        player.sendSystemMessage(Component.literal(
            "  §6/ai addmodel <地址> <Key> <模型名> [上下文长度]"));
        player.sendSystemMessage(Component.literal(
            "  §7例: /ai addmodel https://apihub.agnes-ai.com/v1 sk-xxx agnes-2.0-flash 70000"));
        player.sendSystemMessage(Component.literal(
            "  §7例: /ai addmodel https://api.deepseek.com sk-xxx deepseek-v4-flash 500000"));
        player.sendSystemMessage(Component.literal(
            "§7AI 回复折叠显示，悬停查看全文"));
    }

    public static XxsxBuilder getInstance() { return instance; }
    public BuilderConfig getConfig() { return config; }
    public SessionManager getSessionManager() { return sessionManager; }
    public CommandExecutor getCommandExecutor() { return commandExecutor; }
    public VoxelBuildManager getBuildManager() { return buildManager; }
}
