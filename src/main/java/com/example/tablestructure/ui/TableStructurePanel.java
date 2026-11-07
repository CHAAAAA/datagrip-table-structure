package com.example.tablestructure.ui;

import com.example.tablestructure.model.ColumnInfo;
import com.example.tablestructure.model.ForeignKeyInfo;
import com.example.tablestructure.model.IndexInfo;
import com.example.tablestructure.model.KeyInfo;
import com.example.tablestructure.model.TableInfo;
import com.example.tablestructure.model.TableStructure;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTabbedPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * 表结构查看器主面板：
 * <ul>
 *   <li>顶部：表总体信息 + 刷新按钮 + 状态栏</li>
 *   <li>中间：列 / 索引 / 约束 / 外键 四个 Tab，每个 Tab 是一个只读表格</li>
 * </ul>
 * 只做渲染，不做任何数据库模型访问（模型访问在 {@code TableStructureLoader} 中、读锁内完成）。
 */
public final class TableStructurePanel extends JPanel {

    private static final int TAB_COLUMNS = 0;
    private static final int TAB_INDEXES = 1;
    private static final int TAB_KEYS = 2;
    private static final int TAB_FOREIGN_KEYS = 3;

    private static final String[] COLUMN_HEADERS = {
            "#", "列名", "类型", "长度", "精度", "标度", "非空", "默认值", "主键", "外键", "自增", "计算列", "注释"
    };
    private static final int[] COLUMN_WIDTHS = {40, 160, 170, 60, 60, 60, 55, 140, 55, 55, 55, 65, 300};
    /** 列 Tab 中需要按数字右对齐的下标：# / 长度 / 精度 / 标度。 */
    private static final int[] COLUMN_RIGHT_ALIGNED = {0, 3, 4, 5};
    /** 列 Tab 的序号列：点它选中整行，复制整行时跳过。 */
    private static final IntPredicate COLUMN_IS_ROW_HANDLE = column -> column == 0;

    private static final String[] INDEX_HEADERS = {"索引名", "唯一", "函数索引", "索引列", "注释"};
    private static final int[] INDEX_WIDTHS = {180, 55, 75, 260, 280};

    private static final String[] KEY_HEADERS = {"约束名", "类型", "涉及列"};
    private static final int[] KEY_WIDTHS = {180, 75, 280};

    private static final String[] FOREIGN_KEY_HEADERS = {"外键名", "列", "引用表", "引用列", "ON DELETE", "ON UPDATE", "延迟性"};
    private static final int[] FOREIGN_KEY_WIDTHS = {180, 180, 180, 180, 95, 95, 130};

    private final JBLabel infoLabel = new JBLabel();
    private final JBLabel statusLabel = new JBLabel();
    private final JButton refreshButton = new JButton("刷新", AllIcons.Actions.Refresh);
    private final JBTabbedPane tabs = new JBTabbedPane();

    /** 当前展示的结构快照（选中行 → 列 的映射要用）。 */
    @Nullable
    private TableStructure structure;
    /** 生成 ALTER DDL 的回调；由编辑器注入。 */
    @Nullable
    private Consumer<List<ColumnInfo>> alterDdlAction;

    private final ReadOnlyTableModel columnModel = new ReadOnlyTableModel(COLUMN_HEADERS, COLUMN_RIGHT_ALIGNED);
    private final ReadOnlyTableModel indexModel = new ReadOnlyTableModel(INDEX_HEADERS);
    private final ReadOnlyTableModel keyModel = new ReadOnlyTableModel(KEY_HEADERS);
    private final ReadOnlyTableModel foreignKeyModel = new ReadOnlyTableModel(FOREIGN_KEY_HEADERS);

    private final JBTable columnTable = createTable(columnModel, COLUMN_WIDTHS);
    private final JBTable indexTable = createTable(indexModel, INDEX_WIDTHS);
    private final JBTable keyTable = createTable(keyModel, KEY_WIDTHS);
    private final JBTable foreignKeyTable = createTable(foreignKeyModel, FOREIGN_KEY_WIDTHS);

    public TableStructurePanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(8, 8, 8, 8));
        infoLabel.setBorder(JBUI.Borders.empty(0, 0, 6, 0));
        statusLabel.setFont(JBUI.Fonts.smallFont());
        statusLabel.setForeground(UIUtil.getContextHelpForeground());

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.add(infoLabel, BorderLayout.CENTER);
        header.add(refreshButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        tabs.addTab("列", scrollPane(columnTable));
        tabs.addTab("索引", scrollPane(indexTable));
        tabs.addTab("约束", scrollPane(keyTable));
        tabs.addTab("外键", scrollPane(foreignKeyTable));
        add(tabs, BorderLayout.CENTER);

        installCopy();

        showStatus("从左侧数据库对象树中选择一张表，或点击“查看表结构”");
    }

    /** 由编辑器注入刷新逻辑（重新读取 DataGrip 模型）。 */
    public void setRefreshAction(@NotNull Runnable action) {
        refreshButton.addActionListener(event -> action.run());
    }

    /**
     * 由编辑器注入「生成 ALTER DDL」逻辑：参数是选中的列。
     * 未注入时列 Tab 没有这个菜单项。
     */
    public void setAlterDdlAction(@NotNull Consumer<List<ColumnInfo>> action) {
        this.alterDdlAction = action;
        // 菜单项的启用状态是弹出时由 update() 现算的，这里不必重建菜单
    }

    /** 选中行对应的列，按表格显示顺序（多选/排序后都对得上）。 */
    private @NotNull List<ColumnInfo> selectedColumns() {
        int[] viewRows = columnTable.getSelectedRows();
        if (viewRows.length == 0 || structure == null) {
            return List.of();
        }
        List<ColumnInfo> columns = structure.columns();
        List<ColumnInfo> selected = new ArrayList<>(viewRows.length);
        for (int viewRow : viewRows) {
            int modelRow = columnTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < columns.size()) {
                selected.add(columns.get(modelRow));
            }
        }
        return selected;
    }

    /**
     * 四张表都挂上复制能力；列 Tab 的第 0 列（{@code #}）是序号列：点它选整行，复制整行时跳过它。
     * 列 Tab 的右键菜单在复制项之后追加「生成 ALTER DDL」。
     */
    private void installCopy() {
        installCopy(indexTable, indexModel, column -> false);
        installCopy(keyTable, keyModel, column -> false);
        installCopy(foreignKeyTable, foreignKeyModel, column -> false);
        rebuildColumnPopup();
    }

    /**
     * 重建列 Tab 的右键菜单：复制项 + 「生成 ALTER DDL」。
     *
     * <p>一个表只装一个 popup handler——复制和生成 DDL 共用同一个右键菜单，
     * 避免两条 MouseListener 各弹一个菜单。菜单项在弹出时由 update() 按选中行/列决定启用状态。</p>
     */
    private void rebuildColumnPopup() {
        DefaultActionGroup group = TableCopySupport.install(columnTable, columnModel, COLUMN_IS_ROW_HANDLE);
        group.add(Separator.getInstance());
        group.add(new EdtAction("生成 ALTER DDL…", "生成新增选中列的 ALTER TABLE 语句（含注释）") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                List<ColumnInfo> selected = selectedColumns();
                if (alterDdlAction != null && !selected.isEmpty()) {
                    alterDdlAction.accept(selected);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                event.getPresentation().setEnabledAndVisible(alterDdlAction != null && !selectedColumns().isEmpty());
            }
        });
        PopupHandler.installRowSelectionTablePopup(columnTable, group, ActionPlaces.UNKNOWN);
    }

    private static void installCopy(@NotNull JBTable table, @NotNull ReadOnlyTableModel model,
                                    @NotNull IntPredicate isRowHandle) {
        PopupHandler.installRowSelectionTablePopup(table,
                TableCopySupport.install(table, model, isRowHandle), ActionPlaces.UNKNOWN);
    }

    public @NotNull JComponent getPreferredFocusedComponent() {
        return columnTable;
    }

    public void showLoading() {
        showStatus("正在加载表结构…");
        refreshButton.setEnabled(false);
        setBusy(true);
    }

    public void showError(@NotNull String message) {
        showStatus("加载失败：" + message);
        refreshButton.setEnabled(true);
        setBusy(false);
    }

    /** 展示一份新的结构快照（必须在 EDT 调用）。 */
    public void showStructure(@NotNull TableStructure structure) {
        this.structure = structure;
        TableInfo info = structure.table();
        infoLabel.setText(buildInfoHtml(info));

        tabs.setTitleAt(TAB_COLUMNS, "列 (" + structure.columns().size() + ")");
        tabs.setTitleAt(TAB_INDEXES, "索引 (" + structure.indexes().size() + ")");
        tabs.setTitleAt(TAB_KEYS, "约束 (" + structure.keys().size() + ")");
        tabs.setTitleAt(TAB_FOREIGN_KEYS, "外键 (" + structure.foreignKeys().size() + ")");

        columnModel.setRows(columnRows(structure.columns()));
        indexModel.setRows(indexRows(structure.indexes()));
        keyModel.setRows(keyRows(structure.keys()));
        foreignKeyModel.setRows(foreignKeyRows(structure.foreignKeys()));

        refreshButton.setEnabled(true);
        setBusy(false);
        if (structure.columns().isEmpty()) {
            showStatus("没有读到列信息——可能是权限不足、视图无定义，或对象尚未加载；可点击“刷新”重试");
        } else {
            showStatus("就绪 · " + structure.columns().size() + " 列 / "
                    + structure.indexes().size() + " 索引 / "
                    + structure.keys().size() + " 约束 / "
                    + structure.foreignKeys().size() + " 外键");
        }
    }

    // ------------------------------------------------------------------ rows

    private static @NotNull List<String[]> columnRows(@NotNull List<ColumnInfo> columns) {
        List<String[]> rows = new ArrayList<>(columns.size());
        for (ColumnInfo column : columns) {
            rows.add(new String[]{
                    String.valueOf(column.position()),
                    column.name(),
                    column.typeDisplay(),
                    number(column.length()),
                    number(column.precision()),
                    number(column.scale()),
                    column.notNull() ? "是" : "",
                    column.defaultValue(),
                    column.primaryKey() ? "是" : "",
                    column.foreignKey() ? "是" : "",
                    column.autoGenerated() ? "是" : "",
                    column.computed() ? "是" : "",
                    column.comment()
            });
        }
        return rows;
    }

    private static @NotNull List<String[]> indexRows(@NotNull List<IndexInfo> indexes) {
        List<String[]> rows = new ArrayList<>(indexes.size());
        for (IndexInfo index : indexes) {
            rows.add(new String[]{
                    index.name(),
                    index.unique() ? "是" : "",
                    index.functionBased() ? "是" : "",
                    index.columns(),
                    index.comment()
            });
        }
        return rows;
    }

    private static @NotNull List<String[]> keyRows(@NotNull List<KeyInfo> keys) {
        List<String[]> rows = new ArrayList<>(keys.size());
        for (KeyInfo key : keys) {
            rows.add(new String[]{
                    key.name(),
                    key.primary() ? "主键" : "唯一",
                    key.columns()
            });
        }
        return rows;
    }

    private static @NotNull List<String[]> foreignKeyRows(@NotNull List<ForeignKeyInfo> foreignKeys) {
        List<String[]> rows = new ArrayList<>(foreignKeys.size());
        for (ForeignKeyInfo foreignKey : foreignKeys) {
            rows.add(new String[]{
                    foreignKey.name(),
                    foreignKey.columns(),
                    foreignKey.refTable(),
                    foreignKey.refColumns(),
                    foreignKey.onDelete(),
                    foreignKey.onUpdate(),
                    foreignKey.deferrability()
            });
        }
        return rows;
    }

    // ------------------------------------------------------------------ helpers

    private static @NotNull JBTable createTable(@NotNull ReadOnlyTableModel model, @NotNull int[] preferredWidths) {
        JBTable table = new JBTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setStriped(true);
        table.setRowHeight(JBUI.scale(22));
        table.setShowVerticalLines(false);
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < preferredWidths.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(preferredWidths[i]);
        }
        return table;
    }

    private static @NotNull JBScrollPane scrollPane(@NotNull JBTable table) {
        return new JBScrollPane(table);
    }

    /** 加载中打开 JBTable 自带的 busy 动画，让“正在读取”可感知。 */
    private void setBusy(boolean busy) {
        for (JBTable table : List.of(columnTable, indexTable, keyTable, foreignKeyTable)) {
            table.setPaintBusy(busy);
        }
    }

    private void showStatus(@NotNull String message) {
        statusLabel.setText(message);
    }

    private static @NotNull String number(int value) {
        return value > 0 ? String.valueOf(value) : "";
    }

    private static @NotNull String buildInfoHtml(@NotNull TableInfo info) {
        StringBuilder html = new StringBuilder("<html><font size=+1><b>");
        html.append(escape(info.qualifiedName())).append("</b>");
        if (!info.kind().isEmpty()) {
            html.append(" &nbsp;·&nbsp; ").append(escape(info.kind()));
        }
        if (!info.comment().isEmpty()) {
            html.append(" &nbsp;·&nbsp; ").append(escape(info.comment()));
        }
        html.append("</font><br><font color=\"#").append(ColorUtil.toHex(UIUtil.getContextHelpForeground())).append("\">");
        html.append("数据源: ").append(escape(info.dataSourceName()));
        html.append(" &nbsp;|&nbsp; DBMS: ").append(escape(info.dbmsName()));
        if (!info.dbmsVersion().isEmpty()) {
            html.append(' ').append(escape(info.dbmsVersion()));
        }
        html.append(" &nbsp;|&nbsp; 列 ").append(info.columnCount())
                .append(" · 索引 ").append(info.indexCount())
                .append(" · 约束 ").append(info.keyCount())
                .append(" · 外键 ").append(info.foreignKeyCount());
        if (info.temporary()) {
            html.append(" &nbsp;|&nbsp; 临时表");
        }
        if (info.system()) {
            html.append(" &nbsp;|&nbsp; 系统表");
        }
        html.append("</font></html>");
        return html.toString();
    }

    private static @NotNull String escape(@NotNull String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
