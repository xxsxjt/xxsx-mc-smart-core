package com.xxsx.builder.config;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 独立日志输出 — 写入 logs/xxsx_builder.log
 * 不依赖 SLF4J/Log4J，直接写文件，便于排查 AI 交互问题。
 */
public class ModLogger {
    private static final Path LOG_DIR = Paths.get("logs");
    private static final Path LOG_FILE = LOG_DIR.resolve("xxsx_builder.log");
    private static PrintWriter writer;
    private static final Object lock = new Object();

    static {
        try {
            Files.createDirectories(LOG_DIR);
            writer = new PrintWriter(new FileWriter(LOG_FILE.toFile(), true));
        } catch (IOException e) {
            System.err.println("[xxsx_builder] 无法创建日志文件: " + e.getMessage());
        }
    }

    public static void info(String msg) { log("INFO", msg); }
    public static void warn(String msg) { log("WARN", msg); }
    public static void error(String msg) { log("ERROR", msg); }

    /** 记录 AI 完整对话往返 */
    public static void aiChat(String playerName, String input, String response) {
        log("AI_CHAT", "[" + playerName + "] >> " + input);
        log("AI_CHAT", "[" + playerName + "] << " + response.replace("\n", "\\n"));
    }

    /** 记录指令执行 */
    public static void commandExec(String cmd, int result) {
        log("CMD", "执行: /" + cmd + " (返回 " + result + ")");
    }

    public static void commandError(String cmd, String error) {
        log("CMD_ERR", "失败: /" + cmd + " → " + error);
    }

    private static void log(String level, String msg) {
        synchronized (lock) {
            if (writer != null) {
                writer.printf("[%s] [%s] %s%n",
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                    level, msg);
                writer.flush();
            }
        }
    }
}
