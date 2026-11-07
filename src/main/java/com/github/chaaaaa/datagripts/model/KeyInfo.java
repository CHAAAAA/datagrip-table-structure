package com.github.chaaaaa.datagripts.model;

import org.jetbrains.annotations.NotNull;

/**
 * 主键 / 唯一约束。
 */
public record KeyInfo(
        @NotNull String name,
        boolean primary,
        @NotNull String columns
) {
}
