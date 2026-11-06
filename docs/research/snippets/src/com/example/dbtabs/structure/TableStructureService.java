package com.example.dbtabs.structure;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project service that guarantees <b>one VirtualFile instance per table</b>.
 *
 * <p>This matters: {@code FileEditorManager.openFile()} keys tabs by VirtualFile instance. Calling it
 * twice with two different LightVirtualFile objects that have the same name opens two tabs.
 */
@Service(Service.Level.PROJECT)
public final class TableStructureService {

  private final Map<String, TableStructureVirtualFile> files = new ConcurrentHashMap<>();

  public static TableStructureService getInstance(Project project) {
    return project.getService(TableStructureService.class);
  }

  public TableStructureVirtualFile fileFor(TableKey key) {
    return files.computeIfAbsent(key.id(), id -> new TableStructureVirtualFile(key));
  }

  /** Call when the table is dropped/renamed so that a later open creates a fresh tab. */
  public void invalidate(TableKey key) {
    TableStructureVirtualFile file = files.remove(key.id());
    if (file != null) {
      file.setValid(false);
    }
  }
}
