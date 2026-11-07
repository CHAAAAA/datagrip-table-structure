package com.github.chaaaaa.datagripts.ui;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * 菜单项只读写 Swing 状态（选中行/单元格），统一声明在 EDT 上 update。
 * 平台默认是 BGT，声明错了会在 BGT 上读 Swing 选中态。
 */
public abstract class EdtAction extends AnAction {

    protected EdtAction(@NotNull String text, @NotNull String description) {
        this(text, description, null);
    }

    protected EdtAction(@NotNull String text, @NotNull String description, @Nullable Icon icon) {
        super(text, description, icon);
    }

    @Override
    public final @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }
}
