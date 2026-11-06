package com.example.dbtabs.structure.vfs;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSystem;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Option B: a plain read-only {@link VirtualFile} that only carries a table key.
 *
 * <p>It deliberately implements no content: the tab content is rendered by the FileEditor.
 * {@code getName()} drives the tab title, {@code getFileType()} the tab icon.
 *
 * <p>Note: parent is {@code null} (flat namespace) - the same trick DataGrip's own
 * {@code DatabaseVirtualFileSystem} uses ("das://..." files have no directory tree).
 */
public final class TableStructureFsFile extends VirtualFile {

  private final VirtualFileSystem fileSystem;
  private final String path;
  private final String name;
  private final long stamp = System.currentTimeMillis();
  private volatile boolean valid = true;

  TableStructureFsFile(VirtualFileSystem fileSystem, String path) {
    this.fileSystem = fileSystem;
    this.path = path;
    int slash = path.lastIndexOf('/');
    this.name = slash < 0 ? path : path.substring(slash + 1);
  }

  void invalidate() {
    valid = false;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public VirtualFileSystem getFileSystem() {
    return fileSystem;
  }

  @Override
  public String getPath() {
    return path;
  }

  @Override
  public boolean isWritable() {
    return false;
  }

  @Override
  public boolean isDirectory() {
    return false;
  }

  @Override
  public boolean isValid() {
    return valid;
  }

  @Override
  public VirtualFile getParent() {
    return null;
  }

  @Override
  public VirtualFile[] getChildren() {
    return EMPTY_ARRAY;
  }

  @Override
  public long getTimeStamp() {
    return stamp;
  }

  @Override
  public long getLength() {
    return 0;
  }

  @Override
  public void refresh(boolean asynchronous, boolean recursive, Runnable postRunnable) {
    if (postRunnable != null) {
      postRunnable.run();
    }
  }

  @Override
  public InputStream getInputStream() {
    return InputStream.nullInputStream();
  }

  @Override
  public OutputStream getOutputStream(Object requestor, long newModificationStamp, long newTimeStamp)
      throws IOException {
    throw new IOException("read-only virtual file: " + path);
  }

  @Override
  public byte[] contentsToByteArray() {
    return new byte[0];
  }

  @Override
  public com.intellij.openapi.fileTypes.FileType getFileType() {
    return com.example.dbtabs.structure.TableStructureFileType.INSTANCE;
  }
}
