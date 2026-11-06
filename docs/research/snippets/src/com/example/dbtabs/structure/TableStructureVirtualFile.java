package com.example.dbtabs.structure;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.testFramework.LightVirtualFile;

/**
 * Option A virtual file: a light (in-memory, non-registered-VFS) file carrying the table identity.
 *
 * <p>Verified 2026.2 facts about this base class:
 * <ul>
 *   <li>{@code LightVirtualFile(String name, FileType type, CharSequence text)} exists.</li>
 *   <li>It implements {@code VirtualFileWithAssignedFileType}, so the assigned FileType wins over
 *       name-based detection - no ".sql" style name is needed.</li>
 *   <li>Its file system is a private, <b>unregistered</b> {@code MyVirtualFileSystem} with protocol
 *       {@code "mock"}; therefore {@code VirtualFileManager.findFileByUrl()} can never resolve it
 *       again after the IDE restarts (see report: tab restoration).</li>
 * </ul>
 *
 * <p>The content is intentionally empty: the real content is produced by the FileEditor, not by the
 * file. Using {@code setContent(...)} on a LightVirtualFile would also work but is unnecessary and
 * would produce a Document if the file type were not binary.
 */
public final class TableStructureVirtualFile extends LightVirtualFile {

  private final TableKey key;

  public TableStructureVirtualFile(TableKey key) {
    super(key.tabTitle(), TableStructureFileType.INSTANCE, "");
    this.key = key;
    setWritable(false);
  }

  public TableKey getKey() {
    return key;
  }

  /** Tab text comes from the VirtualFile name; FileType supplies the icon. */
  @Override
  public String getPresentableName() {
    return getName();
  }
}
