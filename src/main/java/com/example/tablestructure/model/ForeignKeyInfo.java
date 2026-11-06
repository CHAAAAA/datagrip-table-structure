package com.example.tablestructure.model;

import org.jetbrains.annotations.NotNull;

/**
 * 外键信息。
 */
public record ForeignKeyInfo(
        @NotNull String name,
        @NotNull String columns,
        @NotNull String refTable,
        @NotNull String refColumns,
        @NotNull String onDelete,
        @NotNull String onUpdate,
        @NotNull String deferrability
) {
}
