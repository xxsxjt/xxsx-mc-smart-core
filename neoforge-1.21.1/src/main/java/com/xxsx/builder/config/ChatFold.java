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
            return Component.literal("§b[AI] " + fullText.replace("\n", "\n§b[AI] "));
        }

        // 折叠版本
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(FOLD_LINES, lines.length); i++) {
            preview.append(lines[i]).append("\n");
        }
        String shortText = preview.toString().trim();
        int remaining = fullText.length() - shortText.length();

        MutableComponent folded = Component.literal("§b[AI] " + shortText);
        MutableComponent expand = Component.literal(
            " §8§o[+" + remaining + "字符, " + lines.length + "行, 悬停查看]");

        // 悬停显示全文，点击复制
        expand.withStyle(Style.EMPTY
            .withColor(ChatFormatting.AQUA)
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal(fullText).withStyle(ChatFormatting.WHITE)))
            .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, fullText)));

        folded.append(expand);
        return folded;
    }

    public static Component foldExecResult(String result) {
        if (result == null || result.isEmpty()) return Component.empty();
        if (result.length() < 300) return Component.literal(result);

        String summary = result.substring(0, Math.min(200, result.length())) + "...";
        MutableComponent c = Component.literal("§7" + summary + " §8[悬停看全部]");
        c.withStyle(Style.EMPTY
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                Component.literal(result))));
        return c;
    }
}
