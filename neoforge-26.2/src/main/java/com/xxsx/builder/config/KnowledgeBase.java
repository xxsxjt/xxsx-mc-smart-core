package com.xxsx.builder.config;

import com.xxsx.builder.XxsxBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识库系统 — 支持内置（jar内 resources/knowledge/）和外置。
 * AI 用 [KNOWLEDGE]模组名[/KNOWLEDGE] 请求读取。
 */
public class KnowledgeBase {
    private static final Set<String> CORE_TOPICS = Set.of("ftbquests");
    private static final Map<String, String> TOPIC_ALIASES = Map.ofEntries(
        Map.entry("integration", "integrations"),
        Map.entry("integrations", "integrations"),
        Map.entry("联动", "integrations"),
        Map.entry("justenoughprofessions", "jei"),
        Map.entry("naturescompass", "compasses"),
        Map.entry("explorerscompass", "compasses"),
        Map.entry("sophisticatedbackpacks", "sophisticated"),
        Map.entry("sophisticatedstorage", "sophisticated"),
        Map.entry("sophisticatedcore", "sophisticated"),
        Map.entry("xaerominimap", "xaero"),
        Map.entry("xaeroworldmap", "xaero"),
        Map.entry("enchdesc", "enchanting"),
        Map.entry("enchantmentdescriptions", "enchanting"),
        Map.entry("enchantmentinsights", "enchanting"),
        Map.entry("easymagic", "enchanting"),
        Map.entry("taxfreelevels", "enchanting"),
        Map.entry("legendarytooltips", "clientui"),
        Map.entry("controlling", "clientui"),
        Map.entry("mousetweaks", "clientui"),
        Map.entry("sodium", "clientui"),
        Map.entry("sodium_extra", "clientui"),
        Map.entry("drippyloadingscreen", "clientui"),
        Map.entry("fancymenu", "clientui"),
        Map.entry("lootr", "exploration"),
        Map.entry("shogi", "exploration"),
        Map.entry("earth_online", "projectmods"),
        Map.entry("templenihility", "projectmods")
    );
    private static final Map<String, List<String>> BUILTIN_DOCS = Map.ofEntries(
        Map.entry("ftbquests", List.of("任务系统.md")),
        Map.entry("bloodmagic", List.of("祭坛与炼金术.md")),
        Map.entry("integrations", List.of("26.2联动清单.md")),
        Map.entry("jei", List.of("26.2联动.md")),
        Map.entry("jade", List.of("26.2联动.md")),
        Map.entry("appleskin", List.of("26.2联动.md")),
        Map.entry("waystones", List.of("26.2联动.md")),
        Map.entry("compasses", List.of("26.2联动.md")),
        Map.entry("sophisticated", List.of("26.2联动.md")),
        Map.entry("curios", List.of("26.2联动.md")),
        Map.entry("xaero", List.of("26.2联动.md")),
        Map.entry("enchanting", List.of("26.2联动.md")),
        Map.entry("clientui", List.of("26.2联动.md")),
        Map.entry("exploration", List.of("26.2联动.md")),
        Map.entry("projectmods", List.of("26.2联动.md"))
    );

    public static String readKnowledge(String kbName) {
        String requested = normalizeTopic(kbName);
        String topic = resolveTopic(requested);
        String live = readLiveKnowledge(topic);
        if (live != null) return live;
        String external = readExternal(requested);
        if (external != null) return external;
        external = readExternal(topic);
        if (external != null) return external;
        return readBuiltinSimple(topic);
    }

    private static String readExternal(String kbName) {
        String root = XxsxBuilder.getInstance() != null
            ? XxsxBuilder.getInstance().getConfig().knowledgePath : "config/xxsx_builder/knowledge";
        Path dir = Paths.get(root, kbName);
        if (!Files.isDirectory(dir)) return null;
        return readDir(dir, kbName, "外置");
    }

    private static String readLiveKnowledge(String kbName) {
        return switch (kbName) {
            case "ftbquests" -> readFTBQuests();
            default -> null;
        };
    }

    public static boolean isCoreTopic(String kbName) {
        return CORE_TOPICS.contains(resolveTopic(normalizeTopic(kbName)));
    }

    public static String describeTopic(String kbName) {
        String requested = normalizeTopic(kbName);
        String topic = resolveTopic(requested);
        if (readLiveKnowledge(topic) != null) return "实时/生成型联动知识";
        String root = XxsxBuilder.getInstance() != null
            ? XxsxBuilder.getInstance().getConfig().knowledgePath : "config/xxsx_builder/knowledge";
        if (Files.isDirectory(Paths.get(root, requested))) return "外置联动文档";
        if (Files.isDirectory(Paths.get(root, topic))) return "外置联动文档";
        if (BUILTIN_DOCS.containsKey(topic)) return isCoreTopic(topic) ? "内置核心知识" : "内置联动文档";
        return null;
    }

    /** 简化内置读取：不依赖 JarFile，直接从已知资源名读取 */
    private static String readBuiltinSimple(String kbName) {
        String resourcePath = "knowledge/" + kbName + "/";
        List<String> knownFiles = BUILTIN_DOCS.getOrDefault(kbName, Collections.emptyList());
        if (knownFiles.isEmpty()) return null;

        StringBuilder result = new StringBuilder();
        result.append("【知识库: ").append(kbName).append("（")
            .append(isCoreTopic(kbName) ? "内置核心" : "内置联动")
            .append("）】\n");
        var loader = KnowledgeBase.class.getClassLoader();
        for (String file : knownFiles) {
            try (var is = loader.getResourceAsStream(resourcePath + file)) {
                if (is == null) continue;
                String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                result.append("--- ").append(file).append(" ---\n").append(content).append("\n");
            } catch (Exception ignored) {}
        }
        return result.toString();
    }

    private static String readDir(Path dir, String name, String sourceLabel) {
        StringBuilder result = new StringBuilder();
        result.append("【知识库: ").append(name).append("（").append(sourceLabel).append("）】\n");
        try {
            Files.list(dir)
                .filter(f -> f.toString().endsWith(".md") || f.toString().endsWith(".txt"))
                .sorted().forEach(f -> {
                    try { result.append("--- ").append(f.getFileName()).append(" ---\n")
                               .append(Files.readString(f)).append("\n"); }
                    catch (IOException ignored) {}
                });
        } catch (IOException e) { return null; }
        return result.toString();
    }

    /** FTB Quests 实时读取 */
    private static String readFTBQuests() {
        Path dir = Paths.get("config", "ftbquests", "quests", "chapters");
        if (!Files.isDirectory(dir)) return null;

        StringBuilder r = new StringBuilder("【FTB Quests 实时数据】\n");
        try {
            List<Path> files = Files.list(dir).filter(f -> f.toString().endsWith(".snbt")).sorted()
                .collect(Collectors.toList());
            r.append("共 ").append(files.size()).append(" 个章节\n\n");
            for (Path f : files) {
                String name = f.getFileName().toString().replace(".snbt", "");
                r.append("=== ").append(name).append(" ===\n");
                r.append(extractQuestSummary(Files.readString(f))).append("\n\n");
            }
        } catch (IOException e) {
            r.append("读取失败: ").append(e.getMessage());
        }
        return r.toString();
    }

    private static String extractQuestSummary(String snbt) {
        StringBuilder sb = new StringBuilder();
        int ti = snbt.indexOf("title:");
        if (ti >= 0) {
            int end = snbt.indexOf("\n", ti);
            sb.append("标题: ").append(snbt.substring(ti, end > 0 ? end : snbt.length())).append("\n");
        }
        int qc = snbt.split("\\{", -1).length - 1;
        sb.append("任务/对象数: ~").append(qc).append("\n");
        int idx = snbt.indexOf("quests:[");
        if (idx >= 0) {
            int end = snbt.indexOf("]", idx);
            if (end > idx && end - idx < 2000)
                sb.append("任务列表: ").append(snbt.substring(idx, end)).append("\n");
        }
        return sb.toString();
    }

    public static List<String> listKnowledgeBases() {
        Set<String> names = new LinkedHashSet<>();
        names.addAll(CORE_TOPICS);
        names.addAll(BUILTIN_DOCS.keySet());
        names.addAll(TOPIC_ALIASES.keySet());
        String root = XxsxBuilder.getInstance() != null
            ? XxsxBuilder.getInstance().getConfig().knowledgePath : "config/xxsx_builder/knowledge";
        Path dir = Paths.get(root);
        if (Files.isDirectory(dir)) {
            try { Files.list(dir).filter(Files::isDirectory).map(p -> p.getFileName().toString()).forEach(names::add); }
            catch (IOException ignored) {}
        }
        return new ArrayList<>(names);
    }

    public static String summarizeKnowledge(String kbName, java.util.function.Function<String, String> summarizer) {
        String topic = resolveTopic(normalizeTopic(kbName));
        String content = readKnowledge(topic);
        if (content == null || content.isBlank()) return null;
        if (summarizer == null) return content;
        return summarizer.apply(content);
    }

    public static String searchKnowledge(String kbName, String keyword) {
        String topic = resolveTopic(normalizeTopic(kbName));
        String content = readKnowledge(topic);
        if (content == null || content.isBlank()) return null;
        if (keyword == null || keyword.isBlank()) return content;
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        List<String> hits = Arrays.stream(content.split("\\R"))
            .filter(line -> line.toLowerCase(Locale.ROOT).contains(lowerKeyword))
            .limit(20)
            .toList();
        if (hits.isEmpty()) return "知识主题 " + topic + " 中未找到关键词: " + keyword;
        return "知识主题 " + topic + " 中与“" + keyword + "”相关的内容:\n"
            + String.join("\n", hits);
    }

    public static String processKnowledgeTags(String aiResponse, CommandSourceStack source) {
        if (aiResponse == null || !aiResponse.contains("[KNOWLEDGE]")) return aiResponse;
        StringBuilder result = new StringBuilder();
        var m = java.util.regex.Pattern.compile(
            "\\[KNOWLEDGE\\](.*?)\\[/KNOWLEDGE\\]", java.util.regex.Pattern.DOTALL).matcher(aiResponse);
        int lastEnd = 0;
        while (m.find()) {
            result.append(aiResponse, lastEnd, m.start());
            String kbName = resolveTopic(normalizeTopic(m.group(1)));
            String content = readKnowledge(kbName);
            if (content != null) {
                result.append("\n").append(content).append("\n");
                if (source != null) {
                    String desc = describeTopic(kbName);
                    source.sendSuccess(() -> Component.literal("§7[知识库] 已加载: " + kbName
                        + (desc == null ? "" : " (" + desc + ")")), false);
                }
            } else {
                var avail = listKnowledgeBases();
                result.append("\n【知识库 \"").append(kbName).append("\" 不存在")
                      .append(avail.isEmpty() ? "，没有可用知识库" : "，可用: " + String.join(", ", avail))
                      .append("】\n");
            }
            lastEnd = m.end();
        }
        result.append(aiResponse.substring(lastEnd));
        return result.toString();
    }

    private static String normalizeTopic(String kbName) {
        return kbName == null ? "" : kbName.trim().toLowerCase(Locale.ROOT);
    }

    private static String resolveTopic(String kbName) {
        String topic = normalizeTopic(kbName);
        return TOPIC_ALIASES.getOrDefault(topic, topic);
    }
}
