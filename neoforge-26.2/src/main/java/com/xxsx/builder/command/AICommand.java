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
                                "§e内置: Agnes | DeepSeek | StepFun\n" +
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
                        "§e/ai clear — 清空旧对话，重新开始\n" +
                        "§e/ai compact — 压缩旧对话，尽量保留重点后继续\n" +
                        "§7若提示上下文过长: 回复 y=立即压缩, n=取消；或直接用 /ai compact /ai clear\n" +
                        "§7想查某个模组联动资料时，直接对 AI 说：总结 <主题> 文档 / 查询 <modid> 模组"), false);
                    return 1;
                })
        );

        dispatcher.register(Commands.literal("a")
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
        // 数字输入 → build 倍数确认（不匹配则忽略，不发AI）
        try {
            float multiplier = Float.parseFloat(input);
            if (multiplier <= 0 || multiplier > 100) {
                source.sendFailure(Component.literal("§c倍数范围: 0.5 ~ 100"));
                return 1;
            }
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            if (session.pendingBuildPath != null) {
                String path = session.pendingBuildPath;
                float actualScale = session.pendingBuildBaseScale * multiplier;
                session.pendingBuildPath = null;
                String mulStr = multiplier == (int)multiplier ? String.valueOf((int)multiplier) : String.format("%.1f", multiplier);
                source.sendSuccess(() -> Component.literal(
                    "§e倍数 " + mulStr + "x → 实际比例 " + Math.round(actualScale)), false);
                VoxelBuildManager m = XxsxBuilder.getInstance().getBuildManager();
                if (m != null) {
                    m.startBuild(playerName, path, actualScale, source);
                    // 应用预设速度
                    if (session.pendingBuildSpeed > 0)
                        m.setSpeed(playerName, session.pendingBuildSpeed);
                }
                return 1;
            }
            // 纯数字无pending build → 忽略，不发AI
            source.sendSuccess(() -> Component.literal("§7没有待确认的倍数输入"), false);
            return 1;
        } catch (NumberFormatException ignored) {}

        // clear
        if (input.startsWith("clear")) {
            XxsxBuilder.getInstance().getSessionManager().getSession(playerName).clearHistory();
            source.sendSuccess(() -> Component.literal("§a已清空旧对话，现在可以重新开始"), false);
            return 1;
        }

        // compact
        if (input.startsWith("compact")) {
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            boolean changed = session.compactHistory();
            session.clearPendingContextAction();
            if (changed) {
                source.sendSuccess(() -> Component.literal("§a已压缩旧对话，尽量保留重点后继续"), false);
            } else {
                source.sendSuccess(() -> Component.literal("§7当前对话还不长，暂时不用压缩"), false);
            }
            return 1;
        }

        SessionManager sm = XxsxBuilder.getInstance().getSessionManager();
        CommandExecutor exec = XxsxBuilder.getInstance().getCommandExecutor();
        if (sm == null || exec == null) {
            source.sendFailure(Component.literal("§cAI 系统未就绪"));
            return 1;
        }

        // PMX 路径检测 — 提取并注入 AI 上下文（不绕过 AI）
        String pmxPath = extractPmxPath(input);
        if (pmxPath != null) {
            pmxPath = pmxPath.replace("\"", "").trim();  // 去引号
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(pmxPath);
                if (java.nio.file.Files.exists(p)) {
                    com.xxsx.builder.voxel.PMXModel model;
                    if (pmxPath.toLowerCase().endsWith(".obj")) {
                        com.xxsx.builder.voxel.OBJModel o = new com.xxsx.builder.voxel.OBJParser().parse(p);
                        model = new com.xxsx.builder.voxel.PMXModel();
                        model.vertices.addAll(o.vertices); model.normals.addAll(o.normals);
                        model.uvs.addAll(o.uvs); model.faces.addAll(o.faces);
                        model.faceMaterials.addAll(o.faceMaterials); model.materials.addAll(o.materials);
                    } else {
                        model = new com.xxsx.builder.voxel.PMXParser().parse(p);
                    }
                    // 确保 textureBaseDir 已设置
                    if (model.textureBaseDir == null && p.getParent() != null)
                        model.textureBaseDir = p.getParent().toString();
                    float minX=999,minY=999,minZ=999,maxX=-999,maxY=-999,maxZ=-999;
                    for (var v : model.vertices) {
                        if(v.x<minX)minX=v.x; if(v.y<minY)minY=v.y; if(v.z<minZ)minZ=v.z;
                        if(v.x>maxX)maxX=v.x; if(v.y>maxY)maxY=v.y; if(v.z>maxZ)maxZ=v.z;
                    }
                    float sx=maxX-minX, sy=maxY-minY, sz=maxZ-minZ;
                    float maxDim = Math.max(sx, Math.max(sy, sz));
                    int suggested = (int)(50000.0 / Math.sqrt(model.vertices.size()));
                    suggested = Math.max(30, Math.min(400, suggested));
                    int gridW = Math.max(1, Math.round(sx * suggested / maxDim));
                    int gridH = Math.max(1, Math.round(sy * suggested / maxDim));
                    int gridD = Math.max(1, Math.round(sz * suggested / maxDim));
                    long estBlocks = ((long)gridW * gridH * gridD) / 10;
                    sm.addSystemMessage(playerName,
                        "【PMX模型信息】文件: " + p.getFileName()
                        + " | 顶点: " + model.vertices.size() + " | 面: " + model.faces.size()
                        + " | 材质: " + model.materials.size() + "\n"
                        + "尺寸: " + String.format("%.1f", sx) + "x" + String.format("%.1f", sy) + "x" + String.format("%.1f", sz)
                        + " | 建议比例: " + suggested + " (30-400)\n"
                        + "占地: " + gridW + "x" + gridH + "x" + gridD + " 方块"
                        + " | 约" + estBlocks + "方块\n"
                        + "→ 询问用户倍数后，用 /ai build 路径 触发建筑（优先系统功能）。");
                }
            } catch (Exception e) {
                source.sendSuccess(() -> Component.literal("§cPMX 预读失败: " + e.getMessage()), false);
            }
        }

        // 普通对话 — AI 主导，路径信息已注入上下文
        source.sendSuccess(() -> Component.literal("§7[你] " + input), false);
        ModLogger.info("[" + playerName + "] " + input);

        String anchorPos = CommandExecutor.getPlayerPos(source);
        ChatSession session = sm.getSession(playerName);
        sm.addSystemMessage(playerName, "玩家=" + playerName + " 坐标=" + anchorPos);
        source.sendSuccess(() -> Component.literal("§e思考中... (/ai stop 可终止)"), false);

        runAgent(sm, exec, session, input, anchorPos, anchorPos, source, 0);
        return 1;
    }

    /** 纯净 Agent：发送→执行→反馈→AI 自决是否继续 */
    private static void runAgent(SessionManager sm, CommandExecutor exec, ChatSession session,
            String prompt, String playerPos, String anchorPos, CommandSourceStack source, int depth) {

        // 安全兜底：工具链自动回灌后仍避免无意义循环
        if (depth > 20) {
            source.sendSuccess(() -> Component.literal("§e已执行 20 轮，自动暂停"), false);
            return;
        }

        CompletableFuture<String> future = sm.handleInput(session.playerName, prompt);
        session.setPendingFuture(future);
        CompletableFuture.delayedExecutor(15, TimeUnit.SECONDS).execute(() -> {
            if (!future.isDone() && !future.isCancelled()) {
                source.sendSuccess(() -> Component.literal("§e仍在等待... (/ai stop 可终止)"), false);
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
            boolean shouldContinue = hasToolFeedback || execResult.continueRequested;
            XxsxBuilder.LOGGER.info("[AICommand] execSummary found={} dispatched={} failed={} queries={} continueRequested={} shouldContinue={} feedback={}",
                execResult.commandsFound, execResult.commandsDispatched, execResult.commandsFailed,
                execResult.queriesHandled, execResult.continueRequested, shouldContinue,
                execFeedback.replace("\n", "\\n"));

            if (!execFeedback.isEmpty())
                source.sendSuccess(() -> ChatFold.foldExecResult(execFeedback), false);
            if (!execFeedback.isEmpty()) {
                sm.addSystemMessage(session.playerName, buildToolResultBlock(execResult, execFeedback));
            }
            VoxelBuildManager vm = XxsxBuilder.getInstance().getBuildManager();
            if (vm != null && vm.hasActiveJob(session.playerName)) {
                source.sendSuccess(() -> Component.literal("§7建造进行中，完成后可继续对话"), false);
                return;
            }
            if (shouldContinue) {
                runAgent(sm, exec, session,
                    "根据上一条 [TOOL_RESULT] 自行判断下一步：如果任务完成，直接简短总结；如果还需要操作，输出新的 [QUERY] 或 [CMD]。",
                    playerPos, anchorPos, source, depth + 1);
            }
        }, source.getServer())
        .exceptionally(e -> {
            if (!(e instanceof java.util.concurrent.CancellationException))
                source.sendFailure(Component.literal("§cAI 异常: " + e.getMessage()));
            return null;
        });
    }

    private static String buildToolResultBlock(CommandExecutor.ExecutionResult result, String feedback) {
        return "[TOOL_RESULT]\n"
            + "commands_found=" + result.commandsFound + "\n"
            + "commands_sent=" + result.commandsDispatched + "\n"
            + "commands_failed=" + result.commandsFailed + "\n"
            + "queries_handled=" + result.queriesHandled + "\n"
            + "next_policy=自行判断：完成就总结；需要继续就输出新的 [QUERY] 或 [CMD]；不要重复已经成功的同一条命令。\n"
            + "feedback:\n" + feedback.strip() + "\n"
            + "[/TOOL_RESULT]";
    }

    private static int handleBuildSpeed(CommandSourceStack src, String n) {
        try {
            int spd = Integer.parseInt(n);
            // 存到配置文件 + 文件持久化
            XxsxBuilder.getInstance().getConfig().buildSpeed = spd;
            saveBuildSpeed(spd);
            var vm = XxsxBuilder.getInstance().getBuildManager();
            String name = src.getTextName();
            if (vm != null && vm.setSpeed(name, spd)) {
                src.sendSuccess(() -> Component.literal("§e速度: " + spd + "/tick (" + (spd*20) + "/秒) [已保存]"), false);
            } else {
                src.sendSuccess(() -> Component.literal("§e速度已保存: " + spd + "/tick (建造开始时生效)"), false);
            }
        } catch (NumberFormatException e) {
            src.sendFailure(Component.literal("§c格式: /ai build speed <数字>"));
        }
        return 1;
    }

    private static void saveBuildSpeed(int spd) {
        try {
            var p = java.nio.file.Paths.get("config", "xxsx_builder", "build_speed.txt");
            java.nio.file.Files.createDirectories(p.getParent());
            java.nio.file.Files.writeString(p, String.valueOf(spd));
        } catch (Exception e) {
            XxsxBuilder.LOGGER.warn("[Build] 保存速度失败: {}", e.getMessage());
        }
    }

    public static int loadBuildSpeed() {
        try {
            var p = java.nio.file.Paths.get("config", "xxsx_builder", "build_speed.txt");
            if (java.nio.file.Files.exists(p))
                return Integer.parseInt(java.nio.file.Files.readString(p).trim());
        } catch (Exception ignored) {}
        return -1;
    }

    private static int handleBuildConfirm(CommandSourceStack src, boolean yes) {
        var vm = XxsxBuilder.getInstance().getBuildManager();
        if (vm == null) return 1;
        if (yes) {
            if (vm.confirmClear(src.getTextName()))
                src.sendSuccess(() -> Component.literal("§a已确认，正在清除..."), false);
            else src.sendFailure(Component.literal("§c没有待确认的建造"));
        } else {
            if (vm.cancelClear(src.getTextName()))
                src.sendSuccess(() -> Component.literal("§7已跳过清除"), false);
            else src.sendFailure(Component.literal("§c没有待确认的建造"));
        }
        return 1;
    }

    private static int handleBuildStop(CommandSourceStack src) {
        var vm = XxsxBuilder.getInstance().getBuildManager();
        if (vm != null && vm.cancelBuild(src.getTextName()))
            src.sendSuccess(() -> Component.literal("§a建造已停止"), false);
        else
            src.sendFailure(Component.literal("§c没有活跃的建造"));
        return 1;
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
        // 去引号
        String path = args.trim();
        if (path.startsWith("\"") && path.indexOf("\"", 1) > 0) {
            path = path.substring(1, path.indexOf("\"", 1));
        }
        if (path.isEmpty()) { src.sendFailure(Component.literal("§c请指定 PMX 路径")); return 1; }

        try {
            java.nio.file.Path p = java.nio.file.Paths.get(path);
            if (!java.nio.file.Files.exists(p)) {
                src.sendFailure(Component.literal("§c文件不存在: " + path)); return 1;
            }

            com.xxsx.builder.voxel.PMXModel model;
            if (path.toLowerCase().endsWith(".obj")) {
                var o = new com.xxsx.builder.voxel.OBJParser().parse(p);
                model = new com.xxsx.builder.voxel.PMXModel();
                model.vertices.addAll(o.vertices); model.normals.addAll(o.normals);
                model.uvs.addAll(o.uvs); model.faces.addAll(o.faces);
                model.faceMaterials.addAll(o.faceMaterials); model.materials.addAll(o.materials);
            } else {
                model = new com.xxsx.builder.voxel.PMXParser().parse(p);
            }
            if (model.textureBaseDir == null && p.getParent() != null)
                model.textureBaseDir = p.getParent().toString();
            float minX=999,minY=999,minZ=999,maxX=-999,maxY=-999,maxZ=-999;
            for (var v : model.vertices) {
                if(v.x<minX)minX=v.x; if(v.y<minY)minY=v.y; if(v.z<minZ)minZ=v.z;
                if(v.x>maxX)maxX=v.x; if(v.y>maxY)maxY=v.y; if(v.z>maxZ)maxZ=v.z;
            }
            float sx=maxX-minX, sy=maxY-minY, sz=maxZ-minZ;

            int suggested = (int)(50000.0 / Math.sqrt(model.vertices.size()));
            suggested = Math.max(30, Math.min(400, suggested));
            // 1倍 = suggested 比例下的方块尺寸
            float maxDim = Math.max(sx, Math.max(sy, sz));
            int w1 = Math.max(1, Math.round(sx * suggested / maxDim));
            int h1 = Math.max(1, Math.round(sy * suggested / maxDim));
            int d1 = Math.max(1, Math.round(sz * suggested / maxDim));

            src.sendSuccess(() -> Component.literal(
                "§e" + p.getFileName() + " | 顶点" + model.vertices.size()
                + " 面" + model.faces.size() + " 材质" + model.materials.size()), false);
            src.sendSuccess(() -> Component.literal(
                "§71x大小: " + w1 + "x" + h1 + "x" + d1 + " 方块"
                + " | 建议倍数1-10 (如3=" + (w1*3) + "x" + (h1*3) + "x" + (d1*3) + ")"), false);
            src.sendSuccess(() -> Component.literal(
                "§6输入倍数: /ai 3 (支持小数如 1.5 2.5) | §7调速: /ai build speed <数字>\n" +
                "§7默认1000/tick, 大模型可设500+防卡顿, 小模型可设5000+加速"), false);

            // 存待确认状态
            ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(playerName);
            session.pendingBuildPath = path;
            session.pendingBuildBaseScale = suggested;

        } catch (Exception e) {
            src.sendFailure(Component.literal("§cPMX解析失败: " + e.getMessage()));
        }
        return 1;
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
