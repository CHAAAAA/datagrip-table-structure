package com.github.chaaaaa.datagripts.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 表的稳定标识：数据源唯一 id + schema + 表名。
 * 用于“同一个表只开一个标签页”的去重，以及数据源刷新后的重新定位。
 */
public record TableKey(
        @NotNull String dataSourceId,
        @NotNull String schema,
        @NotNull String table
) {
    public TableKey {
        dataSourceId = emptyIfNull(dataSourceId);
        schema = emptyIfNull(schema);
        table = emptyIfNull(table);
    }

    /** 作为缓存 map 的 key。 */
    public @NotNull String id() {
        return dataSourceId + "/" + schema + "/" + table;
    }

    /** 编辑器标签页标题，例如 {@code HR.EMP}。 */
    public @NotNull String tabTitle() {
        return schema.isEmpty() ? table : schema + "." + table;
    }

    private static @NotNull String emptyIfNull(@Nullable String value) {
        return value == null ? "" : value;
    }
}
