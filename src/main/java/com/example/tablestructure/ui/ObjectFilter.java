package com.example.tablestructure.ui;

import com.example.tablestructure.model.ObjectEntry;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.codeStyle.NameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 对象总览的过滤逻辑：大小写不敏感的模糊匹配。
 *
 * <p>用平台内置 {@link MinusculeMatcher}（覆盖驼峰、下划线、子序列，如 {@code empa} → {@code EMP_ADDRESS}），
 * 前缀 {@code *} 表示"包含"。含非 ASCII 字符（中文等）的名称上模糊匹配不可靠，退化为子串匹配。</p>
 */
public final class ObjectFilter {

    private ObjectFilter() {
    }

    /**
     * @param pattern 搜索词；空串表示不按名称过滤
     * @param group   类型过滤；null 或空串表示全部
     */
    public static @NotNull List<ObjectEntry> filter(
            @NotNull List<ObjectEntry> all,
            @Nullable String pattern,
            @Nullable String group
    ) {
        String trimmed = pattern == null ? "" : pattern.trim();
        boolean filterGroup = group != null && !group.isEmpty();
        if (trimmed.isEmpty() && !filterGroup) {
            return all;
        }

        MinusculeMatcher matcher = trimmed.isEmpty() ? null : NameUtil.buildMatcher("*" + trimmed).build();
        String lowerPattern = trimmed.toLowerCase();

        List<ObjectEntry> result = new ArrayList<>();
        for (ObjectEntry entry : all) {
            if (filterGroup && !group.equals(entry.group())) {
                continue;
            }
            if (matcher != null && !matches(entry.name(), matcher, lowerPattern)) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    private static boolean matches(@NotNull String name, @NotNull MinusculeMatcher matcher, @NotNull String lowerPattern) {
        if (matcher.matches(name)) {
            return true;
        }
        return !isAscii(name) && name.toLowerCase().contains(lowerPattern);
    }

    private static boolean isAscii(@NotNull String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }
}
