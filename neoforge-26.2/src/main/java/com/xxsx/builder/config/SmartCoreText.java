package com.xxsx.builder.config;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Language-key helpers for the client-only 26.2 UI and feedback surface. */
public final class SmartCoreText {
    private static final String MODID = "xxsx_mc_smart_core";

    private SmartCoreText() {
    }

    public static MutableComponent gui(String key, Object... args) {
        return translate("gui." + MODID + "." + key, args);
    }

    public static MutableComponent message(String key, Object... args) {
        return translate("message." + MODID + "." + key, args);
    }

    public static MutableComponent command(String key, Object... args) {
        return translate("command." + MODID + "." + key, args);
    }

    public static MutableComponent chat(String key, Object... args) {
        return translate("chat." + MODID + "." + key, args);
    }

    public static MutableComponent system(String key, Object... args) {
        return translate("system." + MODID + "." + key, args);
    }

    public static String text(String key, Object... args) {
        return translate(key, args).getString();
    }

    public static MutableComponent translate(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
