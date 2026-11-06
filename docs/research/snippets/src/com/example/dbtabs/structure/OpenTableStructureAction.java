package com.example.dbtabs.structure;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Opens (or re-selects) the structure tab. Register it into the Database tool window popup, see
 * plugin.xml snippet in the report.
 *
 * <p>Verified 2026.2 opening API:
 * {@code FileEditorManager#openFile(VirtualFile, boolean focus)} returns {@code FileEditor[]} and is
 * the only call needed - the provider above is picked up automatically.
 */
public final class OpenTableStructureAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      return;
    }
    TableKey key = selectedTableKey(e);
    if (key == null) {
      return;
    }
    // Option A: the virtual file lives in memory only.
    TableStructureVirtualFile file = TableStructureService.getInstance(project).fileFor(key);
    FileEditorManager.getInstance(project).openFile(file, true);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(e.getProject() != null && selectedTableKey(e) != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }

  /**
   * Placeholder: replace with the real DataGrip selection lookup, e.g.
   * {@code DbElement} from the Database view / console (see report, "getting the selected table").
   */
  private static TableKey selectedTableKey(@NotNull AnActionEvent e) {
    if (e.getData(CommonDataKeys.PROJECT) == null) {
      return null;
    }
    return null; // TODO wire to the Database tool window selection
  }
}
