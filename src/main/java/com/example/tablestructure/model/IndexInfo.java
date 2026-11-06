package com.example.tablestructure.model;

import org.jetbrains.annotations.NotNull;

/**
 * 索引信息。
 */
public record IndexInfo(
        @NotNull String name,
        boolean unique,
        boolean functionBased,
        @NotNull String columns,
        @NotNull String comment
) {
}
