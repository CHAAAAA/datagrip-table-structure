package com.github.chaaaaa.datagripts.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 把 {@link TableStructureVirtualFile} 绑定到 {@link TableStructureFileEditor}。
 *
 * <p>2026.2 平台接口要点（已用 javap 核实）：
 * <ul>
 *   <li>抽象方法是 {@code createEditor(Project, VirtualFile)}（不存在 {@code createFileEditor}）；</li>
 *   <li>{@code acceptRequiresReadAction()} 默认 true，这里只是 instanceof 检查，返回 false；</li>
 *   <li>{@code HIDE_DEFAULT_EDITOR} 策略 + 非 DumbAware 会在运行时被平台记录 PluginException，
 *       因此实现 {@link DumbAware}。</li>
 * </ul>
 */
public final class TableStructureEditorProvider implements FileEditorProvider, DumbAware {

    public static final String EDITOR_TYPE_ID = "datagrip-table-structure";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof TableStructureVirtualFile;
    }

    @Override
    public boolean acceptRequiresReadAction() {
        return false;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new TableStructureFileEditor(project, (TableStructureVirtualFile) file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        // 该虚拟文件没有默认（文本）编辑器，隐藏默认编辑器即可
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
