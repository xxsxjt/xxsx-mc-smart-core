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
import com.xxsx.builder.config.SmartCoreText;
import com.xxsx.builder.modelimport.ModelImportBridge;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class AICommand {
    private static final SuggestionProvider<CommandSourceStack> API_SUGGESTIONS =
            (ctx, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                    SessionManager.PROVIDERS.keySet().toArray(new String[0]), builder);

    private static MutableComponent commandMessage(String key, Object... args) {
        return SmartCoreText.command(key, args);
    }

    private static Component success(String key, Object... args) {
        return commandMessage(key, args).withStyle(ChatFormatting.GREEN);
    }

    private static Component warning(String key, Object... args) {
        return commandMessage(key, args).withStyle(ChatFormatting.YELLOW);
    }

    private static Component detail(String key, Object... args) {
        return commandMessage(key, args).withStyle(ChatFormatting.GRAY);
    }

    private static Component failure(String key, Object... args) {
        return commandMessage(key, args).withStyle(ChatFormatting.RED);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ai")
                // api 子命令
                .then(Commands.literal("api")
                        .executes(ctx -> {
                            String cur = SessionManager.guessProviderName(
                                XxsxBuilder.getInstance().getConfig().providerUrl);
                            ctx.getSource().sendSuccess(() -> warning("api.current", cur), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(API_SUGGESTIONS)
                                .executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    XxsxBuilder.getInstance().getConfig().switchToProvider(name);
                                    XxsxBuilder.getInstance().getSessionManager().initProvider();
                                    var models = SessionManager.PROVIDER_MODELS.get(name);
                                    ctx.getSource().sendSuccess(() -> success("api.switched", name,
                                        models != null ? String.join(", ", models) : ""), false);
                                    return 1;
                                })))
                // model 子命令
                .then(Commands.literal("model")
                        .executes(ctx -> {
                            String cur = XxsxBuilder.getInstance().getConfig().getEffectiveModel();
                            String prov = SessionManager.guessProviderName(
                                XxsxBuilder.getInstance().getConfig().providerUrl);
                            var models = SessionManager.PROVIDER_MODELS.getOrDefault(prov.replace(" (内置)", ""), List.of());
                            ctx.getSource().sendSuccess(() -> warning("model.current", prov, cur,
                                String.join(", ", models)), false);
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
                                        success("model.switched", name), false);
                                    return 1;
                                })))
                // addmodel 子命令
                .then(Commands.literal("addmodel")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> commandMessage("addmodel.help")
                                .withStyle(ChatFormatting.AQUA), false);
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
                                                            ctx.getSource().sendSuccess(() -> success("addmodel.added_with_context",
                                                                model, finalTokens > 0 ? finalTokens
                                                                    : commandMessage("default").getString()), false);
                                                            return 1;
                                                        }))
                                                .executes(ctx -> {
                                                    String url = StringArgumentType.getString(ctx, "url");
                                                    String key = StringArgumentType.getString(ctx, "key");
                                                    String model = StringArgumentType.getString(ctx, "model");
                                                    PlayerConfig.set(ctx.getSource().getTextName(), url, key, model, 0);
                                                    XxsxBuilder.getInstance().getSessionManager().reloadProvider();
                                                    ctx.getSource().sendSuccess(() -> success("addmodel.added", model), false);
                                                    return 1;
                                                })))))
                .then(Commands.literal("build")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> warning("build.help"), false);
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
                    ctx.getSource().sendSuccess(() -> warning("help"), false);
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("a")
                .then(Commands.argument("input", StringArgumentType.greedyString())
                        .executes(AICommand::execute)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        XxsxBuilder.getInstance().refreshSessionLanguage();
        String input = StringArgumentType.getString(ctx, "input").trim();
        CommandSourceStack source = ctx.getSource();
        String playerName = source.getTextName();

        // stop
        if (input.startsWith("stop")) {
            boolean c = false;
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            if (session.cancelPending()) { source.sendSuccess(() -> success("stop.cancelled"), false); c = true; }
            if (session.isAwaitingConfirm()) {
                session.cancel();
                source.sendSuccess(() -> detail("stop.pending_cancelled"), false);
                c = true;
            }
            if (ModelImportBridge.cancelBuild(playerName)) {
                source.sendSuccess(() -> success("stop.build_cancelled"), false); c = true;
            }
            if (!c) source.sendFailure(failure("stop.no_active_task"));
            return 1;
        }
        // 数字输入 → build 倍数确认（不匹配则忽略，不发AI）
        try {
            float multiplier = Float.parseFloat(input);
            if (multiplier <= 0 || multiplier > 100) {
                source.sendFailure(failure("multiplier.range"));
                return 1;
            }
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            if (ModelImportBridge.handleNumericInput(input, source, session)) {
                return 1;
            }
            // 纯数字无pending build → 忽略，不发AI
            source.sendSuccess(() -> detail("multiplier.none_pending"), false);
            return 1;
        } catch (NumberFormatException ignored) {}

        // clear
        if (input.startsWith("clear")) {
            XxsxBuilder.getInstance().getSessionManager().getSession(playerName).clearHistory();
            source.sendSuccess(() -> success("clear.complete"), false);
            return 1;
        }

        // compact
        if (input.startsWith("compact")) {
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            boolean changed = session.compactHistory();
            session.clearPendingContextAction();
            if (changed) {
                source.sendSuccess(() -> success("compact.complete"), false);
            } else {
                source.sendSuccess(() -> detail("compact.not_needed"), false);
            }
            return 1;
        }

        SessionManager sm = XxsxBuilder.getInstance().getSessionManager();
        CommandExecutor exec = XxsxBuilder.getInstance().getCommandExecutor();
        if (sm == null || exec == null) {
            source.sendFailure(failure("ai.not_ready"));
            return 1;
        }
        ChatSession session = sm.getSession(playerName);
        if (session.isAwaitingConfirm()) {
            String pendingAction = session.getPendingAction();
            if (isConfirmInput(input)) {
                session.confirm();
                source.sendSuccess(() -> success("confirm.accepted", pendingAction), false);
                return 1;
            }
            if (isCancelInput(input)) {
                session.cancel();
                source.sendSuccess(() -> detail("confirm.cancelled", pendingAction), false);
                return 1;
            }
        }

        // 模型路径检测由可选模型导入扩展解析，并仅把结果注入 AI 上下文。
        String pmxPath = extractPmxPath(input);
        if (pmxPath != null) {
            String modelInfo = ModelImportBridge.queryPMX(pmxPath);
            if (!modelInfo.isBlank()) sm.addSystemMessage(playerName, modelInfo);
        }

        // 普通对话 — AI 主导，路径信息已注入上下文
        source.sendSuccess(() -> detail("you", input), false);
        ModLogger.info("[" + playerName + "] " + input);

        String anchorPos = CommandExecutor.getPlayerPos(source);
        sm.addSystemMessage(playerName, SmartCoreText.system("language_instruction").getString());
        sm.addSystemMessage(playerName, SmartCoreText.text("system.xxsx_mc_smart_core.player_location", playerName, anchorPos));
        source.sendSuccess(() -> warning("ai.thinking"), false);

        runAgent(sm, exec, session, input, anchorPos, anchorPos, source, 0);
        return 1;
    }

    /** 纯净 Agent：发送→执行→反馈→AI 自决是否继续 */
    private static void runAgent(SessionManager sm, CommandExecutor exec, ChatSession session,
            String prompt, String playerPos, String anchorPos, CommandSourceStack source, int depth) {

        // 安全兜底：工具链自动回灌后仍避免无意义循环
        if (depth > 20) {
            source.sendSuccess(() -> warning("ai.max_rounds"), false);
            return;
        }

        CompletableFuture<String> future = sm.handleInput(session.playerName, prompt);
        session.setPendingFuture(future);
        CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS).execute(() -> {
            if (!future.isDone() && !future.isCancelled()) {
                source.sendSuccess(() -> warning("ai.still_waiting"), false);
            }
        });

        future.thenAcceptAsync(response -> {
            if (response == null || response.isEmpty()) { return; }

            // 处理知识库标签
            String processed = KnowledgeBase.processKnowledgeTags(response, source);

            source.sendSuccess(() -> ChatFold.fold(response, session.playerName), false);
            ModLogger.aiChat(session.playerName, prompt, response);
            XxsxBuilder.LOGGER.info("[AICommand] 原始回复: {}", response.replace("\n", "\\n"));
            XxsxBuilder.LOGGER.info("[AICommand] 处理后回复: {}", processed.replace("\n", "\\n"));

            // 执行 [CMD]
            CommandExecutor.ExecutionResult execResult = exec.execute(processed, source);
            String execFeedback = execResult.feedbackText();
            boolean hasToolFeedback = !execFeedback.isBlank()
                && (execResult.executedAny() || execResult.hasQueryFeedback() || execResult.commandsFailed > 0);
            boolean shouldContinue = (hasToolFeedback || execResult.continueRequested)
                && !execResult.confirmationRequired;
            XxsxBuilder.LOGGER.info("[AICommand] execSummary found={} dispatched={} failed={} queries={} confirmationRequired={} continueRequested={} shouldContinue={} feedback={}",
                execResult.commandsFound, execResult.commandsDispatched, execResult.commandsFailed,
                execResult.queriesHandled, execResult.confirmationRequired, execResult.continueRequested, shouldContinue,
                execFeedback.replace("\n", "\\n"));

            if (!execFeedback.isEmpty())
                source.sendSuccess(() -> ChatFold.foldExecResult(execFeedback), false);
            if (!execFeedback.isEmpty()) {
                sm.addSystemMessage(session.playerName, buildToolResultBlock(execResult, execFeedback));
            }
            if (ModelImportBridge.hasActiveJob(session.playerName)) {
                source.sendSuccess(() -> detail("build.in_progress"), false);
                return;
            }
            if (shouldContinue) {
                runAgent(sm, exec, session,
                    SmartCoreText.system("tool_continue").getString(),
                    playerPos, anchorPos, source, depth + 1);
            }
        }, source.getServer())
        .exceptionally(e -> {
            if (!(e instanceof java.util.concurrent.CancellationException))
                source.sendFailure(failure("ai.exception", e.getMessage()));
            return null;
        });
    }

    private static String buildToolResultBlock(CommandExecutor.ExecutionResult result, String feedback) {
        return "[TOOL_RESULT]\n"
            + "commands_found=" + result.commandsFound + "\n"
            + "commands_sent=" + result.commandsDispatched + "\n"
            + "commands_failed=" + result.commandsFailed + "\n"
            + "queries_handled=" + result.queriesHandled + "\n"
            + "confirmation_required=" + result.confirmationRequired + "\n"
            + "next_policy=" + SmartCoreText.text(result.confirmationRequired
                ? "system.xxsx_mc_smart_core.tool_policy_confirm"
                : "system.xxsx_mc_smart_core.tool_policy_continue") + "\n"
            + "feedback:\n" + feedback.strip() + "\n"
            + "[/TOOL_RESULT]";
    }

    private static boolean isConfirmInput(String input) {
        return input.equalsIgnoreCase("y") || input.equalsIgnoreCase("yes")
            || input.equalsIgnoreCase("confirm") || input.equals("确认");
    }

    private static boolean isCancelInput(String input) {
        return input.equalsIgnoreCase("n") || input.equalsIgnoreCase("no")
            || input.equalsIgnoreCase("cancel") || input.equals("取消");
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
                src.sendFailure(failure("flyspeed.range"));
                return 1;
            }
            var player = src.getPlayer();
            if (player == null) {
                src.sendFailure(failure("flyspeed.player_only"));
                return 1;
            }
            player.getAbilities().setFlyingSpeed(speed);
            player.onUpdateAbilities();
            src.sendSuccess(() -> success("flyspeed.set", String.format("%.2f", speed),
                String.format("%.0f", speed / 0.05)), false);
        } catch (NumberFormatException e) {
            src.sendFailure(failure("flyspeed.format"));
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
