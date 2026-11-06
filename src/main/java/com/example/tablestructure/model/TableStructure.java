package com.example.tablestructure.model;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 一张表的结构快照。加载完成后不可变，可在 EDT 上安全渲染。
 */
public record TableStructure(
        @NotNull TableInfo table,
        @NotNull List<ColumnInfo> columns,
        @NotNull List<IndexInfo> indexes,
        @NotNull List<KeyInfo> keys,
        @NotNull List<ForeignKeyInfo> foreignKeys
) {
    public TableStructure {
        columns = List.copyOf(columns);
        indexes = List.copyOf(indexes);
        keys = List.copyOf(keys);
        foreignKeys = List.copyOf(foreignKeys);
    }
}
