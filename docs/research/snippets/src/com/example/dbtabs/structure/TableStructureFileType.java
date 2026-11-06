package com.example.dbtabs.structure;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import javax.swing.Icon;

/**
 * Dedicated {@link FileType} so that:
 * <ul>
 *   <li>the editor tab gets a stable icon (FileType#getIcon),</li>
 *   <li>our FileEditorProvider can be pre-filtered in plugin.xml with
 *       {@code fileType="DataGripTableStructure"} (verified attribute, see report),</li>
 *   <li>no other provider / text editor is chosen for these files.</li>
 * </ul>
 *
 * Registered via {@code <fileType ... fieldName="INSTANCE"/>} (FileTypeFactory is deprecated).
 */
public final class TableStructureFileType implements FileType {

  /** Name is what plugin.xml {@code fileType="..."} must reference. */
  public static final String NAME = "DataGripTableStructure";

  /** Required by the {@code <fileType fieldName="INSTANCE"/>} bean. */
  public static final TableStructureFileType INSTANCE = new TableStructureFileType();

  private TableStructureFileType() {
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Database table structure";
  }

  @Override
  public String getDefaultExtension() {
    return "tablestructure";
  }

  @Override
  public Icon getIcon() {
    // verified to exist in 2026.2: com.intellij.icons.AllIcons.Nodes.DataTables
    return AllIcons.Nodes.DataTables;
  }

  /**
   * Binary: prevents the platform from building a Document for the virtual file.
   * We do not need a Document, and this keeps the text editor provider away.
   */
  @Override
  public boolean isBinary() {
    return true;
  }
}
