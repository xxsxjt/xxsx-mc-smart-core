package com.xxsx.builder.ai;

import com.xxsx.builder.XxsxBuilder;
import com.xxsx.builder.config.BuilderConfig;
import com.xxsx.builder.config.KnowledgeBase;
import com.xxsx.builder.config.ModLogger;
import com.xxsx.builder.config.SmartCoreText;
import com.xxsx.builder.modelimport.ModelImportBridge;
import com.mojang.brigadier.context.CommandContextBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import java.util.function.Function;

public class CommandExecutor {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(CommandExecutor.class);
    private static final Pattern CMD_PATTERN = Pattern.compile("\\[CMD\\](.*?)\\[/CMD\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_LINE = Pattern.compile("^\\s*/(\\w+.*)$", Pattern.MULTILINE);
    private static final Pattern QUERY_PATTERN = Pattern.compile("\\[QUERY\\](.*?)\\[/QUERY\\]", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTINUE_PATTERN = Pattern.compile("\\[CONTINUE\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern ENCHANT_ENTRY_PATTERN = Pattern.compile("\\{\\s*id\\s*:\\s*\"([^\"]+)\"\\s*,\\s*lvl\\s*:\\s*(\\d+)\\s*\\}");
    private static final Pattern CUSTOM_NAME_PATTERN = Pattern.compile("Name\\s*:\\s*('(?:[^'\\\\]|\\\\.)*'|\"(?:[^\"\\\\]|\\\\.)*\")");
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

    private static String queryText(String key, Object... args) {
        return SmartCoreText.text("query.xxsx_mc_smart_core." + key, args);
    }

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
        public boolean confirmationRequired;
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
        boolean matchedCmdBlock = false;

        Matcher m = CMD_PATTERN.matcher(afterQuery);
        while (m.find()) {
            matchedCmdBlock = true;
            String cmd = m.group(1).trim();
            if (cmd.isEmpty()) continue;
            result.commandsFound++;
            LOGGER.info("[CommandExecutor] matched [CMD] block: {}", cmd.replace("\n", "\\n"));
            executeOrQueue(cmd, source, result);
            if (result.confirmationRequired) break;
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
                    executeOrQueue(loose, source, result);
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
                executeOrQueue(cmd, source, result);
                if (result.confirmationRequired) break;
            }
        }

        if (result.commandsFound == 0 && result.feedback.length() == 0) {
            LOGGER.warn("[CommandExecutor] no executable commands found in response: {}", aiResponse.replace("\n", "\\n"));
        }
        LOGGER.info("[CommandExecutor] summary found={} dispatched={} failed={} queries={} continue={}",
            result.commandsFound, result.commandsDispatched, result.commandsFailed, result.queriesHandled, result.continueRequested);
        return result;
    }

    /** 处理 [QUERY] 标签：读文件、查游戏状态和运行时注册表。 */
    private String processQueries(String text, CommandSourceStack source, ExecutionResult result) {
        Matcher m = QUERY_PATTERN.matcher(text);
        while (m.find()) {
            String q = m.group(1).trim();
            String response = doQuery(q, source);
            if (response != null && !response.isEmpty()) {
                result.queriesHandled++;
                result.feedback.append("\n").append(queryText("result_header")).append(response);
            }
        }
        return m.replaceAll(""); // 去掉 [QUERY] 块，避免干扰
    }

    /** 执行查询 */
    private String doQuery(String query, CommandSourceStack source) {
        String[] parts = query.split("\\s+", 3);
        if (parts.length == 0) return queryText("invalid");

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
                case "registry", "registries", "注册表" -> {
                    return queryRegistry(arg, extra);
                }
                case "items", "物品" -> {
                    return queryRegistry("items", (arg + " " + extra).trim());
                }
                case "blocks", "方块" -> {
                    return queryRegistry("blocks", (arg + " " + extra).trim());
                }
                case "entity", "entities", "entitytype", "entitytypes", "实体", "生物" -> {
                    return queryRegistry("entities", (arg + " " + extra).trim());
                }
                case "effect", "effects", "buff", "buffs", "状态效果", "效果", "药水效果" -> {
                    return queryRegistry("effects", (arg + " " + extra).trim());
                }
                case "potion", "potions", "药水", "药剂" -> {
                    return queryRegistry("potions", (arg + " " + extra).trim());
                }
                case "enchantment", "enchantments", "附魔" -> {
                    return queryRegistry("enchantments", (arg + " " + extra).trim());
                }
                case "structure", "structures", "结构" -> {
                    return queryRegistry("structures", (arg + " " + extra).trim());
                }
                case "biome", "biomes", "生物群系" -> {
                    return queryRegistry("biomes", (arg + " " + extra).trim());
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
                    return queryText("help");
                }
                default -> {
                    return queryText("unknown_type", type);
                }
            }
        } catch (Exception e) {
            return queryText("failed", e.getMessage());
        }
    }

    private String readFile(String path) {
        // 安全检查：只允许读 config/ 和 logs/ 下的文件
        Path p = Paths.get(path);
        if (!p.normalize().toString().startsWith("config") && !p.normalize().toString().startsWith("logs")) {
            return queryText("file.security");
        }
        if (!Files.isRegularFile(p)) return queryText("file.missing", path);
        try {
            String content = Files.readString(p);
            if (content.length() > 5000) content = content.substring(0, 5000) + "\n" + queryText("truncated");
            return queryText("file.content", path) + "\n" + content;
        } catch (IOException e) { return queryText("file.read_failed", e.getMessage()); }
    }

    private String listDir(String path) {
        Path p = Paths.get(path.isEmpty() ? "." : path);
        if (!Files.isDirectory(p)) return queryText("directory.missing", path);
        try {
            return Files.list(p).limit(50)
                .map(f -> (Files.isDirectory(f) ? "D " : "F ") + f.getFileName())
                .collect(Collectors.joining("\n"));
        } catch (IOException e) { return queryText("directory.list_failed", e.getMessage()); }
    }

    private String queryBlock(CommandSourceStack source, String coords) {
        BlockPos pos = parseCoords(coords, source);
        ServerLevel level = source.getLevel();
        if (level == null) return queryText("no_level");
        BlockState state = level.getBlockState(pos);
        return queryText("block", pos.getX(), pos.getY(), pos.getZ(),
            state.getBlock().builtInRegistryHolder().key().identifier());
    }

    private String queryNearby(CommandSourceStack source, String radius) {
        double r = Double.parseDouble(radius);
        Vec3 center = source.getPosition();
        ServerLevel level = source.getLevel();
        if (level == null) return queryText("no_level");
        var entities = level.getEntities((Entity)null,
            new net.minecraft.world.phys.AABB(center.x - r, center.y - r, center.z - r,
                center.x + r, center.y + r, center.z + r),
            e -> true);
        StringBuilder sb = new StringBuilder(queryText("nearby.header", entities.size(), r)).append("\n");
        entities.stream().limit(20).forEach(e ->
            sb.append(String.format("  %s [%s] (%.1f, %.1f, %.1f)\n",
                e.getName().getString(), e.getType().toShortString(), e.getX(), e.getY(), e.getZ())));
        if (entities.size() > 20) sb.append(queryText("nearby.more", entities.size()));
        return sb.toString();
    }

    private String queryPlayer(CommandSourceStack source) {
        Entity e = source.getEntity();
        if (!(e instanceof ServerPlayer p)) return queryText("not_player");
        return queryText("player", p.getName().getString(), p.level().dimension().identifier(),
            String.format("%.1f", p.getX()), String.format("%.1f", p.getY()), String.format("%.1f", p.getZ()),
            String.format("%.1f", p.getHealth()), p.getMaxHealth(),
            p.experienceLevel,
            p.getMainHandItem().isEmpty() ? queryText("empty") : p.getMainHandItem().getDisplayName().getString(),
            p.gameMode.getGameModeForPlayer().getName());
    }

    private String queryGamerule(CommandSourceStack source, String rule) {
        return queryText("gamerule", rule.isEmpty() ? "" : rule);
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
        return queryText("system", cores, os, totalMem, maxMem, freeMem, bps, bpt);
    }

    private String queryWorld(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        if (level == null) return queryText("no_level");
        long dayTime = level.getLevelData().getGameTime() % 24000;
        return queryText("world", level.dimension().identifier(), dayTime,
            dayTime < 13000 ? queryText("world.day") : queryText("world.night"),
            level.isRaining() ? (level.isThundering() ? queryText("world.thunder") : queryText("world.rain"))
                : queryText("world.clear"), level.getDifficulty().name());
    }

    private String queryMods() {
        return net.neoforged.fml.ModList.get().getMods().stream()
            .map(m -> m.getModId() + " " + m.getVersion())
            .limit(50).collect(Collectors.joining("\n", queryText("mods.header") + "\n", ""));
    }

    private String queryIntegrations(String filter) {
        String needle = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(queryText("integrations.header")).append("\n");
        int shown = 0;
        int loaded = 0;
        for (IntegrationCatalog.IntegrationDef def : IntegrationCatalog.integrations()) {
            if (!needle.isEmpty() && !def.matches(needle)) {
                continue;
            }
            Optional<String> version = loadedModVersion(def.modId());
            if (version.isPresent()) loaded++;
            shown++;
            sb.append(version.isPresent() ? queryText("status.installed") : queryText("status.not_detected"))
                .append(def.modId()).append(" - ").append(def.displayName());
            version.ifPresent(v -> sb.append(" ").append(v));
            sb.append("\n    ").append(queryText("topic", def.topic()))
                .append(" | ").append(def.displayRole())
                .append("\n");
        }
        if (shown == 0) {
            return queryText("integrations.no_match", filter);
        }
        sb.append(queryText("integrations.summary", loaded, shown));
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
        List<IntegrationCatalog.ActionDef> actions = IntegrationCatalog.findActions(merged);
        if (!actions.isEmpty()) {
            return describeCatalogActions(actions);
        }
        return queryText("command_library.not_found", normalized, normalized, normalized);
    }

    private String commandLibraryIndex() {
        Map<String, List<IntegrationCatalog.ActionDef>> categories = new LinkedHashMap<>();
        for (IntegrationCatalog.ActionDef action : IntegrationCatalog.actions()) {
            categories.computeIfAbsent(action.category(), ignored -> new ArrayList<>()).add(action);
        }
        StringBuilder sb = new StringBuilder(queryText("command_library.header")).append("\n");
        for (var entry : categories.entrySet()) {
            IntegrationCatalog.ActionDef first = entry.getValue().get(0);
            String labels = entry.getValue().stream().map(IntegrationCatalog.ActionDef::displayLabel)
                .distinct().limit(3).collect(Collectors.joining(" / "));
            sb.append("  ").append(first.displayCategory()).append(" - ")
                .append(isModLoaded(first.modId()) ? queryText("status.installed") : queryText("status.not_detected"))
                .append(first.modId()).append(": ").append(labels)
                .append("; [QUERY]commands ").append(first.modId()).append("[/QUERY]\n");
        }
        sb.append(queryText("command_library.footer"));
        return sb.toString();
    }

    private String describeCatalogActions(List<IntegrationCatalog.ActionDef> actions) {
        StringBuilder sb = new StringBuilder(queryText("action_catalog.header")).append("\n");
        for (IntegrationCatalog.ActionDef action : actions) {
            boolean modLoaded = isModLoaded(action.modId());
            sb.append("  ").append(modLoaded ? queryText("status.installed") : queryText("status.not_detected"))
                .append(action.displayLabel()).append(" (" ).append(action.modId()).append(")\n");
            if (action.commandTemplate() != null) {
                boolean commandRegistered = isCommandRegistered(action.commandTemplate());
                sb.append("    ").append(commandRegistered ? queryText("command_registered") : queryText("command_not_registered"))
                    .append("; ").append(action.risk().description()).append("\n")
                    .append("    [CMD]/").append(action.commandTemplate()).append("[/CMD]\n");
            } else {
                sb.append("    ").append(action.displayQueryHint()).append("\n");
            }
        }
        sb.append(queryText("action_catalog.footer"));
        return sb.toString();
    }

    private boolean isCommandRegistered(String commandTemplate) {
        String root = commandTemplate.trim().split("\\s+", 2)[0];
        return server.getCommands().getDispatcher().getRoot().getChild(root) != null;
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
        sb.append(queryText("curios.header", queryText("curios.action." + action), slot, amount,
            modLoaded ? queryText("curios.mod_loaded") : queryText("curios.mod_missing"),
            commandLoaded ? queryText("curios.command_registered") : queryText("curios.command_missing")));

        if (d.isEmpty() || action.equals("help")) {
            appendCuriosGeneralHelp(sb);
            return sb.toString();
        }
        if (action.equals("slots")) {
            appendCuriosSlots(sb);
            return sb.toString();
        }

        switch (action) {
            case "list" -> sb.append(queryText("curios.case.list"))
                .append("[CMD]/curios list[/CMD]\n");
            case "add" -> sb.append(queryText("curios.case.add"))
                .append("[CMD]/curios add ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "set" -> sb.append(queryText("curios.case.set"))
                .append("[CMD]/curios set ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "remove" -> sb.append(queryText("curios.case.remove"))
                .append("[CMD]/curios remove ").append(slot).append(" ").append(player).append(" ").append(amount).append("[/CMD]\n");
            case "clear" -> sb.append(queryText("curios.case.clear"))
                .append("[CMD]/curios clear ").append(player).append(" ").append(slot).append("[/CMD]\n")
                .append(queryText("curios.case.clear_all")).append("[CMD]/curios clear ").append(player).append("[/CMD]\n");
            case "drop" -> sb.append(queryText("curios.case.drop"))
                .append("[CMD]/curios drop ").append(player).append(" ").append(slot).append("[/CMD]\n")
                .append(queryText("curios.case.drop_all")).append("[CMD]/curios drop ").append(player).append("[/CMD]\n");
            case "reset" -> sb.append(queryText("curios.case.reset"))
                .append("[CMD]/curios reset ").append(player).append("[/CMD]\n");
            case "replace" -> {
                String item = findItemId(d).orElse("<item_id>");
                int index = findIndexAfter(d, "index").orElse(0);
                sb.append(queryText("curios.case.replace"))
                    .append("[CMD]/curios replace ").append(slot).append(" ").append(index)
                    .append(" ").append(player).append(" with ").append(item);
                if (amount > 1) sb.append(" ").append(amount);
                sb.append("[/CMD]\n");
            }
            default -> appendCuriosGeneralHelp(sb);
        }

        sb.append(queryText("curios.footer", String.join(", ", CURIOS_SLOTS)));
        return sb.toString();
    }

    private void appendCuriosGeneralHelp(StringBuilder sb) {
        sb.append(queryText("curios.general_help"));
        appendCuriosSlots(sb);
    }

    private void appendCuriosSlots(StringBuilder sb) {
        sb.append(queryText("curios.slots", String.join(", ", CURIOS_SLOTS)));
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
        if (modId == null || modId.isBlank()) return queryText("mod.usage");
        String normalized = modId.trim().toLowerCase(Locale.ROOT);
        var mod = net.neoforged.fml.ModList.get().getMods().stream()
            .filter(m -> m.getModId().equalsIgnoreCase(normalized))
            .findFirst();
        String topicDesc = KnowledgeBase.describeTopic(normalized);
        if (mod.isPresent()) {
            var m = mod.get();
            return queryText("mod.installed", m.getModId(), m.getVersion(),
                topicDesc == null ? "" : "\n" + queryText("topic", topicDesc));
        }
        return topicDesc == null
            ? queryText("mod.not_detected", normalized)
            : queryText("mod.not_detected_with_docs", normalized, topicDesc);
    }

    private String queryKnowledgeDoc(String topic) {
        if (topic == null || topic.isBlank()) return queryText("doc.usage");
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String content = KnowledgeBase.readKnowledge(normalized);
        if (content == null || content.isBlank()) {
            return queryText("doc.not_found", normalized, String.join(", ", KnowledgeBase.listKnowledgeBases()));
        }
        if (content.length() > 4000) content = content.substring(0, 4000) + "\n" + queryText("truncated");
        String desc = KnowledgeBase.describeTopic(normalized);
        return (desc == null ? "" : queryText("topic", desc) + "\n") + content;
    }

    private String summarizeKnowledge(String topic, CommandSourceStack source) {
        if (topic == null || topic.isBlank()) return queryText("summarize.usage");
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String summary = XxsxBuilder.getInstance().getSessionManager()
            .summarizeKnowledge(getPlayerName(source), normalized,
                Objects.requireNonNullElse(KnowledgeBase.readKnowledge(normalized), ""),
                SmartCoreText.system("knowledge_summary_instruction").getString());
        if (summary == null || summary.isBlank()) {
            return queryText("summarize.not_found", normalized);
        }
        String desc = KnowledgeBase.describeTopic(normalized);
        return queryText("summarize.header", normalized) + "\n"
            + (desc == null ? "" : queryText("topic", desc) + "\n")
            + summary;
    }

    private String searchKnowledge(String topic, String keyword) {
        if (topic == null || topic.isBlank()) return queryText("search.usage");
        String normalized = topic.trim().toLowerCase(Locale.ROOT);
        String result = KnowledgeBase.searchKnowledge(normalized, keyword);
        if (result == null || result.isBlank()) {
            return queryText("search.not_found", normalized);
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

    private boolean executeOrQueue(String cmd, CommandSourceStack source, ExecutionResult result) {
        String normalized = normalizeCommand(cmd, source);
        if (normalized.isEmpty() || isBlocked(normalized)
            || !config.confirmStateChanges
            || IntegrationCatalog.riskForCommand(normalized) == IntegrationCatalog.CommandRisk.SAFE) {
            return executeSingle(normalized, source, result);
        }
        requestCommandConfirmation(normalized, source, result);
        return false;
    }

    private String normalizeCommand(String cmd, CommandSourceStack source) {
        String enhanced = cmd.replace("@s", getPlayerName(source));
        enhanced = enhanced.replace("@p", getPlayerName(source));
        String raw = enhanced.startsWith("/") ? enhanced.substring(1) : enhanced;
        return raw.isEmpty() ? "" : normalizeModernGiveSyntax(raw);
    }

    private void requestCommandConfirmation(String normalized, CommandSourceStack source, ExecutionResult result) {
        ChatSession session = XxsxBuilder.getInstance().getSessionManager().getSession(getPlayerName(source));
        if (session.isAwaitingConfirm()) {
            result.commandsFailed++;
            result.confirmationRequired = true;
            result.feedback.append("\n").append(queryText("confirmation.already_pending", session.getPendingAction()));
            return;
        }

        String description = queryText("confirmation.action", normalized);
        session.setConfirmAction(description, () -> {
            ExecutionResult confirmed = new ExecutionResult();
            executeSingle(normalized, source, confirmed);
            String feedback = confirmed.feedbackText();
            if (feedback.isBlank()) {
                feedback = queryText("confirmation.executed", normalized);
            }
            String confirmedFeedback = feedback;
            source.sendSuccess(() -> Component.literal(confirmedFeedback), false);
            XxsxBuilder.getInstance().getSessionManager().addSystemMessage(session.playerName,
                queryText("confirmation.system_executed", confirmedFeedback));
        });
        result.confirmationRequired = true;
        result.feedback.append("\n").append(queryText("confirmation.required", normalized));
    }

    /** 执行单条已获准的指令 */
    private boolean executeSingle(String cmd, CommandSourceStack source, ExecutionResult result) {
        String normalized = normalizeCommand(cmd, source);
        if (normalized.isEmpty()) return false;

        if (isBlocked(normalized)) {
            result.commandsFailed++;
            result.feedback.append("\n").append(queryText("command.blocked", normalized));
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
                    ? "\n" + queryText("give.components_hint")
                    : "";
                result.feedback.append("\n").append(queryText("command.parse_failed", normalized,
                    reader.getCursor(), reader.getRemaining(), hint));
                ModLogger.commandError(normalized, "parse_incomplete:" + reader.getRemaining());
                LOGGER.warn("[CommandExecutor] parse incomplete: {} | remaining={} | cursor={}", normalized, reader.getRemaining(), reader.getCursor());
                return false;
            }

            LOGGER.info("[CommandExecutor] performing command: /{}", normalized);
            commands.performCommand(parse, normalized);
            result.commandsDispatched++;
            result.feedback.append("\n").append(queryText("command.sent", normalized));
            ModLogger.commandExec(normalized, 1);
            LOGGER.info("[CommandExecutor] performed command: /{}", normalized);
            return true;
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = e.getClass().getSimpleName();
            if (normalized.contains("{Enchantments:") || normalized.contains("{StoredEnchantments:")) {
                msg += " | " + queryText("give.components_hint");
            }
            result.commandsFailed++;
            result.feedback.append("\n").append(queryText("command.failed", normalized, msg));
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

    /**
     * 枚举当前实例中的基础注册表。ID 发现必须使用这里的实时结果，
     * 而不是从内置文档或源码猜测某个模组的注册名。
     */
    private String queryRegistry(String requestedKind, String filter) {
        if (requestedKind == null || requestedKind.isBlank()) {
            return queryText("registry.usage", queryText("registry.kinds"));
        }

        String kind = normalizeRegistryKind(requestedKind);
        if (kind == null) {
            return queryText("registry.unknown_kind", requestedKind, queryText("registry.kinds"));
        }

        return switch (kind) {
            case "items" -> listRuntimeRegistry(kind, filter, BuiltInRegistries.ITEM.keySet(), this::itemDisplayName);
            case "blocks" -> listRuntimeRegistry(kind, filter, BuiltInRegistries.BLOCK.keySet(), this::blockDisplayName);
            case "entities" -> listRuntimeRegistry(kind, filter, BuiltInRegistries.ENTITY_TYPE.keySet(), this::entityDisplayName);
            case "effects" -> listRuntimeRegistry(kind, filter, BuiltInRegistries.MOB_EFFECT.keySet(), this::effectDisplayName);
            case "potions" -> listRuntimeRegistry(kind, filter, BuiltInRegistries.POTION.keySet(), this::potionDisplayName);
            case "enchantments" -> listRuntimeRegistry(kind, filter,
                server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).keySet(), this::enchantmentDisplayName);
            case "structures" -> listRuntimeRegistry(kind, filter,
                server.registryAccess().lookupOrThrow(Registries.STRUCTURE).keySet(), this::structureDisplayName);
            case "biomes" -> listRuntimeRegistry(kind, filter,
                server.registryAccess().lookupOrThrow(Registries.BIOME).keySet(), this::biomeDisplayName);
            default -> throw new IllegalStateException("Unhandled registry kind: " + kind);
        };
    }

    private String normalizeRegistryKind(String requestedKind) {
        return switch (requestedKind.trim().toLowerCase(Locale.ROOT)) {
            case "item", "items", "物品" -> "items";
            case "block", "blocks", "方块" -> "blocks";
            case "entity", "entities", "entitytype", "entitytypes", "实体", "生物" -> "entities";
            case "effect", "effects", "buff", "buffs", "状态效果", "效果", "药水效果" -> "effects";
            case "potion", "potions", "药水", "药剂" -> "potions";
            case "enchantment", "enchantments", "附魔" -> "enchantments";
            case "structure", "structures", "结构" -> "structures";
            case "biome", "biomes", "生物群系" -> "biomes";
            default -> null;
        };
    }

    private String listRuntimeRegistry(String kind, String filter, Collection<Identifier> entries,
                                       Function<Identifier, String> displayNameResolver) {
        String needle = normalizeRegistryText(filter);
        String displayFilter = needle.isEmpty() ? queryText("registry.all") : filter.trim();
        List<Identifier> sortedEntries = entries.stream()
            .sorted(Comparator.comparing(Identifier::toString))
            .toList();
        List<Identifier> matches = new ArrayList<>();
        Map<Identifier, String> displayNames = new HashMap<>();
        for (Identifier id : sortedEntries) {
            String idText = normalizeRegistryText(id.toString());
            if (needle.isEmpty() || idText.contains(needle)) {
                matches.add(id);
                continue;
            }

            String displayName = resolveRegistryDisplayName(displayNameResolver, id);
            if (normalizeRegistryText(displayName).contains(needle)) {
                matches.add(id);
                displayNames.put(id, displayName);
            }
        }
        String kindName = queryText("registry.kind." + kind);
        if (matches.isEmpty()) {
            return queryText("registry.none", kindName, displayFilter);
        }

        int limit = 128;
        StringBuilder result = new StringBuilder(queryText("registry.header",
            kindName, displayFilter, matches.size(), entries.size()));
        for (int i = 0; i < Math.min(matches.size(), limit); i++) {
            Identifier id = matches.get(i);
            String displayName = displayNames.computeIfAbsent(id,
                entryId -> resolveRegistryDisplayName(displayNameResolver, entryId));
            if (displayName.isBlank()) {
                displayName = queryText("registry.no_name");
            }
            result.append("\n").append(queryText("registry.entry", displayName, id));
        }
        if (matches.size() > limit) {
            result.append("\n").append(queryText("registry.more", matches.size() - limit));
        }
        return result.append("\n").append(queryText("registry.hint." + kind)).toString();
    }

    private String normalizeRegistryText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private String resolveRegistryDisplayName(Function<Identifier, String> resolver, Identifier id) {
        try {
            return Optional.ofNullable(resolver.apply(id)).orElse("").trim();
        } catch (RuntimeException e) {
            LOGGER.debug("Unable to resolve runtime registry display name for {}", id, e);
            return "";
        }
    }

    private String itemDisplayName(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id)
            .map(item -> item.getName(item.getDefaultInstance()).getString())
            .orElse("");
    }

    private String blockDisplayName(Identifier id) {
        return BuiltInRegistries.BLOCK.getOptional(id)
            .map(block -> block.getName().getString())
            .orElse("");
    }

    private String entityDisplayName(Identifier id) {
        return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
            .map(entityType -> entityType.getDescription().getString())
            .orElse("");
    }

    private String effectDisplayName(Identifier id) {
        return BuiltInRegistries.MOB_EFFECT.getOptional(id)
            .map(effect -> effect.getDisplayName().getString())
            .orElse("");
    }

    private String potionDisplayName(Identifier id) {
        return BuiltInRegistries.POTION.get(id)
            .map(holder -> net.minecraft.world.item.alchemy.PotionContents
                .createItemStack(net.minecraft.world.item.Items.POTION, holder)
                .getDisplayName().getString())
            .orElse("");
    }

    private String enchantmentDisplayName(Identifier id) {
        return server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(id)
            .map(holder -> holder.value().description().getString())
            .orElse("");
    }

    private String structureDisplayName(Identifier id) {
        return translatedRegistryName("structure", id);
    }

    private String biomeDisplayName(Identifier id) {
        return translatedRegistryName("biome", id);
    }

    private String translatedRegistryName(String prefix, Identifier id) {
        String namespacedKey = prefix + "." + id.getNamespace() + "." + id.getPath();
        String translated = Component.translatable(namespacedKey).getString().trim();
        if (!translated.equals(namespacedKey)) {
            return translated;
        }

        String pathKey = prefix + "." + id.getPath();
        translated = Component.translatable(pathKey).getString().trim();
        return translated.equals(pathKey) ? "" : translated;
    }

    private String queryItem(String itemId) {
        if (itemId.isEmpty()) return queryText("item.usage");
        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(toResourceLocation(itemId));
        if (itemHolder.isEmpty()) return queryText("item.not_found", itemId);
        var item = itemHolder.get().value();
        if (item == net.minecraft.world.item.Items.AIR) return queryText("item.not_found", itemId);
        var stack = new net.minecraft.world.item.ItemStack(item);
        StringBuilder sb = new StringBuilder();
        sb.append(queryText("item.id", itemId)).append("\n");
        sb.append(queryText("item.name", stack.getDisplayName().getString())).append("\n");
        sb.append(queryText("item.type", item.getClass().getSimpleName())).append("\n");
        // 工具提示（含属性等）
        var tip = stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY, null,
            net.minecraft.world.item.TooltipFlag.Default.NORMAL);
        if (!tip.isEmpty()) {
            sb.append(queryText("item.tooltip")).append("\n");
            for (var t : tip) {
                sb.append("  ").append(t.getString()).append("\n");
            }
        }
        // 最大堆叠
        sb.append(queryText("item.max_stack", stack.getMaxStackSize()));
        // 食用信息
        var food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food != null) {
            sb.append("\n").append(queryText("item.food", food.nutrition(),
                String.format("%.1f", food.saturation())));
        }
        return sb.toString();
    }

    /** AI 查询 PMX 模型元数据（顶点/面/包围盒/材质），据此决定构建参数 */
    private String queryPMX(String filePath) {
        return ModelImportBridge.queryPMX(filePath);
    }

    private String queryRecipes(String itemId) {
        return queryRecipes(itemId, true);
    }

    private String queryRecipes(String itemId, boolean includeJeiHint) {
        if (itemId.isEmpty()) return queryText("recipe.usage");
        var itemHolder = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(toResourceLocation(itemId));
        if (itemHolder.isEmpty()) return queryText("item.not_found", itemId);
        var item = itemHolder.get().value();
        var level = server.overworld();
        var displayContext = net.minecraft.world.item.crafting.display.SlotDisplayContext.fromLevel(level);
        StringBuilder sb = new StringBuilder();
        sb.append(queryText("recipe.header", itemId)).append("\n");
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
                    sb.append(queryText("recipe.more")).append("\n");
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
            sb.append(queryText("recipe.none")).append("\n");
        }
        if (includeJeiHint && isModLoaded("jei")) {
            sb.append(queryText("jei.installed"));
        }
        return sb.toString();
    }

    private String queryJei(String itemId) {
        String jeiInfo = isModLoaded("jei")
            ? queryText("jei.installed")
            : queryText("jei.not_detected");
        if (itemId == null || itemId.isBlank()) {
            return jeiInfo + "\n" + queryText("jei.usage");
        }
        return jeiInfo + "\n" + queryRecipes(itemId, false);
    }

    private String summarizeIngredients(net.minecraft.world.item.crafting.Recipe<?> recipe) {
        var ingredients = recipe.placementInfo().ingredients();
        if (ingredients.isEmpty()) return queryText("recipe.ingredients_unknown");
        String summary = ingredients.stream()
            .map(this::summarizeIngredient)
            .filter(s -> !s.isBlank())
            .limit(9)
            .collect(Collectors.joining(" + "));
        if (ingredients.size() > 9) summary += " + ...";
        return summary.isBlank() ? queryText("recipe.ingredients_unknown") : summary;
    }

    private String summarizeIngredient(net.minecraft.world.item.crafting.Ingredient ingredient) {
        if (ingredient == null || ingredient.isEmpty()) return queryText("empty");
        List<String> names = ingredient.items()
            .limit(4)
            .map(holder -> new net.minecraft.world.item.ItemStack(holder).getDisplayName().getString())
            .distinct()
            .collect(Collectors.toList());
        if (names.isEmpty()) return queryText("recipe.custom_ingredient");
        return names.size() == 1 ? names.get(0) : "(" + String.join("/", names) + (names.size() >= 4 ? "/..." : "") + ")";
    }

    private String summarizeResults(List<net.minecraft.world.item.ItemStack> results, net.minecraft.world.item.Item targetItem) {
        String summary = results.stream()
            .filter(stack -> stack.getItem() == targetItem)
            .map(stack -> stack.getCount() + "x " + stack.getDisplayName().getString())
            .distinct()
            .limit(4)
            .collect(Collectors.joining(" / "));
        return summary.isBlank() ? queryText("recipe.target_item") : summary;
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
