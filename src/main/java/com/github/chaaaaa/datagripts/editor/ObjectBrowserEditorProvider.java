package com.github.chaaaaa.datagripts.editor;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 把 {@link ObjectBrowserVirtualFile} 绑定到 {@link ObjectBrowserFileEditor}。
 * 与 {@link TableStructureEditorProvider} 同构（HIDE_DEFAULT_EDITOR 必须配 DumbAware）。
 */
public final class ObjectBrowserEditorProvider implements FileEditorProvider, DumbAware {

    public static final String EDITOR_TYPE_ID = "datagrip-object-browser";

    @Override
    public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
        return file instanceof ObjectBrowserVirtualFile;
    }

    @Override
    public boolean acceptRequiresReadAction() {
        return false;
    }

    @Override
    public @NotNull FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
        return new ObjectBrowserFileEditor(project, (ObjectBrowserVirtualFile) file);
    }

    @Override
    public @NotNull String getEditorTypeId() {
        return EDITOR_TYPE_ID;
    }

    @Override
    public @NotNull FileEditorPolicy getPolicy() {
        return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
    }
}
