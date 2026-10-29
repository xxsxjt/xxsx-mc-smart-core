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
import com.xxsx.builder.api.modelimport.ModelImportBridge;
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
                // api 子命令
                .then(Commands.literal("api")
                        .executes(ctx -> {
                            String cur = SessionManager.guessProviderName(
                                XxsxBuilder.getInstance().getConfig().providerUrl);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§e当前: " + cur + "\n§7/ai api <名称> 切换"), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(API_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    XxsxBuilder.getInstance().getConfig().switchToProvider(name);
                                    XxsxBuilder.getInstance().getSessionManager().initProvider();
                                    var models = SessionManager.PROVIDER_MODELS.get(name);
                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                        "§a已切换: " + name + " §7模型: " + (models != null ? String.join(", ", models) : "")
                                        + "\n§7/ai model <名称> 选择"), false);
                                    return 1;
                                })))
                // model 子命令
                .then(Commands.literal("model")
                        .executes(ctx -> {
                            String cur = XxsxBuilder.getInstance().getConfig().getEffectiveModel();
                            String prov = SessionManager.guessProviderName(
                                XxsxBuilder.getInstance().getConfig().providerUrl);
                            var models = SessionManager.PROVIDER_MODELS.getOrDefault(prov.replace(" (内置)", ""), List.of());
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§e" + prov + " / " + cur + "\n§7可用: " + String.join(", ", models)), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> {
                                    String prov = SessionManager.guessProviderName(
                                        XxsxBuilder.getInstance().getConfig().providerUrl).replace(" (内置)", "");
                                    var models = SessionManager.PROVIDER_MODELS.getOrDefault(prov, List.of());
                                    return net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        models.toArray(new String[0]), builder);
                                })
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    XxsxBuilder.getInstance().getConfig().switchToModel(name);
                                    XxsxBuilder.getInstance().getSessionManager().clearPlayerProviders();
                                    ctx.getSource().sendSuccess(() ->
                                        Component.literal("§a已切换模型: " + name), false);
                                    return 1;
                                })))
                // addmodel 子命令
                .then(Commands.literal("addmodel")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§b=== API 供应商 ===\n" +
                                "§e内置: Kilo Free (Beam → Auto → Flint)\n" +
                                "§7切换: /ai api <名称>  选模型: /ai model <名称>\n" +
                                "\n" +
                                "§b=== 添加自定义 API ===\n" +
                                "§7方式1（推荐）: 编辑配置文件\n" +
                                "§7  config/xxsx_builder-common.toml\n" +
                                "§7  改 ai.provider_url / ai.provider_key / ai.provider_model\n" +
                                "§7  然后重进世界即可生效\n" +
                                "§7方式2: /ai addmodel <地址> <Key> <模型名> [上下文长度]\n" +
                                "§7  例: /ai addmodel https://api.xxx.com sk-xxx my-model 70000\n" +
                                "§7  (聊天框字符限制，长Key建议用方式1)"), false);
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
                                                                + (finalTokens > 0 ? finalTokens : "默认")
                                                                + "\n§7提示: 长Key建议直接编辑 config/xxsx_builder-common.toml"), false);
                                                            return 1;
                                                        }))
                                                .executes(ctx -> {
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    String model = StringArgumentType.getString(ctx, "model");
                                                    PlayerConfig.set(ctx.getSource().getTextName(), url, key, model, 0);
                                                    XxsxBuilder.getInstance().getSessionManager().reloadProvider();
                                                    ctx.getSource().sendSuccess(() ->
                                                        Component.literal("§a已添加: " + model
                                                        + "\n§7提示: 长Key建议直接编辑 config/xxsx_builder-common.toml"), false);
                                                    return 1;
                                                })))))
                .then(Commands.literal("build")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "§e/ai build <路径> — 解析PMX\n" +
                                "§6/ai <数字> — 输入倍数(如 /ai 3)\n" +
                                "§e/ai build y/n — 确认/跳过清除\n" +
                                "§e/ai build stop — 停止建造\n" +
                                "§e/ai build speed <数字> — 调速"), false);
                            return 1;
                        })
                        .then(Commands.literal("speed")
                                .then(Commands.argument("n", StringArgumentType.word())
                                        .executes(ctx -> handleBuildSpeed(ctx.getSource(), StringArgumentType.getString(ctx, "n")))))
                        .then(Commands.literal("y")
                                .executes(ctx -> handleBuildConfirm(ctx.getSource(), true)))
                        .then(Commands.literal("n")
                                .executes(ctx -> handleBuildConfirm(ctx.getSource(), false)))
                        .then(Commands.literal("stop")
                                .executes(ctx -> handleBuildStop(ctx.getSource())))
                        .then(Commands.argument("path", StringArgumentType.greedyString())
                                .executes(ctx -> handleBuildCommand(
                                    StringArgumentType.getString(ctx, "path").trim(),
                                    ctx.getSource(), ctx.getSource().getTextName()))))
                .then(Commands.literal("flyspeed")
                        .then(Commands.argument("speed", StringArgumentType.word())
                                .executes(ctx -> handleFlySpeed(ctx.getSource(), StringArgumentType.getString(ctx, "speed")))))
                // 自然语言对话（必须在所有字面量之后，避免被 greedy 吞掉子命令）
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .executes(AICommand::execute))
                // 帮助
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "§e/ai <消息> — AI 对话\n" +
                        "§e/ai build <路径> — 解析PMX模型\n" +
                        "§6/ai <数字> — 输入建造倍数(如 /ai 3, 支持小数 2.5)\n" +
                        "§e/ai build y/n — 确认/跳过清除\n" +
                        "§e/ai build speed <N> — 调速\n" +
                        "§e/ai build stop — 停止建造\n" +
                        "§e/ai api/model — 切换模型\n" +
                        "§e/ai addmodel <url> <key> <模型名> [ctx长度] — 添加个人API\n" +
                        "§e/ai flyspeed <数字> — 修改飞行速度(默认0.05, 建议0.1~1.0)\n" +
                        "§e/ai stop — 终止当前任务\n" +
                        "§e/ai clear — 清除对话历史"), false);
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
            if (ModelImportBridge.cancelBuild(playerName)) {
                source.sendSuccess(() -> Component.literal("§a已取消建筑"), false); c = true;
            }
            if (!c) source.sendFailure(Component.literal("§c没有正在执行的任务"));
            return 1;
        }
        // 数字输入 → build 倍数确认（不匹配则忽略，不发AI）
        try {
            float multiplier = Float.parseFloat(input);
            if (multiplier <= 0 || multiplier > 100) {
                source.sendFailure(Component.literal("§c倍数范围: 0.5 ~ 100"));
                return 1;
            }
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            if (ModelImportBridge.handleNumericInput(input, source, session)) {
                return 1;
            }
            // 纯数字无pending build → 忽略，不发AI
            source.sendSuccess(() -> Component.literal("§7没有待确认的倍数输入"), false);
            return 1;
        } catch (NumberFormatException ignored) {}

        // clear
        if (input.startsWith("clear")) {
            XxsxBuilder.getInstance().getSessionManager().getSession(playerName).clearHistory();
            source.sendSuccess(() -> Component.literal("§a已清除"), false);
            return 1;
        }

        SessionManager sm = XxsxBuilder.getInstance().getSessionManager();
        CommandExecutor exec = XxsxBuilder.getInstance().getCommandExecutor();
        if (sm == null || exec == null) {
            source.sendFailure(Component.literal("§cAI 系统未就绪"));
            return 1;
        }

        // PMX/OBJ 路径检测由可选模型导入扩展处理，只把结果注入 AI 上下文。
        String pmxPath = extractPmxPath(input);
        if (pmxPath != null) {
            String modelInfo = ModelImportBridge.queryPMX(pmxPath);
            if (!modelInfo.isBlank()) sm.addSystemMessage(playerName, modelInfo);
        }

        // 普通对话 — AI 主导，路径信息已注入上下文
        source.sendSuccess(() -> Component.literal("§7[你] " + input), false);
        ModLogger.info("[" + playerName + "] " + input);

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
            boolean hasCommands = processed.toUpperCase().contains("[CMD]");

            if (hasCommands) {
                if (!execResult.isEmpty())
                    source.sendSuccess(() -> ChatFold.foldExecResult(execResult), false);
                // 如果触发了建造 → 停止 agent，建造有自己的交互(y/n/倍数)
                if (ModelImportBridge.hasActiveJob(session.playerName)) {
                    source.sendSuccess(() -> Component.literal("§7建造进行中，完成后可继续对话"), false);
                    return;
                }
                // 否则继续 agent 循环
                runAgent(sm, exec, session, "继续", playerPos, anchorPos, source, depth + 1);
            }
            // 无指令 = AI 认为任务完成，自然结束
        }, source.getServer())
        .exceptionally(e -> {
            if (!(e instanceof java.util.concurrent.CancellationException))
                source.sendFailure(Component.literal("§cAI 异常: " + e.getMessage()));
            return null;
        });
    }

    private static int handleBuildSpeed(CommandSourceStack src, String n) {
        return ModelImportBridge.handleBuildSpeed(src, n);
    }

    private static int handleBuildConfirm(CommandSourceStack src, boolean yes) {
        return ModelImportBridge.handleBuildConfirm(src, yes);
    }

    private static int handleBuildStop(CommandSourceStack src) {
        return ModelImportBridge.handleBuildStop(src);
    }

    private static int handleFlySpeed(CommandSourceStack src, String speedStr) {
        try {
            float speed = Float.parseFloat(speedStr);
            if (speed <= 0 || speed > 10) {
                src.sendFailure(Component.literal("§c速度范围: 0.01 ~ 10 (默认0.05)"));
                return 1;
            }
            var player = src.getPlayer();
            if (player == null) {
                src.sendFailure(Component.literal("§c仅玩家可用"));
                return 1;
            }
            player.getAbilities().setFlyingSpeed(speed);
            player.onUpdateAbilities();
            src.sendSuccess(() -> Component.literal(
                "§a飞行速度: " + String.format("%.2f", speed)
                + " (默认0.05, 当前" + String.format("%.0f", speed / 0.05) + "倍)"), false);
        } catch (NumberFormatException e) {
            src.sendFailure(Component.literal("§c格式: /ai flyspeed <数字>  例: /ai flyspeed 0.2"));
        }
        return 1;
    }

    /** /ai build 两步交互：先展示1倍坐标，再等用户输入倍数 */
    private static int handleBuildCommand(String args, CommandSourceStack src, String playerName) {
        return ModelImportBridge.handleBuildCommand(args, src, playerName);
    }

    private static String extractPmxPath(String input) {
        if (input == null || input.isEmpty()) return null;
        var q = java.util.regex.Pattern.compile(
            "\"([^\"]{5,}\\.(pmx|obj))\"", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (q.find()) return q.group(1).trim();
        var b = java.util.regex.Pattern.compile(
            "([A-Za-z]:[\\\\/][^\\s]{5,}\\.(pmx|obj))", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(input);
        if (b.find()) return b.group(1).trim();
        return null;
    }
}
