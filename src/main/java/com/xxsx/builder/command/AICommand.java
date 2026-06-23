package com.xxsx.builder.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.ai.ChatSession;
import com.xxsx.builder.ai.CommandExecutor;
import com.xxsx.builder.ai.SessionManager;
import com.xxsx.builder.config.ChatFold;
import com.xxsx.builder.config.KnowledgeBase;
import com.xxsx.builder.config.ModLogger;
import com.xxsx.builder.config.PlayerConfig;
import com.xxsx.builder.voxel.VoxelBuildManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AICommand {
    private static final SuggestionProvider<CommandSourceStack> API_SUGGESTIONS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    SessionManager.PROVIDERS.keySet().toArray(new String[0]), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ai")
                .requires(src -> src.hasPermission(2))
                // 自然语言对话
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .executes(AICommand::execute))
                // api 子命令
                .then(Commands.literal("api")
                        .executes(ctx -> {
                            String cur = XxsxBuilder.getInstance().getSessionManager().getCurrentProviderName();
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§e当前: " + cur + "\n§7/ai api <名称> 切换"), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(API_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    XxsxBuilder.getInstance().getSessionManager().switchProvider(name);
                                    var models = SessionManager.PROVIDER_MODELS.get(name);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§a已切换: " + name + " §7模型: " + (models != null ? String.join(", ", models) : "")
                                        + "\n§7/ai model <名称> 选择"), false);
                                    return 1;
                                })))
                // model 子命令
                .then(Commands.literal("model")
                        .executes(ctx -> {
                            String cur = XxsxBuilder.getInstance().getSessionManager().getCurrentModel();
                            String prov = XxsxBuilder.getInstance().getSessionManager().getCurrentProviderName();
                            var models = SessionManager.PROVIDER_MODELS.getOrDefault(prov.replace(" (内置)", ""), List.of());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§e" + prov + " / " + cur + "\n§7可用: " + String.join(", ", models)), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String prov = XxsxBuilder.getInstance().getSessionManager().getCurrentProviderName().replace(" (内置)", "");
                                    var models = SessionManager.PROVIDER_MODELS.getOrDefault(prov, List.of());
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        models.toArray(new String[0]), builder);
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    XxsxBuilder.getInstance().getSessionManager().switchModel(name);
                                    ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a已切换模型: " + name), false);
                                    return 1;
                                })))
                // addmodel 子命令
                .then(Commands.literal("addmodel")
                        .executes(ctx -> {
                            var pc = PlayerConfig.get(ctx.getSource().getTextName());
                            ctx.getSource().sendSuccess(() -> Component.literal(pc.hasCustomApi()
                                ? "§e你的模型: " + pc.apiUrl + " | " + pc.apiModel + " | ctx="
                                    + (pc.contextMaxTokens > 0 ? pc.contextMaxTokens : "默认")
                                : "§e未添加个人模型\n§7/ai addmodel <地址> <Key> <模型名> [上下文长度]"), false);
                            return 1;
                        })
                        .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("key", StringArgumentType.string())
                                        .then(Commands.argument("model", StringArgumentType.string())
                                                .then(Commands.argument("context", StringArgumentType.word())
                                                        .executes(ctx -> {
                                                            String url = StringArgumentType.getString(ctx, "url");
                                                            String key = StringArgumentType.getString(ctx, "key");
                                                            String model = StringArgumentType.getString(ctx, "model");
                                                            int ctxTokens = 0;
                                                            try { ctxTokens = Integer.parseInt(
                                                                StringArgumentType.getString(ctx, "context")); }
                                                            catch (NumberFormatException ignored) {}
                                                            final int finalTokens = ctxTokens;
                                                            PlayerConfig.set(ctx.getSource().getTextName(), url, key, model, finalTokens);
                                                            XxsxBuilder.getInstance().getSessionManager().reloadProvider();
                                                            ctx.getSource().sendSuccess(() -> Component.literal(
                                                                "§a已添加: " + model + " | 上下文="
                                                                + (finalTokens > 0 ? finalTokens : "默认")), false);
                                                            return 1;
                                                        }))
                                                .executes(ctx -> {
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    String model = StringArgumentType.getString(ctx, "model");
                                                    PlayerConfig.set(ctx.getSource().getTextName(), url, key, model, 0);
                                                    XxsxBuilder.getInstance().getSessionManager().reloadProvider();
                                                    ctx.getSource().sendSuccess(() ->
                                                        Component.literal("§a已添加: " + model), false);
                                                    return 1;
                                                })))))
                // build 子命令 — 直接解析 PMX，不经过 AI
                .then(Commands.literal("build")
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String args = StringArgumentType.getString(ctx, "path").trim();
                                    CommandSourceStack src = ctx.getSource();
                                    String n = src.getTextName();
                                    if (args.startsWith("\"") && args.indexOf("\"", 1) > 0) {
                                        int q = args.indexOf("\"", 1);
                                        args = args.substring(1, q) + " " + args.substring(q + 1).trim();
                                    }
                                    String[] parts = args.split("\\s+", 2);
                                    String path = parts[0];
                                    if (path.isEmpty()) {
                                        src.sendFailure(Component.literal("§c请指定 PMX 文件路径"));
                                        return 1;
                                    }
                                    int scale = XxsxBuilder.getInstance().getConfig().defaultScale;
                                    try { if (parts.length > 1) scale = Integer.parseInt(parts[1]); }
                                    catch (NumberFormatException e) {
                                        src.sendFailure(Component.literal("§c大小必须是数字"));
                                        return 1;
                                    }
                                    final int s = scale;

                                    // 快速预解析获取模型信息
                                    try {
                                        java.nio.file.Path pmxPath = java.nio.file.Paths.get(path);
                                        if (!java.nio.file.Files.exists(pmxPath)) {
                                            src.sendFailure(Component.literal("§c文件不存在: " + path));
                                            return 1;
                                        }
                                        long fsize = java.nio.file.Files.size(pmxPath);
                                        src.sendSuccess(() -> Component.literal(
                                            "§ePMX: " + pmxPath.getFileName()
                                            + " §7(" + (fsize/1024) + "KB, 比例 " + s + ")"), false);
                                    } catch (java.io.IOException ex) {
                                        src.sendFailure(Component.literal("§c文件错误: " + ex.getMessage()));
                                        return 1;
                                    }

                                    VoxelBuildManager m = XxsxBuilder.getInstance().getBuildManager();
                                    if (m != null) m.startBuild(n, path, s, src);
                                    return 1;
                                })))
                // 帮助
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e/ai <消息> — AI 对话\n" +
                        "§e/ai build <路径> [比例] — 直接解析 PMX\n" +
                        "§e/ai api/model — 切换模型\n" +
                        "§e/ai addmodel — 添加个人模型\n" +
                        "§e/ai stop/clear"), false);
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("a")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .executes(AICommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        String input = StringArgumentType.getString(ctx, "input").trim();
        CommandSourceStack source = ctx.getSource();
        String playerName = source.getTextName();

        // stop
        if (input.startsWith("stop")) {
            boolean c = false;
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            if (session.cancelPending()) { source.sendSuccess(() -> Component.literal("§a已取消"), false); c = true; }
            VoxelBuildManager mgr = XxsxBuilder.getInstance().getBuildManager();
            if (mgr != null && mgr.cancelBuild(playerName)) {
                source.sendSuccess(() -> Component.literal("§a已取消建筑"), false); c = true;
            }
            if (!c) source.sendFailure(Component.literal("§c没有正在执行的任务"));
            return 1;
        }
        // clear
        if (input.startsWith("clear")) {
            XxsxBuilder.getInstance().getSessionManager().getSession(playerName).clearHistory();
            source.sendSuccess(() -> Component.literal("§a已清除"), false);
            return 1;
        }

        // PMX 路径检测
        String pmxPath = extractPmxPath(input);
        if (pmxPath != null) {
            source.sendSuccess(() -> Component.literal("§e检测到 PMX 文件，开始解析..."), false);
            ModLogger.info("提取 PMX 路径: " + pmxPath);
            VoxelBuildManager m = XxsxBuilder.getInstance().getBuildManager();
            if (m != null) m.startBuild(playerName, pmxPath,
                XxsxBuilder.getInstance().getConfig().defaultScale, source);
            return 1;
        }

        // 普通对话
        source.sendSuccess(() -> Component.literal("§7[你] " + input), false);
        ModLogger.info("[" + playerName + "] " + input);

        SessionManager sm = XxsxBuilder.getInstance().getSessionManager();
        CommandExecutor exec = XxsxBuilder.getInstance().getCommandExecutor();
        if (sm == null || exec == null) {
            source.sendFailure(Component.literal("§cAI 系统未就绪"));
            return 1;
        }

        String anchorPos = CommandExecutor.getPlayerPos(source);
        ChatSession session = sm.getSession(playerName);
        sm.addSystemMessage(playerName, "玩家=" + playerName + " 坐标=" + anchorPos);
        source.sendSuccess(() -> Component.literal("§e思考中... (/ai stop 可终止)"), false);

        // 15 秒后提示（仅首轮）
        CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS).execute(() ->
            source.sendSuccess(() -> Component.literal("§e仍在等待... (/ai stop 可终止)"), false));

        runAgent(sm, exec, session, input, anchorPos, anchorPos, source, 0);
        return 1;
    }

    /** 纯净 Agent：发送→执行→反馈→AI 自决是否继续 */
    private static void runAgent(SessionManager sm, CommandExecutor exec, ChatSession session,
            String prompt, String playerPos, String anchorPos, CommandSourceStack source, int depth) {

        // 安全兜底：100 轮自动停
        if (depth > 100) {
            source.sendSuccess(() -> Component.literal("§e已执行 100 轮，自动暂停"), false);
            return;
        }

        CompletableFuture<String> future = sm.handleInput(session.playerName, prompt);
        session.setPendingFuture(future);

        future.thenAcceptAsync(response -> {
            if (response == null || response.isEmpty()) { return; }

            // 处理知识库标签
            String processed = KnowledgeBase.processKnowledgeTags(response, source);

            source.sendSuccess(() -> ChatFold.fold(response, session.playerName), false);
            ModLogger.aiChat(session.playerName, prompt, response);

            // 执行 [CMD]
            String execResult = exec.executeCommands(processed, source);
            boolean hasCommands = processed.toUpperCase().contains("[CMD]") && !execResult.isEmpty();

            if (!execResult.isEmpty())
                source.sendSuccess(() -> ChatFold.foldExecResult(execResult), false);

            if (hasCommands) {
                // 有指令→反馈结果→继续
                String feedback = "【执行结果】\n" + execResult
                    + "\n\n请继续。如任务已完成，回复即可，不要加 [CMD]。";
                source.sendSuccess(() -> Component.literal("§7── 继续 ──"), false);
                runAgent(sm, exec, session, feedback, playerPos, anchorPos, source, depth + 1);
            }
            // 无指令 = AI 认为任务完成，自然结束
        }, source.getServer())
        .exceptionally(e -> {
            if (!(e instanceof java.util.concurrent.CancellationException))
                source.sendFailure(Component.literal("§cAI 异常: " + e.getMessage()));
            return null;
        });
    }

    // ===== PMX 路径提取 =====

    private static String extractPmxPath(String input) {
        if (input == null || input.isEmpty()) return null;
        var qm = java.util.regex.Pattern.compile(
            "[\"\"\"]([^\"]{5,}\\.pmx)[\"\"\"]", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (qm.find()) { return qm.group(1).trim(); }
        var bm = java.util.regex.Pattern.compile(
            "([A-Za-z]:[\\\\/][^\\s]{5,}\\.pmx)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (bm.find()) { return bm.group(1).trim(); }
        return null;
    }
}
