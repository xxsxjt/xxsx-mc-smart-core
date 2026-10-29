package com.xxsx.builder.api.modelimport;

import com.xxsx.builder.ai.ChatSession;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Optional bridge from Smart Core to the separately packaged model importer. */
public final class ModelImportBridge {
    public interface Handler {
        boolean handleNumericInput(String input, CommandSourceStack source, ChatSession session);
        int handleBuildSpeed(CommandSourceStack source, String value);
        int handleBuildConfirm(CommandSourceStack source, boolean confirm);
        int handleBuildStop(CommandSourceStack source);
        int handleBuildCommand(String args, CommandSourceStack source, String playerName);
        String queryPMX(String filePath);
        boolean cancelBuild(String playerName);
        boolean hasActiveJob(String playerName);
    }

    private static volatile Handler handler;

    private ModelImportBridge() {}

    public static void register(Handler value) { handler = value; }

    public static void unregister(Handler value) {
        if (handler == value) handler = null;
    }

    public static boolean handleNumericInput(String input, CommandSourceStack source, ChatSession session) {
        Handler value = handler;
        return value != null && value.handleNumericInput(input, source, session);
    }

    public static int handleBuildSpeed(CommandSourceStack source, String value) {
        Handler current = handler;
        if (current != null) return current.handleBuildSpeed(source, value);
        return unavailable(source);
    }

    public static int handleBuildConfirm(CommandSourceStack source, boolean confirm) {
        Handler current = handler;
        if (current != null) return current.handleBuildConfirm(source, confirm);
        return unavailable(source);
    }

    public static int handleBuildStop(CommandSourceStack source) {
        Handler current = handler;
        if (current != null) return current.handleBuildStop(source);
        return unavailable(source);
    }

    public static int handleBuildCommand(String args, CommandSourceStack source, String playerName) {
        Handler current = handler;
        if (current != null) return current.handleBuildCommand(args, source, playerName);
        return unavailable(source);
    }

    public static String queryPMX(String filePath) {
        Handler current = handler;
        return current != null
            ? current.queryPMX(filePath)
            : "[xxsx] 模型导入扩展未安装，无法解析 PMX/OBJ。";
    }

    public static boolean cancelBuild(String playerName) {
        Handler current = handler;
        return current != null && current.cancelBuild(playerName);
    }

    public static boolean hasActiveJob(String playerName) {
        Handler current = handler;
        return current != null && current.hasActiveJob(playerName);
    }

    private static int unavailable(CommandSourceStack source) {
        source.sendFailure(Component.literal("§c模型导入扩展未安装：请同时安装 xxsx-mc-model-import。"));
        return 1;
    }
}
