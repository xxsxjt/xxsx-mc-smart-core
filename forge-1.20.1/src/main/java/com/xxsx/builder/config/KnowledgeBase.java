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

    public static String readKnowledge(String kbName) {
        // FTB Quests 实时读取
        if ("ftbquests".equals(kbName)) {
            String live = readFTBQuests();
            if (live != null) return live;
        }
        // 外置
        String external = readExternal(kbName);
        if (external != null) return external;
        // 内置（简化版：只读已知的几个）
        return readBuiltinSimple(kbName);
    }

    private static String readExternal(String kbName) {
        String root = XxsxBuilder.getInstance() != null
            ? XxsxBuilder.getInstance().getConfig().knowledgePath : "config/xxsx_builder/knowledge";
        Path dir = Paths.get(root, kbName);
        if (!Files.isDirectory(dir)) return null;
        return readDir(dir, kbName);
    }

    /** 简化内置读取：不依赖 JarFile，直接从已知资源名读取 */
    private static String readBuiltinSimple(String kbName) {
        // 只支持 ftbquests 和 bloodmagic 两个内置知识库
        String resourcePath = "knowledge/" + kbName + "/";
        List<String> knownFiles = switch (kbName) {
            case "ftbquests" -> List.of("任务系统.md");
            case "bloodmagic" -> List.of("祭坛与炼金术.md");
            default -> Collections.emptyList();
        };
        if (knownFiles.isEmpty()) return null;

        StringBuilder result = new StringBuilder();
        result.append("【知识库: ").append(kbName).append("（内置）】\n");
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

    private static String readDir(Path dir, String name) {
        StringBuilder result = new StringBuilder();
        result.append("【知识库: ").append(name).append("】\n");
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
        names.add("ftbquests");
        names.add("bloodmagic");
        // 外置
        String root = XxsxBuilder.getInstance() != null
            ? XxsxBuilder.getInstance().getConfig().knowledgePath : "config/xxsx_builder/knowledge";
        Path dir = Paths.get(root);
        if (Files.isDirectory(dir)) {
            try { Files.list(dir).filter(Files::isDirectory).map(p -> p.getFileName().toString()).forEach(names::add); }
            catch (IOException ignored) {}
        }
        return new ArrayList<>(names);
    }

    public static String processKnowledgeTags(String aiResponse, CommandSourceStack source) {
        if (aiResponse == null || !aiResponse.contains("[KNOWLEDGE]")) return aiResponse;
        StringBuilder result = new StringBuilder();
        var m = java.util.regex.Pattern.compile(
            "\\[KNOWLEDGE\\](.*?)\\[/KNOWLEDGE\\]", java.util.regex.Pattern.DOTALL).matcher(aiResponse);
        int lastEnd = 0;
        while (m.find()) {
            result.append(aiResponse, lastEnd, m.start());
            String kbName = m.group(1).trim();
            String content = readKnowledge(kbName);
            if (content != null) {
                result.append("\n").append(content).append("\n");
                if (source != null)
                    source.sendSuccess(() -> Component.literal("§7[知识库] 已加载: " + kbName), false);
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
}
