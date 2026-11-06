package com.example.dbtabs.structure;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Binds {@link TableStructureVirtualFile} to {@link TableStructureFileEditor}.
 *
 * <p>Verified 2026.2 interface (note: the method is <b>createEditor</b>, not createFileEditor):
 * <pre>
 * public interface FileEditorProvider extends PossiblyDumbAware {
 *   boolean accept(Project, VirtualFile);
 *   default boolean acceptRequiresReadAction();
 *   FileEditor createEditor(Project, VirtualFile);
 *   default void disposeEditor(FileEditor);
 *   default FileEditorState readState(Element, Project, VirtualFile);
 *   default void writeState(FileEditorState, Project, Element);
 *   String getEditorTypeId();
 *   FileEditorPolicy getPolicy();
 * }
 * </pre>
 *
 * <p>HIDE_DEFAULT_EDITOR + not-DumbAware is reported as an error by
 * FileEditorProviderManagerImpl.checkPolicy(), hence {@link DumbAware} below.
 */
public final class TableStructureFileEditorProvider implements FileEditorProvider, DumbAware {

  /** Also used as the {@code id} of the &lt;fileEditorProvider&gt; extension. */
  public static final String EDITOR_TYPE_ID = "datagrip-table-structure";

  @Override
  public boolean accept(Project project, VirtualFile file) {
    return file instanceof TableStructureVirtualFile;
  }

  /** Pure instanceof check: no read action needed. Default is true. */
  @Override
  public boolean acceptRequiresReadAction() {
    return false;
  }

  @Override
  public FileEditor createEditor(Project project, VirtualFile file) {
    return new TableStructureFileEditor((TableStructureVirtualFile) file);
  }

  @Override
  public String getEditorTypeId() {
    return EDITOR_TYPE_ID;
  }

  @Override
  public FileEditorPolicy getPolicy() {
    // no text editor tab for our virtual files
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
