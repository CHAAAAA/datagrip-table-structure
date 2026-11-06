package com.example.dbtabs.structure;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.BorderLayout;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * The actual tab content: one JPanel with inner tabs (Columns / Indexes / Constraints).
 *
 * <p>This is a plain {@link FileEditor}; it is NOT a dialog and NOT a TextEditor. Every abstract
 * member of FileEditor as verified in 2026.2 is implemented below.
 *
 * <p>Note: {@code FileEditor#getFile()} has a <b>default implementation that logs a
 * "proper @NotNull implementation required" error</b>, so it must be overridden (done here).
 * {@code FileEditor#getState(FileEditorStateLevel)} already defaults to
 * {@code FileEditorState.INSTANCE}, so only {@code setState} is mandatory.
 */
public final class TableStructureFileEditor extends UserDataHolderBase implements FileEditor, Disposable {

  private final TableStructureVirtualFile file;
  private final JPanel component = new JPanel(new BorderLayout());
  private final JTabbedPane tabs = new JTabbedPane();
  private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
  private boolean disposed;

  public TableStructureFileEditor(TableStructureVirtualFile file) {
    this.file = file;
    component.add(new JLabel(" " + file.getKey().dataSourceId() + " / " + file.getKey().schema()
        + " / " + file.getKey().table()), BorderLayout.NORTH);
    tabs.addTab("Columns", scroll(table(new String[]{"#", "Name", "Type", "Nullable", "Default"})));
    tabs.addTab("Indexes", scroll(table(new String[]{"Name", "Unique", "Columns"})));
    tabs.addTab("Constraints", scroll(table(new String[]{"Name", "Type", "Columns"})));
    component.add(tabs, BorderLayout.CENTER);
  }

  private static JTable table(String[] columns) {
    return new JTable(new DefaultTableModel(new Object[0][], columns) {
      @Override
      public boolean isCellEditable(int row, int column) {
        return false;
      }
    });
  }

  private static JComponent scroll(JComponent content) {
    return new JScrollPane(content);
  }

  // ---- FileEditor -------------------------------------------------------------------------

  @Override
  public JComponent getComponent() {
    return component;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return tabs;
  }

  /** Label of this editor kind (used by the "editor type" switcher), not the tab title. */
  @Override
  public String getName() {
    return "Structure";
  }

  @Override
  public VirtualFile getFile() {
    return file;
  }

  @Override
  public void setState(FileEditorState state) {
    // no state (selection/scroll) is persisted
  }

  @Override
  public boolean isModified() {
    return false;
  }

  @Override
  public boolean isValid() {
    return !disposed && file.isValid();
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    changeSupport.removePropertyChangeListener(listener);
  }

  /** Called by the platform when the tab becomes selected - a good place to refresh. */
  @Override
  public void selectNotify() {
    refresh();
  }

  public void refresh() {
    if (disposed) {
      return;
    }
    // real implementation: re-introspect the table (background) and refill the three models
  }

  @Override
  public void dispose() {
    disposed = true;
    tabs.removeAll();
    component.removeAll();
  }
}
