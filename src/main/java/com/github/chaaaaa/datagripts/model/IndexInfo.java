package com.github.chaaaaa.datagripts.model;

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
