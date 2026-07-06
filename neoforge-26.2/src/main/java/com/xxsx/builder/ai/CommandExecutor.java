package com.xxsx.builder.ai;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.KnowledgeBase;
import com.xxsx.builder.config.ModLogger;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class CommandExecutor {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CommandExecutor.class);
    private static final Pattern CMD_PATTERN = Pattern.compile("\\[CMD\\](.*?)\\[/CMD\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_LINE = Pattern.compile("^\\s*/(\\w+.*)$", Pattern.MULTILINE);
    private static final Pattern QUERY_PATTERN = Pattern.compile("\\[QUERY\\](.*?)\\[/QUERY\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTINUE_PATTERN = Pattern.compile("\\[CONTINUE\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCHANT_ENTRY_PATTERN = Pattern.compile("\\{\\s*id\\s*:\\s*\"([^\"]+)\"\\s*,\\s*lvl\\s*:\\s*(\\d+)\\s*\\}");
    private static final Pattern CUSTOM_NAME_PATTERN = Pattern.compile("Name\\s*:\\s*('(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")");
    private record IntegrationDef(String modId, String topic, String name, String role) {}
    private static final List<IntegrationDef> INTEGRATIONS = List.of(
        new IntegrationDef("jei", "jei", "JEI 物品管理器", "配方/用途提示，配合 [QUERY]recipe 和 [QUERY]jei 使用"),
        new IntegrationDef("justenoughprofessions", "jei", "JEI 工作方块", "村民职业与工作方块提示"),
        new IntegrationDef("jade", "jade", "玉 Jade", "准星方块/实体识别，辅助建筑定位和调试"),
        new IntegrationDef("appleskin", "appleskin", "AppleSkin", "食物营养/饱和度提示，补充 [QUERY]item 食物信息"),
        new IntegrationDef("waystones", "waystones", "Waystones", "传送点/旅行网络规划"),
        new IntegrationDef("naturescompass", "compasses", "Nature's Compass", "生物群系定位建议"),
        new IntegrationDef("explorerscompass", "compasses", "Explorer's Compass", "结构定位建议"),
        new IntegrationDef("sophisticatedbackpacks", "sophisticated", "Sophisticated Backpacks", "建筑材料随身携带与升级规划"),
        new IntegrationDef("sophisticatedstorage", "sophisticated", "Sophisticated Storage", "大规模材料仓储规划"),
        new IntegrationDef("curios", "curios", "Curios", "饰品槽位和装备类模组识别"),
        new IntegrationDef("xaerominimap", "xaero", "Xaero's Minimap", "建筑点位、路径和地形导航"),
        new IntegrationDef("xaeroworldmap", "xaero", "Xaero's World Map", "区域规划与大地图标记"),
        new IntegrationDef("enchdesc", "enchanting", "Enchantment Descriptions", "附魔说明"),
        new IntegrationDef("enchantmentinsights", "enchanting", "Enchantment Insights", "附魔台预览"),
        new IntegrationDef("easymagic", "enchanting", "Easy Magic", "附魔台便捷操作"),
        new IntegrationDef("taxfreelevels", "enchanting", "Tax Free Levels", "经验等级消耗优化"),
        new IntegrationDef("legendarytooltips", "clientui", "Legendary Tooltips", "物品提示框增强"),
        new IntegrationDef("controlling", "clientui", "Controlling", "键位冲突查询"),
        new IntegrationDef("mousetweaks", "clientui", "Mouse Tweaks", "库存操作优化"),
        new IntegrationDef("lootr", "exploration", "Lootr", "探索战利品箱识别"),
        new IntegrationDef("shogi", "exploration", "Shogi", "可作为建筑/装饰主题参考"),
        new IntegrationDef("earth_online", "projectmods", "Earth Online", "本工作区 26.2 项目模组"),
        new IntegrationDef("templenihility", "projectmods", "Temple of Nihility", "本工作区 26.2 项目模组")
    );
    private static final List<String> CURIOS_SLOTS = List.of(
        "back", "belt", "body", "bracelet", "charm", "curio", "feet", "hands", "head", "necklace", "ring"
    );
    private static final Map<String, String> CURIOS_SLOT_ALIASES = Map.ofEntries(
        Map.entry("back", "back"), Map.entry("背部", "back"), Map.entry("背饰", "back"),
        Map.entry("belt", "belt"), Map.entry("腰带", "belt"),
        Map.entry("body", "body"), Map.entry("身体", "body"), Map.entry("胸饰", "body"),
        Map.entry("bracelet", "bracelet"), Map.entry("手镯", "bracelet"), Map.entry("手环", "bracelet"),
        Map.entry("charm", "charm"), Map.entry("护符", "charm"), Map.entry("符咒", "charm"),
        Map.entry("curio", "curio"), Map.entry("饰品", "curio"), Map.entry("饰品栏", "curio"),
        Map.entry("饰品槽", "curio"), Map.entry("栏位", "curio"), Map.entry("通用", "curio"),
        Map.entry("feet", "feet"), Map.entry("脚部", "feet"), Map.entry("鞋", "feet"),
        Map.entry("hands", "hands"), Map.entry("手部", "hands"), Map.entry("手套", "hands"),
        Map.entry("head", "head"), Map.entry("头部", "head"), Map.entry("头饰", "head"),
        Map.entry("necklace", "necklace"), Map.entry("项链", "necklace"),
        Map.entry("ring", "ring"), Map.entry("戒指", "ring"), Map.entry("指环", "ring")
    );
    private final MinecraftServer server;
    private final BuilderConfig config;
    private Set<String> blacklist;

    public CommandExecutor(MinecraftServer server, BuilderConfig config) {
        this.server = server;
        this.config = config;
        updateBlacklist();
    }

    public void updateBlacklist() {
        blacklist = new HashSet<>(Arrays.asList(config.commandBlacklist.split(",")));
    }

    public static String getPlayerName(CommandSourceStack source) {
        try {
            return source.getEntity() != null ? source.getEntity().getName().getString() : "SERVER";
        } catch (Exception e) { return "UNKNOWN"; }
    }

    public static String getPlayerPos(CommandSourceStack source) {
        var pos = source.getPosition();
        return String.format("%.1f %.1f %.1f", pos.x, pos.y, pos.z);
    }

    /**
     * 从 AI 回复提取并执行指令，同时处理 [QUERY] 查询。
     * 返回喂回 AI 的完整反馈。
     */
    public String executeCommands(String aiResponse, CommandSourceStack source) {
        return execute(aiResponse, source).feedbackText();
    }

    public static class ExecutionResult {
        public int commandsFound;
        public int commandsDispatched;
        public int commandsFailed;
        public int queriesHandled;
        public boolean continueRequested;
        public final StringBuilder feedback = new StringBuilder();

        public boolean executedAny() { return commandsDispatched > 0; }
        public boolean hasQueryFeedback() { return queriesHandled > 0; }
        public String feedbackText() { return feedback.toString(); }
    }

    public ExecutionResult execute(String aiResponse, CommandSourceStack source) {
        ExecutionResult result = new ExecutionResult();
        if (aiResponse == null || aiResponse.isEmpty()) return result;

        String afterQuery = processQueries(aiResponse, source, result);
        result.continueRequested = CONTINUE_PATTERN.matcher(afterQuery).find();
        int count = 0;
        boolean matchedCmdBlock = false;

        Matcher m = CMD_PATTERN.matcher(afterQuery);
        while (m.find()) {
            matchedCmdBlock = true;
            String cmd = m.group(1).trim();
            if (cmd.isEmpty()) continue;
            result.commandsFound++;
            LOGGER.info("[CommandExecutor] matched [CMD] block: {}", cmd.replace("\n", "\\n"));
            if (executeSingle(cmd, source, result)) count++;
        }

        if (!matchedCmdBlock) {
            int open = afterQuery.toUpperCase().indexOf("[CMD]");
            if (open >= 0) {
                String loose = afterQuery.substring(open + 5).trim();
                int close = loose.toUpperCase().indexOf("[/CMD]");
                if (close >= 0) loose = loose.substring(0, close).trim();
                int newline = loose.indexOf('\n');
                if (newline >= 0) loose = loose.substring(0, newline).trim();
                if (!loose.isEmpty()) {
                    result.commandsFound++;
                    LOGGER.warn("[CommandExecutor] using loose [CMD] fallback: {}", loose);
                    if (executeSingle(loose, source, result)) count++;
                }
            }
        }

        if (!matchedCmdBlock && result.commandsFound == 0) {
            Matcher lineMatcher = COMMAND_LINE.matcher(afterQuery);
            while (lineMatcher.find()) {
                String cmd = "/" + lineMatcher.group(1).trim();
                if (cmd.isEmpty()) continue;
                result.commandsFound++;
                LOGGER.info("[CommandExecutor] matched bare command line: {}", cmd);
                if (executeSingle(cmd, source, result)) count++;
            }
        }

        if (result.commandsFound == 0 && result.feedback.length() == 0) {
            LOGGER.warn("[CommandExecutor] no executable commands found in response: {}", aiResponse.replace("\n", "\\n"));
        }
        LOGGER.info("[CommandExecutor] summary found={} dispatched={} failed={} queries={} continue={}",
            result.commandsFound, result.commandsDispatched, result.commandsFailed, result.queriesHandled, result.continueRequested);
        return result;
    }

    /** 处理 [QUERY] 标签：读文件/查方块/查实体 */
    private String processQueries(String text, CommandSourceStack source, ExecutionResult result) {
        Matcher m = QUERY_PATTERN.matcher(text);
        while (m.find()) {
            String q = m.group(1).trim();
            String response = doQuery(q, source);
            if (response != null && !response.isEmpty()) {
                result.queriesHandled++;
                result.feedback.append("\n【查询结果】").append(response);
            }
        }
        return m.replaceAll(""); // 去掉 [QUERY] 块，避免干扰
    }

    /** 执行查询 */
    private String doQuery(String query, CommandSourceStack source) {
        String[] parts = query.split("\\s+", 3);
        if (parts.length == 0) return "无效查询";

        String type = parts[0].toLowerCase();
        String arg = parts.length > 1 ? parts[1] : "";
        String extra = parts.length > 2 ? parts[2] : "";

        try {
            switch (type) {
                case "file" -> {
                    return readFile(arg);
                }
                case "dir" -> {
                    return listDir(arg);
                }
                case "block" -> {
                    return queryBlock(source, arg);
                }
                case "nearby" -> {
                    return queryNearby(source, arg.isEmpty() ? "10" : arg);
                }
                case "player" -> {
                    return queryPlayer(source);
                }
                case "mods" -> {
                    return queryMods();
                }
                case "integrations", "integration", "联动" -> {
                    return queryIntegrations(arg);
                }
                case "commands", "command", "cmds", "cmdlib", "指令", "命令" -> {
                    return queryCommandLibrary(arg, extra, source);
                }
                case "curios" -> {
                    return queryCommandLibrary("curios", (arg + " " + extra).trim(), source);
                }
                case "mod" -> {
                    return queryMod(arg);
                }
                case "doc" -> {
                    return queryKnowledgeDoc(arg);
                }
                case "summarize" -> {
                    return summarizeKnowledge(arg, source);
                }
                case "search" -> {
                    return searchKnowledge(arg, extra);
                }
                case "world" -> {
                    return queryWorld(source);
                }
                case "item" -> {
                    return queryItem(arg);
                }
                case "pmx" -> {
                    return queryPMX(arg);
                }
                case "recipe" -> {
                    return queryRecipes(arg);
                }
                case "jei" -> {
                    return queryJei(arg);
                }
                case "gamerule" -> {
                    return queryGamerule(source, arg);
                }
                case "system" -> {
                    return querySystem();
                }
                case "help" -> {
                    return "可用查询: player, item <id>, block x y z, nearby <半径>, world, file <路径>, dir <目录>, mods, integrations [关键词], commands [modid/动作], curios <动作> [槽位] [数量], mod <modid>, doc <主题>, summarize <主题>, search <主题> <关键词>, recipe <物品id>, jei <物品id>";
                }
                default -> {
                    return "未知查询类型: " + type + "，输入 [QUERY]help[/QUERY] 查看可用查询";
                }
            }
        } catch (Exception e) {
            return "查询失败: " + e.getMessage();
        }
    }

    private String readFile(String path) {
        // 安全检查：只允许读 config/ 和 logs/ 下的文件
        Path p = Paths.get(path);
        if (!p.normalize().toString().startsWith("config") && !p.normalize().toString().startsWith("logs")) {
            return "安全限制：只能读取 config/ 和 logs/ 目录下的文件";
        }
        if (!Files.isRegularFile(p)) return "文件不存在: " + path;
        try {
            String content = Files.readString(p);
            if (content.length() > 5000) content = content.substring(0, 5000) + "\n...[截断]";
            return "文件 " + path + ":\n" + content;
        } catch (IOException e) { return "读取失败: " + e.getMessage(); }
    }

    private String listDir(String path) {
        Path p = Paths.get(path.isEmpty() ? "." : path);
        if (!Files.isDirectory(p)) return "目录不存在: " + path;
        try {
            return Files.list(p).limit(50)
                .map(f -> (Files.isDirectory(f) ? "D " : "F ") + f.getFileName())
                .collect(Collectors.joining("\n"));
        } catch (IOException e) { return "列出失败: " + e.getMessage(); }
    }

    private String queryBlock(CommandSourceStack source, String coords) {
        BlockPos pos = parseCoords(coords, source);
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        BlockState state = level.getBlockState(pos);
        return "方块 " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + ": " + state.getBlock().builtInRegistryHolder().key().identifier();
    }

    private String queryNearby(CommandSourceStack source, String radius) {
        double r = Double.parseDouble(radius);
        Vec3 center = source.getPosition();
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        var entities = level.getEntities((Entity)null,
            new net.minecraft.world.phys.AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r),
            e -> true);
        StringBuilder sb = new StringBuilder("附近 " + entities.size() + " 个实体 (半径" + r + "):\n");
        entities.stream().limit(20).forEach(e ->
            sb.append(String.format("  %s [%s] (%.1f, %.1f, %.1f)\n",
                e.getName().getString(), e.getType().toShortString(), e.getX(), e.getY(), e.getZ())));
        if (entities.size() > 20) sb.append("  ...(共 " + entities.size() + " 个)");
        return sb.toString();
    }

    private String queryPlayer(CommandSourceStack source) {
        Entity e = source.getEntity();
        if (!(e instanceof ServerPlayer p)) return "不是玩家";
        return String.format("玩家=%s 维度=%s 坐标=%.1f %.1f %.1f 血量=%.1f/%s 经验=%d级 手持=%s 游戏模式=%s",
            p.getName().getString(),
            p.level().dimension().identifier(),
            p.getX(), p.getY(), p.getZ(),
            p.getHealth(), p.getMaxHealth(),
            p.experienceLevel,
            p.getMainHandItem().isEmpty() ? "空" : p.getMainHandItem().getDisplayName().getString(),
            p.gameMode.getGameModeForPlayer().getName());
    }

    private String queryGamerule(CommandSourceStack source, String rule) {
        return "用 [CMD]/gamerule " + (rule.isEmpty() ? "" : rule) + "[/CMD] 查看/设置。常用: keepInventory doDaylightCycle doWeatherCycle doMobSpawning mobGriefing doFireTick randomTickSpeed";
    }

    private String querySystem() {
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory() / (1024*1024);
        long totalMem = rt.totalMemory() / (1024*1024);
        long freeMem = rt.freeMemory() / (1024*1024);
        int cores = rt.availableProcessors();
        String os = System.getProperty("os.name");
        int bpt = XxsxBuilder.getInstance().getConfig().blocksPerTick;
        int bps = bpt * 20;
        return String.format("CPU: %d核 | OS: %s | 内存: %dMB/%dMB(空闲%dMB) | 建筑速度: %d方块/秒(%d/tick)",
            cores, os, totalMem, maxMem, freeMem, bps, bpt);
    }

    private String queryWorld(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) return "无维度";
        long dayTime = level.getLevelData().getGameTime() % 24000;
        return String.format("维度=%s 时间=%d ticks (%s) 天气=%s 难度=%s",
            level.dimension().identifier(),
            dayTime,
            dayTime < 13000 ? "白天" : "夜晚",
            level.isRaining() ? (level.isThundering() ? "雷暴" : "下雨") : "晴朗",
            level.getDifficulty().name());
    }

    private String queryMods() {
        return net.neoforged.fml.ModList.get().getMods().stream()
            .map(m -> m.getModId() + " " + m.getVersion())
            .limit(50).collect(Collectors.joining("\n", "已装 mods:\n", ""));
    }

    private String queryIntegrations(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder("26.2 可联动客户端/内容模组:\n");
        int shown = 0;
        int loaded = 0;
        for (IntegrationDef def : INTEGRATIONS) {
            if (!needle.isEmpty()
                && !def.modId().contains(needle)
                && !def.topic().contains(needle)
                && !def.name().toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            Optional<String> version = loadedModVersion(def.modId());
            if (version.isPresent()) loaded++;
            shown++;
            sb.append(version.isPresent() ? "  [已安装] " : "  [未检测] ")
                .append(def.modId()).append(" — ").append(def.name());
            version.ifPresent(v -> sb.append(" ").append(v));
            sb.append("\n    主题: ").append(def.topic())
                .append(" | ").append(def.role())
                .append("\n");
        }
        if (shown == 0) {
            return "未找到匹配的联动项: " + filter + "\n可用: [QUERY]integrations[/QUERY]";
        }
        sb.append("已检测 ").append(loaded).append("/").append(shown)
            .append(" 项。读取总清单: [QUERY]doc integrations[/QUERY]；读取主题: [QUERY]doc <主题>[/QUERY]");
        return sb.toString();
    }

    private String queryCommandLibrary(String topic, String detail, CommandSourceStack source) {
        String normalized = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        String merged = ((topic == null ? "" : topic) + " " + (detail == null ? "" : detail))
            .trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty() || containsAny(normalized, "help", "index", "categories", "category", "list", "ls", "分类", "目录", "列表")) {
            return commandLibraryIndex();
        }
        if (isCuriosTopic(normalized)) {
            return queryCuriosCommandLibrary(detail, source);
        }
        if (looksLikeCuriosCommand(merged)) {
            return queryCuriosCommandLibrary(merged, source);
        }
        return "未找到内置指令库: " + normalized
            + "\n可用分类: slots/饰品栏/curios。"
            + "\n可直接查: [QUERY]commands add 饰品栏[/QUERY]、[QUERY]commands slots[/QUERY]、[QUERY]curios add ring[/QUERY]"
            + "\n通用资料仍可用 [QUERY]integrations " + normalized + "[/QUERY] 或 [QUERY]doc " + normalized + "[/QUERY]";
    }

    private String commandLibraryIndex() {
        return "内置联动指令库分类:\n"
            + commandLibraryStatus("curios", "slots / 饰品栏 / Curios 槽位", "commands add 饰品栏") + "\n"
            + "便携查法:\n"
            + "  [QUERY]commands add 饰品栏[/QUERY] — 默认给自己加 1 个通用 curio 槽\n"
            + "  [QUERY]commands add ring[/QUERY] — 给自己加 1 个戒指槽\n"
            + "  [QUERY]commands set ring 2[/QUERY] — 设置戒指槽为 2 个\n"
            + "  [QUERY]commands slots[/QUERY] — 查看可用槽位与中文别名\n"
            + "工具链规则: 查询/执行后会返回 [TOOL_RESULT]，模型会自动判断下一步，无需为了等结果强行写 [CONTINUE]。";
    }

    private String commandLibraryStatus(String modId, String name, String example) {
        boolean modLoaded = isModLoaded(modId);
        boolean commandLoaded = server.getCommands().getDispatcher().getRoot().getChild(modId) != null;
        return "  " + (modLoaded ? "[已安装] " : "[未检测] ") + modId + " — " + name
            + (commandLoaded ? "，命令已注册" : "，命令未注册/世界未加载")
            + "；例: [QUERY]" + example + "[/QUERY]";
    }

    private String queryCuriosCommandLibrary(String detail, CommandSourceStack source) {
        String d = detail == null ? "" : detail.trim().toLowerCase(Locale.ROOT);
        boolean modLoaded = isModLoaded("curios");
        boolean commandLoaded = server.getCommands().getDispatcher().getRoot().getChild("curios") != null;
        String slot = findCuriosSlot(d);
        int amount = findFirstInt(d).orElse(1);
        String action = findCuriosAction(d);
        String player = "@s";

        StringBuilder sb = new StringBuilder();
        sb.append("Curios 内置指令库 (26.2)\n")
            .append("分类: slots / 饰品栏 / curio；意图: ").append(action)
            .append("；槽位: ").append(slot).append("；数量: ").append(amount).append("\n")
            .append("状态: ").append(modLoaded ? "curios 已安装" : "未检测到 curios")
            .append("；").append(commandLoaded ? "/curios 命令已注册" : "/curios 命令未注册")
            .append("；需要游戏管理员权限。\n");

        if (d.isEmpty() || action.equals("help")) {
            appendCuriosGeneralHelp(sb);
            return sb.toString();
        }
        if (action.equals("slots")) {
            appendCuriosSlots(sb);
            return sb.toString();
        }

        switch (action) {
            case "list" -> sb.append("列出已注册 Curios 槽位来源:\n")
                .append("[CMD]/curios list[/CMD]\n");
            case "add" -> sb.append("给当前玩家增加 Curios 槽位。")
                .append("若玩家只说“加饰品栏位”，默认用通用槽 `curio`；戒指用 `ring`。\n")
                .append("[CMD]/curios add ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "set" -> sb.append("把当前玩家的某个 Curios 槽位数量设置为指定值:\n")
                .append("[CMD]/curios set ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "remove" -> sb.append("从当前玩家减少 Curios 槽位数量:\n")
                .append("[CMD]/curios remove ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "clear" -> sb.append("清空当前玩家某类 Curios 槽位物品:\n")
                .append("[CMD]/curios clear ").append(player).append(" ").append(slot).append("[/CMD]\n")
                .append("清空全部 Curios 槽位: [CMD]/curios clear ").append(player).append("[/CMD]\n");
            case "drop" -> sb.append("让当前玩家某类 Curios 槽位物品掉落到地上:\n")
                .append("[CMD]/curios drop ").append(player).append(" ").append(slot).append("[/CMD]\n")
                .append("掉落全部 Curios 槽位: [CMD]/curios drop ").append(player).append("[/CMD]\n");
            case "reset" -> sb.append("重置当前玩家 Curios 槽位修改:\n")
                .append("[CMD]/curios reset ").append(player).append("[/CMD]\n");
            case "replace" -> {
                String item = findItemId(d).orElse("<item_id>");
                int index = findIndexAfter(d, "index").orElse(0);
                sb.append("把物品放入/替换当前玩家 Curios 槽位。index 从 0 开始:\n")
                    .append("[CMD]/curios replace ").append(slot).append(" ").append(index)
                    .append(" ").append(player).append(" with ").append(item);
                if (amount > 1) sb.append(" ").append(amount);
                sb.append("[/CMD]\n");
            }
            default -> appendCuriosGeneralHelp(sb);
        }

        sb.append("槽位速查: ").append(String.join(", ", CURIOS_SLOTS)).append("\n")
            .append("常用别名: 饰品栏/饰品槽/饰品=curio, 戒指=ring, 护符=charm, 项链=necklace, 背部=back。\n")
            .append("下一步: 若玩家就是要执行，把上面的 [CMD] 原样输出；执行结果会进入 [TOOL_RESULT]，再判断是否完成。");
        return sb.toString();
    }

    private void appendCuriosGeneralHelp(StringBuilder sb) {
        sb.append("常用模板:\n")
            .append("  给自己加 1 个通用饰品槽: [CMD]/curios add curio @s 1[/CMD]\n")
            .append("  给自己加 1 个戒指槽: [CMD]/curios add ring @s 1[/CMD]\n")
            .append("  设置戒指槽为 2 个: [CMD]/curios set ring @s 2[/CMD]\n")
            .append("  减少 1 个戒指槽: [CMD]/curios remove ring @s 1[/CMD]\n")
            .append("  清空戒指槽物品: [CMD]/curios clear @s ring[/CMD]\n")
            .append("  列出槽位来源: [CMD]/curios list[/CMD]\n")
            .append("便携查询: [QUERY]commands add 饰品栏[/QUERY]、[QUERY]commands add ring[/QUERY]、[QUERY]commands set ring 2[/QUERY]、[QUERY]commands slots[/QUERY]\n");
        appendCuriosSlots(sb);
    }

    private void appendCuriosSlots(StringBuilder sb) {
        sb.append("内置槽位: ").append(String.join(", ", CURIOS_SLOTS)).append("\n")
            .append("中文别名: 饰品栏=curio, 饰品槽=curio, 饰品=curio, 戒指=ring, 护符=charm, 项链=necklace, 头部=head, 背部=back, 腰带=belt, 手镯=bracelet, 手部=hands, 脚部=feet, 身体=body。\n");
    }

    private String findCuriosAction(String detail) {
        if (detail == null || detail.isBlank()) return "help";
        if (containsAny(detail, "replace", "装备", "放入", "替换")) return "replace";
        if (containsAny(detail, "set", "设置", "设为")) return "set";
        if (containsAny(detail, "add", "添加", "增加", "扩容", "加")) return "add";
        if (containsAny(detail, "remove", "减少", "移除", "减")) return "remove";
        if (containsAny(detail, "clear", "清空")) return "clear";
        if (containsAny(detail, "drop", "掉落", "丢出")) return "drop";
        if (containsAny(detail, "reset", "重置", "恢复")) return "reset";
        if (containsAny(detail, "slot", "slots", "槽位", "栏位", "饰品栏", "饰品槽", "列表槽位")) return "slots";
        if (containsAny(detail, "list", "列表", "列出")) return "list";
        return "help";
    }

    private boolean isCuriosTopic(String text) {
        return text.equals("curios")
            || text.equals("curio")
            || text.equals("slots")
            || text.equals("slot")
            || text.equals("饰品")
            || text.equals("饰品栏")
            || text.equals("饰品槽")
            || text.equals("槽位")
            || text.equals("栏位");
    }

    private boolean looksLikeCuriosCommand(String text) {
        if (text == null || text.isBlank()) return false;
        return containsAny(text, "curios", "curio", "slot", "slots", "饰品", "饰品栏", "饰品槽", "槽位", "栏位",
                "ring", "戒指", "charm", "护符", "necklace", "项链", "back", "背部", "belt", "腰带")
            || containsAny(text, "add", "添加", "增加", "扩容", "加", "set", "设置", "remove", "减少", "clear", "清空", "reset", "重置");
    }

    private String findCuriosSlot(String detail) {
        if (detail != null) {
            for (var entry : CURIOS_SLOT_ALIASES.entrySet()) {
                if (detail.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                    return entry.getValue();
                }
            }
        }
        return "curio";
    }

    private boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private Optional<Integer> findFirstInt(String text) {
        Matcher matcher = Pattern.compile("\\b(\\d+)\\b").matcher(text == null ? "" : text);
        if (matcher.find()) return Optional.of(Integer.parseInt(matcher.group(1)));
        String t = text == null ? "" : text;
        if (containsAny(t, "十个", "十格", "十栏", "十槽")) return Optional.of(10);
        if (containsAny(t, "九个", "九格", "九栏", "九槽")) return Optional.of(9);
        if (containsAny(t, "八个", "八格", "八栏", "八槽")) return Optional.of(8);
        if (containsAny(t, "七个", "七格", "七栏", "七槽")) return Optional.of(7);
        if (containsAny(t, "六个", "六格", "六栏", "六槽")) return Optional.of(6);
        if (containsAny(t, "五个", "五格", "五栏", "五槽")) return Optional.of(5);
        if (containsAny(t, "四个", "四格", "四栏", "四槽")) return Optional.of(4);
        if (containsAny(t, "三个", "三格", "三栏", "三槽")) return Optional.of(3);
        if (containsAny(t, "两个", "两格", "两栏", "两槽", "二个", "二格", "二栏", "二槽")) return Optional.of(2);
        if (containsAny(t, "一个", "一格", "一栏", "一槽", "加个")) return Optional.of(1);
        return Optional.empty();
    }

    private Optional<Integer> findIndexAfter(String text, String key) {
        Matcher matcher = Pattern.compile(key + "\\s+(\\d+)").matcher(text == null ? "" : text);
        if (matcher.find()) return Optional.of(Integer.parseInt(matcher.group(1)));
        return Optional.empty();
    }

    private Optional<String> findItemId(String text) {
        Matcher matcher = Pattern.compile("\\b[a-z0-9_.-]+:[a-z0-9_./-]+\\b").matcher(text == null ? "" : text);
        if (matcher.find()) return Optional.of(matcher.group());
        return Optional.empty();
    }

    private String queryMod(String modId) {
        if (modId == null || modId.isBlank()) return "用法: mod <modid>  例: mod bloodmagic";
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        var mod = net.neoforged.fml.ModList.get().getMods().stream()
            .filter(m -> m.getModId().equalsIgnoreCase(normalized))
            .findFirst();
        String topicDesc = KnowledgeBase.describeTopic(normalized);
        if (mod.isPresent()) {
            var m = mod.get();
            return "Mod: " + m.getModId() + "\n版本: " + m.getVersion()
                + (topicDesc == null ? "" : "\n知识主题: " + topicDesc);
        }
        return topicDesc == null
            ? "未检测到 mod: " + normalized
            : "当前实例未检测到 mod: " + normalized + "\n但存在可读取的联动资料: " + topicDesc;
    }

    private String queryKnowledgeDoc(String topic) {
        if (topic == null || topic.isBlank()) return "用法: doc <主题>  例: doc bloodmagic";
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String content = KnowledgeBase.readKnowledge(normalized);
        if (content == null || content.isBlank()) {
            return "未找到知识主题: " + normalized + "\n可用主题: " + String.join(", ", KnowledgeBase.listKnowledgeBases());
        }
        if (content.length() > 4000) content = content.substring(0, 4000) + "\n...[截断]";
        String desc = KnowledgeBase.describeTopic(normalized);
        return (desc == null ? "" : "主题类型: " + desc + "\n") + content;
    }

    private String summarizeKnowledge(String topic, CommandSourceStack source) {
        if (topic == null || topic.isBlank()) return "用法: summarize <主题>  例: summarize bloodmagic";
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String summary = XxsxBuilder.getInstance().getSessionManager()
            .summarizeKnowledge(getPlayerName(source), normalized,
                Objects.requireNonNullElse(KnowledgeBase.readKnowledge(normalized), ""));
        if (summary == null || summary.isBlank()) {
            return "未找到可总结的知识主题: " + normalized;
        }
        String desc = KnowledgeBase.describeTopic(normalized);
        return "【知识总结: " + normalized + "】\n"
            + (desc == null ? "" : "主题类型: " + desc + "\n")
            + summary;
    }

    private String searchKnowledge(String topic, String keyword) {
        if (topic == null || topic.isBlank()) return "用法: search <主题> <关键词>  例: search ftbquests reward";
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String result = KnowledgeBase.searchKnowledge(normalized, keyword);
        if (result == null || result.isBlank()) {
            return "未找到知识主题: " + normalized;
        }
        return result;
    }

    private BlockPos parseCoords(String coords, CommandSourceStack source) {
        Vec3 p = source.getPosition();
        if (coords.isEmpty()) return BlockPos.containing(p);
        String[] s = coords.replace("~", "").split("[,\\s]+");
        try {
            double x = s.length > 0 ? (s[0].isEmpty() ? p.x : Double.parseDouble(s[0])) : p.x;
            double y = s.length > 1 ? (s[1].isEmpty() ? p.y : Double.parseDouble(s[1])) : p.y;
            double z = s.length > 2 ? (s[2].isEmpty() ? p.z : Double.parseDouble(s[2])) : p.z;
            return BlockPos.containing(x, y, z);
        } catch (NumberFormatException e) { return BlockPos.containing(p); }
    }

    /** 执行单条指令 */
    private boolean executeSingle(String cmd, CommandSourceStack source, ExecutionResult result) {
        String enhanced = cmd.replace("@s", getPlayerName(source));
        enhanced = enhanced.replace("@p", getPlayerName(source));

        String raw = enhanced.startsWith("/") ? enhanced.substring(1) : enhanced;
        if (raw.isEmpty()) return false;
        String normalized = normalizeModernGiveSyntax(raw);
        if (!normalized.equals(raw)) {
            LOGGER.info("[CommandExecutor] normalized modern give syntax: /{} -> /{}", raw, normalized);
        }

        if (isBlocked(normalized)) {
            result.commandsFailed++;
            result.feedback.append("\n§c[禁止] /").append(normalized);
            return false;
        }

        try {
            var commands = server.getCommands();
            var parse = commands.getDispatcher().parse(normalized, source);
            var reader = parse.getReader();
            boolean parsedFully = !reader.canRead();
            if (!parsedFully) {
                result.commandsFailed++;
                String hint = normalized.contains("{Enchantments:") || normalized.contains("{StoredEnchantments:")
                    ? "\n  提示: 26.2 新版本的 /give 复杂物品应优先使用方括号组件格式，例如 minecraft:diamond_sword[minecraft:enchantments={levels:{\"minecraft:sharpness\":5}}]"
                    : "";
                result.feedback.append("\n§c[失败] /").append(normalized)
                    .append("\n  错误: 未能解析完整指令，停在位置 ").append(reader.getCursor())
                    .append(" 附近: ").append(reader.getRemaining())
                    .append(hint);
                ModLogger.commandError(normalized, "parse_incomplete:" + reader.getRemaining());
                LOGGER.warn("[CommandExecutor] parse incomplete: {} | remaining={} | cursor={}", normalized, reader.getRemaining(), reader.getCursor());
                return false;
            }

            LOGGER.info("[CommandExecutor] performing command: /{}", normalized);
            commands.performCommand(parse, normalized);
            result.commandsDispatched++;
            result.feedback.append("\n§a[已发送] /").append(normalized);
            ModLogger.commandExec(normalized, 1);
            LOGGER.info("[CommandExecutor] performed command: /{}", normalized);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            if (normalized.contains("{Enchantments:") || normalized.contains("{StoredEnchantments:")) {
                msg += " | 提示: 26.2 新版本的 /give 复杂物品应优先使用方括号组件格式，例如 minecraft:diamond_sword[minecraft:enchantments={levels:{\"minecraft:sharpness\":5}}]";
            }
            result.commandsFailed++;
            result.feedback.append("\n§c[失败] /").append(normalized).append("\n  错误: ").append(msg);
            ModLogger.commandError(normalized, msg);
            LOGGER.error("[CommandExecutor] command failed: /{}", normalized, e);
            return false;
        }
    }

    private String normalizeModernGiveSyntax(String raw) {
        if (!raw.startsWith("give ")) return raw;
        String[] parts = raw.split("\\s+", 3);
        if (parts.length < 3) return raw;

        String itemPart = parts[2];
        int split = findItemSpecEnd(itemPart);
        String itemSpec = split >= 0 ? itemPart.substring(0, split).trim() : itemPart.trim();
        String tail = split >= 0 ? itemPart.substring(split).trim() : "";
        if (itemSpec.contains("[") || !itemSpec.contains("{") || !itemSpec.endsWith("}")) return raw;

        int brace = itemSpec.indexOf('{');
        if (brace <= 0) return raw;
        String itemId = itemSpec.substring(0, brace);
        String nbt = itemSpec.substring(brace + 1, itemSpec.length() - 1);

        List<String> components = new ArrayList<>();
        String customName = extractCustomName(nbt);
        if (customName != null && !customName.isBlank()) {
            components.add("minecraft:custom_name=" + customName);
        }

        String enchantments = extractEnchantmentsComponent(nbt, "Enchantments");
        if (enchantments == null) {
            enchantments = extractEnchantmentsComponent(nbt, "StoredEnchantments");
            if (enchantments != null) {
                components.add("minecraft:stored_enchantments={levels:{" + enchantments + "}}");
            }
        } else {
            components.add("minecraft:enchantments={levels:{" + enchantments + "}}");
        }

        if (components.isEmpty()) return raw;
        return "give " + parts[1] + " " + itemId + "[" + String.join(",", components) + "]"
            + (tail.isEmpty() ? "" : " " + tail);
    }

    private int findItemSpecEnd(String text) {
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble && !isEscaped(text, i)) inSingle = !inSingle;
            else if (c == '"' && !inSingle && !isEscaped(text, i)) inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                if (c == '{') braceDepth++;
                else if (c == '}') braceDepth--;
                else if (c == '[') bracketDepth++;
                else if (c == ']') bracketDepth--;
                else if (Character.isWhitespace(c) && braceDepth == 0 && bracketDepth == 0) return i;
            }
        }
        return -1;
    }

    private boolean isEscaped(String text, int index) {
        int slashCount = 0;
        for (int i = index - 1; i >= 0 && text.charAt(i) == '\\'; i--) slashCount++;
        return (slashCount % 2) == 1;
    }

    private String extractCustomName(String nbt) {
        int displayIdx = nbt.indexOf("display");
        if (displayIdx < 0) return null;
        Matcher matcher = CUSTOM_NAME_PATTERN.matcher(nbt.substring(displayIdx));
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractEnchantmentsComponent(String nbt, String key) {
        int keyIdx = nbt.indexOf(key);
        if (keyIdx < 0) return null;
        int listStart = nbt.indexOf('[', keyIdx);
        if (listStart < 0) return null;
        int listEnd = findMatching(nbt, listStart, '[', ']');
        if (listEnd < 0) return null;
        String list = nbt.substring(listStart + 1, listEnd);
        Matcher matcher = ENCHANT_ENTRY_PATTERN.matcher(list);
        List<String> pairs = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!id.contains(":")) id = "minecraft:" + id;
            pairs.add("\"" + id + "\":" + matcher.group(2));
        }
        return pairs.isEmpty() ? null : String.join(",", pairs);
    }

    private int findMatching(String text, int openIndex, char open, char close) {
        int depth = 0;
        boolean inSingle = false;
        boolean inDouble = false;
        for (int i = openIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\'' && !inDouble && !isEscaped(text, i)) inSingle = !inSingle;
            else if (c == '"' && !inSingle && !isEscaped(text, i)) inDouble = !inDouble;
            else if (!inSingle && !inDouble) {
                if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return i;
                }
            }
        }
        return -1;
    }

    /** 从游戏注册表查询物品信息 */
    private String queryItem(String itemId) {
        if (itemId.isEmpty()) return "用法: item <物品ID>  例: item minecraft:diamond";
        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(toResourceLocation(itemId));
        if (itemHolder.isEmpty()) return "物品未找到: " + itemId;
        var item = itemHolder.get().value();
        if (item == net.minecraft.world.item.Items.AIR) return "物品未找到: " + itemId;
        var stack = new net.minecraft.world.item.ItemStack(item);
        StringBuilder sb = new StringBuilder();
        sb.append("物品: ").append(itemId).append("\n");
        sb.append("显示名: ").append(stack.getDisplayName().getString()).append("\n");
        sb.append("类型: ").append(item.getClass().getSimpleName()).append("\n");
        // 工具提示（含属性等）
        var tip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        if (!tip.isEmpty()) {
            sb.append("提示:\n");
            for (var t : tip) {
                sb.append("  ").append(t.getString()).append("\n");
            }
        }
        // 最大堆叠
        sb.append("最大堆叠: ").append(stack.getMaxStackSize());
        // 食用信息
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            sb.append("\n食物: ").append(food.nutrition()).append("饱食度 +")
              .append(String.format("%.1f", food.saturation())).append("饱和度");
        }
        return sb.toString();
    }

    /** AI 查询 PMX 模型元数据（顶点/面/包围盒/材质），据此决定构建参数 */
    private String queryPMX(String filePath) {
        if (filePath.isEmpty()) return "用法: pmx <文件路径>";
        // 去引号
        filePath = filePath.replace("\"", "").replace("\"", "").replace("\"", "").trim();
        java.nio.file.Path p = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(p)) return "文件不存在: " + filePath;
        try {
            var parser = new com.xxsx.builder.voxel.PMXParser();
            var model = parser.parse(p);
            // 计算包围盒
            float minX=Float.MAX_VALUE,minY=Float.MAX_VALUE,minZ=Float.MAX_VALUE;
            float maxX=-Float.MAX_VALUE,maxY=-Float.MAX_VALUE,maxZ=-Float.MAX_VALUE;
            for (var v : model.vertices) {
                if (v.x<minX) minX=v.x; if (v.y<minY) minY=v.y; if (v.z<minZ) minZ=v.z;
                if (v.x>maxX) maxX=v.x; if (v.y>maxY) maxY=v.y; if (v.z>maxZ) maxZ=v.z;
            }
            float sx=maxX-minX, sy=maxY-minY, sz=maxZ-minZ;
            float maxDim = Math.max(sx, Math.max(sy, sz));
            int suggestedScale = (int)(50000.0 / Math.sqrt(model.vertices.size()));
            suggestedScale = Math.max(30, Math.min(400, suggestedScale));

            return String.format(
                "PMX模型: %s\n  顶点: %d  面三角形: %d  材质: %d  纹理: %d\n" +
                "  包围盒: %.2f x %.2f x %.2f (最大维: %.2f)\n" +
                "  建议比例: %d (范围 30-400, 越大越精细)\n" +
                "  估算方块: 约 %d 块 (比例=%d时)",
                p.getFileName(), model.vertices.size(), model.faces.size(),
                model.materials.size(), 0,
                sx, sy, sz, maxDim,
                suggestedScale,
                (int)(sx*suggestedScale/maxDim * sy*suggestedScale/maxDim * sz*suggestedScale/maxDim * 0.3),
                suggestedScale);
        } catch (Exception e) {
            return "PMX 解析失败: " + e.getMessage();
        }
    }

    private String queryRecipes(String itemId) {
        return queryRecipes(itemId, true);
    }

    private String queryRecipes(String itemId, boolean includeJeiHint) {
        if (itemId.isEmpty()) return "用法: recipe <物品ID>";
        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(toResourceLocation(itemId));
        if (itemHolder.isEmpty()) return "物品未找到: " + itemId;
        var item = itemHolder.get().value();
        var level = server.overworld();
        var displayContext = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        StringBuilder sb = new StringBuilder();
        sb.append(itemId).append(" 的合成/制作方式:\n");
        int found = 0;

        for (var holder : server.getRecipeManager().getRecipes()) {
            try {
                var recipe = holder.value();
                List<net.minecraft.world.item.ItemStack> results = recipe.display().stream()
                    .flatMap(display -> display.result().resolveForStacks(displayContext).stream())
                    .filter(stack -> !stack.isEmpty())
                    .collect(Collectors.toList());
                if (results.stream().noneMatch(stack -> stack.getItem() == item)) continue;

                found++;
                if (found > 12) {
                    sb.append("  ...(还有更多配方，建议在 JEI 里继续筛选)\n");
                    break;
                }

                sb.append("  [").append(recipe.getType()).append("] ")
                    .append(summarizeIngredients(recipe))
                    .append(" -> ")
                    .append(summarizeResults(results, item))
                    .append("\n");
            } catch (Exception ignored) {
            }
        }

        if (found == 0) {
            sb.append("  未找到服务端配方（可能是 JEI 插件/机器配方/隐藏配方/运行时生成配方）\n");
        }
        if (includeJeiHint && isModLoaded("jei")) {
            sb.append("  JEI 已安装：图形界面中可对物品按 R 查合成、按 U 查用途。");
        }
        return sb.toString();
    }

    private String queryJei(String itemId) {
        String jeiInfo = isModLoaded("jei")
            ? "JEI 已安装，可在客户端物品上按 R 查合成、按 U 查用途。"
            : "未检测到 JEI；当前只能使用服务端配方索引。";
        if (itemId == null || itemId.isBlank()) {
            return jeiInfo + "\n用法: jei <物品ID>  例: jei minecraft:diamond_pickaxe";
        }
        return jeiInfo + "\n" + queryRecipes(itemId, false);
    }

    private String summarizeIngredients(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        var ingredients = recipe.placementInfo().ingredients();
        if (ingredients.isEmpty()) return "材料未知";
        String summary = ingredients.stream()
            .map(this::summarizeIngredient)
            .filter(s -> !s.isBlank())
            .limit(9)
            .collect(Collectors.joining(" + "));
        if (ingredients.size() > 9) summary += " + ...";
        return summary.isBlank() ? "材料未知" : summary;
    }

    private String summarizeIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return "空";
        List<String> names = ingredient.items()
            .limit(4)
            .map(holder -> new net.minecraft.world.item.ItemStack(holder).getDisplayName().getString())
            .distinct()
            .collect(Collectors.toList());
        if (names.isEmpty()) return "自定义材料";
        return names.size() == 1 ? names.get(0) : "(" + String.join("/", names) + (names.size() >= 4 ? "/..." : "") + ")";
    }

    private String summarizeResults(List<net.minecraft.world.item.ItemStack> results, net.minecraft.world.item.Item targetItem) {
        String summary = results.stream()
            .filter(stack -> stack.getItem() == targetItem)
            .map(stack -> stack.getCount() + "x " + stack.getDisplayName().getString())
            .distinct()
            .limit(4)
            .collect(Collectors.joining(" / "));
        return summary.isBlank() ? "目标物品" : summary;
    }

    private boolean isModLoaded(String modId) {
        return loadedModVersion(modId).isPresent();
    }

    private Optional<String> loadedModVersion(String modId) {
        return net.neoforged.fml.ModList.get().getMods().stream()
            .filter(m -> m.getModId().equalsIgnoreCase(modId))
            .map(m -> m.getVersion().toString())
            .findFirst();
    }

    private boolean isBlocked(String cmd) {
        String normalized = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        for (String prefix : blacklist) {
            if (normalized.startsWith(prefix.trim())) return true;
        }
        return false;
    }

    private static Identifier toResourceLocation(String id) {
        int ci = id.indexOf(':');
        return ci >= 0
            ? Identifier.fromNamespaceAndPath(id.substring(0, ci), id.substring(ci + 1))
            : Identifier.withDefaultNamespace(id);
    }
}
