package com.xxsx.builder.config;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.*;

/** AI 回复折叠：显示摘要 + 悬停全文 + 点击复制，避免刷屏 */
public class ChatFold {

    private static final int FOLD_LINES = 3;
    private static final int FOLD_CHARS = 200;

    public static Component fold(String fullText, String playerName) {
        if (fullText == null || fullText.isEmpty()) return Component.empty();

        String[] lines = fullText.split("\n", -1);
        if (lines.length <= FOLD_LINES && fullText.length() < FOLD_CHARS) {
            String prefix = SmartCoreText.chat("ai_prefix").getString();
            return Component.literal(prefix + fullText.replace("\n", "\n" + prefix))
                .withStyle(ChatFormatting.AQUA);
        }

        // 折叠版本
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(FOLD_LINES, lines.length); i++) {
            preview.append(lines[i]).append("\n");
        }
        String shortText = preview.toString().trim();
        int remaining = fullText.length() - shortText.length();

        MutableComponent folded = Component.literal(
            SmartCoreText.chat("ai_prefix").getString() + shortText).withStyle(ChatFormatting.AQUA);
        MutableComponent expand = SmartCoreText.chat("fold_expand", remaining, lines.length).copy();

        // 悬停显示全文，点击复制
        expand.withStyle(Style.EMPTY
            .withColor(ChatFormatting.DARK_GRAY)
            .withItalic(true)
            .withHoverEvent(new HoverEvent.ShowText(
                Component.literal(fullText).withStyle(ChatFormatting.WHITE)))
            .withClickEvent(new ClickEvent.CopyToClipboard(fullText)));

        folded.append(expand);
        return folded;
    }

    public static Component foldExecResult(String result) {
        if (result == null || result.isEmpty()) return Component.empty();
        if (result.length() < 300) return Component.literal(result);

        String summary = result.substring(0, Math.min(200, result.length())) + "...";
        MutableComponent c = Component.literal(summary + " ").withStyle(ChatFormatting.GRAY);
        c.append(SmartCoreText.chat("hover_full").copy().withStyle(ChatFormatting.DARK_GRAY));
        c.withStyle(Style.EMPTY
            .withHoverEvent(new HoverEvent.ShowText(
                Component.literal(result))));
        return c;
    }
}
