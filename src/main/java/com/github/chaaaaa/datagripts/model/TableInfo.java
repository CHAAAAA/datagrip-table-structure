package com.github.chaaaaa.datagripts.model;

import org.jetbrains.annotations.NotNull;

/**
 * 表的总体信息（对应需求中的“表的总体信息”区块）。
 */
public record TableInfo(
        @NotNull String schema,
        @NotNull String name,
        @NotNull String comment,
        @NotNull String dataSourceName,
        @NotNull String dbmsName,
        @NotNull String dbmsVersion,
        @NotNull String kind,
        boolean temporary,
        boolean system,
        int columnCount,
        int indexCount,
        int keyCount,
        int foreignKeyCount
) {
    /** 形如 {@code SCHEMA.TABLE} 的限定名。 */
    public @NotNull String qualifiedName() {
        return schema.isEmpty() ? name : schema + "." + name;
    }
}
