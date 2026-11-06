package com.example.dbtabs.structure.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.ex.dummy.DummyCachingFileSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Option B: persistent, URL-addressable virtual file system for table-structure tabs.
 *
 * <p>Registered in plugin.xml as
 * {@code <virtualFileSystem key="dgstructure" implementationClass="...TableStructureVirtualFileSystem"/>}.
 * Extension point {@code com.intellij.virtualFileSystem} exists and is dynamic (verified in
 * intellij.platform.core.impl.jar, META-INF/CoreImpl.analyzer.xml).
 *
 * <p>Because {@code findFileByPath(path)} can re-create a file from nothing but the path, editor tabs
 * survive IDE restart: the platform persists {@code dgstructure://<path>} and resolves it again on load.
 */
public final class TableStructureVirtualFileSystem extends DummyCachingFileSystem<TableStructureFsFile> {

  public static final String PROTOCOL = "dgstructure";

  /** Must be public no-arg: instantiated reflectively by the platform. */
  public TableStructureVirtualFileSystem() {
    super(PROTOCOL);
  }

  public static TableStructureVirtualFileSystem getInstance() {
    return (TableStructureVirtualFileSystem) com.intellij.openapi.vfs.VirtualFileManager.getInstance()
        .getFileSystem(PROTOCOL);
  }

  @Override
  protected @Nullable TableStructureFsFile findFileByPathInner(@NotNull String path) {
    return path.isEmpty() ? null : new TableStructureFsFile(this, path);
  }

  @Override
  protected @NotNull String getPresentableUrl(@NotNull VirtualFile file) {
    return file.getPath();
  }
}
