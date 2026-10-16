package com.xxsx.builder.modelimport;

import com.xxsx.builder.ai.ChatSession;
import com.xxsx.builder.config.SmartCoreText;
import net.minecraft.commands.CommandSourceStack;

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

    public static void register(Handler value) {
        handler = value;
    }

    public static void unregister(Handler value) {
        if (handler == value) handler = null;
    }

    public static boolean isAvailable() {
        return handler != null;
    }

    public static boolean handleNumericInput(String input, CommandSourceStack source, ChatSession session) {
        Handler value = handler;
        return value != null && value.handleNumericInput(input, source, session);
    }

    public static int handleBuildSpeed(CommandSourceStack source, String value) {
        Handler current = handler;
        if (current != null) return current.handleBuildSpeed(source, value);
        source.sendFailure(SmartCoreText.command("build.extension_missing"));
        return 1;
    }

    public static int handleBuildConfirm(CommandSourceStack source, boolean confirm) {
        Handler current = handler;
        if (current != null) return current.handleBuildConfirm(source, confirm);
        source.sendFailure(SmartCoreText.command("build.extension_missing"));
        return 1;
    }

    public static int handleBuildStop(CommandSourceStack source) {
        Handler current = handler;
        if (current != null) return current.handleBuildStop(source);
        source.sendFailure(SmartCoreText.command("build.extension_missing"));
        return 1;
    }

    public static int handleBuildCommand(String args, CommandSourceStack source, String playerName) {
        Handler current = handler;
        if (current != null) return current.handleBuildCommand(args, source, playerName);
        source.sendFailure(SmartCoreText.command("build.extension_missing"));
        return 1;
    }

    public static String queryPMX(String filePath) {
        Handler current = handler;
        return current != null
            ? current.queryPMX(filePath)
            : SmartCoreText.text("query.xxsx_mc_smart_core.pmx.extension_missing");
    }

    public static boolean cancelBuild(String playerName) {
        Handler current = handler;
        return current != null && current.cancelBuild(playerName);
    }

    public static boolean hasActiveJob(String playerName) {
        Handler current = handler;
        return current != null && current.hasActiveJob(playerName);
    }
}
