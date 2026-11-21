package com.example.reincarnationboard.board;

import java.util.Map;

/** 简单的文本工具：占位符替换 + 截断。 */
public final class Texts {

    private Texts() {
    }

    /**
     * 把 {@code %key%} 替换成对应值，并按 {@code maxLength} 截断。
     * 没有值的占位符保持原样（方便管理员发现写错）。
     */
    public static String fill(String template, Map<String, String> values, int maxLength) {
        if (template == null || template.isEmpty()) {
            return "";
        }
        String out = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            out = out.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return clip(out, maxLength);
    }

    /** 截断到指定长度；上限之外的字符直接丢弃，避免记分板侧栏被撑得过宽。 */
    public static String clip(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        int limit = Math.max(1, maxLength);
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit);
    }
}
