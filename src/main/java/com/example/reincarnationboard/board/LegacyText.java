package com.example.reincarnationboard.board;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 把配置里的 {@code §} 颜色码解析成带样式的 {@link Component}。
 * 记分板侧栏的每一行都直接发 Component，不需要客户端装模组。
 */
public final class LegacyText {

    private LegacyText() {
    }

    public static Component parse(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        MutableComponent root = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == ChatFormatting.PREFIX_CODE && i + 1 < text.length()) {
                ChatFormatting format = ChatFormatting.getByCode(Character.toLowerCase(text.charAt(i + 1)));
                if (format != null) {
                    if (buffer.length() > 0) {
                        root.append(Component.literal(buffer.toString()).setStyle(style));
                        buffer.setLength(0);
                    }
                    style = apply(style, format);
                    i++;
                    continue;
                }
            }
            buffer.append(current);
        }
        if (buffer.length() > 0) {
            root.append(Component.literal(buffer.toString()).setStyle(style));
        }
        return root;
    }

    private static Style apply(Style style, ChatFormatting format) {
        if (format == ChatFormatting.RESET) {
            return Style.EMPTY;
        }
        if (format.isColor()) {
            return style.withColor(format);
        }
        return switch (format) {
            case BOLD -> style.withBold(true);
            case ITALIC -> style.withItalic(true);
            case UNDERLINE -> style.withUnderlined(true);
            case STRIKETHROUGH -> style.withStrikethrough(true);
            case OBFUSCATED -> style.withObfuscated(true);
            default -> style;
        };
    }
}
