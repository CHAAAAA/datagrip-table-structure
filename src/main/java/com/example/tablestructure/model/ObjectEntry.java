package com.example.tablestructure.model;

import com.intellij.database.model.DasObject;
import org.jetbrains.annotations.NotNull;

/**
 * 对象总览中的一行。{@code group} 用于类型过滤与双击分发，{@code dasObject} 在双击时才解析成 PSI
 * （避免为成千上万个对象预先建指针）。
 */
public record ObjectEntry(
        @NotNull String name,
        @NotNull String schema,
        @NotNull String kind,
        @NotNull String comment,
        @NotNull String group,
        boolean tableLike,
        @NotNull DasObject dasObject
) {
    public static final String GROUP_TABLE = "表";
    public static final String GROUP_VIEW = "视图";
    public static final String GROUP_SEQUENCE = "序列";
    public static final String GROUP_ROUTINE = "存储过程";
}
