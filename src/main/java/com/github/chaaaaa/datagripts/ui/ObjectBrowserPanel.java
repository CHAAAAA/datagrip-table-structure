package com.github.chaaaaa.datagripts.ui;

import com.github.chaaaaa.datagripts.model.ObjectEntry;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对象总览面板：schema 切换 + 搜索框 + 类型过滤 + 计数 + 只读表格 + 右键菜单。
 * 只做渲染与过滤，模型访问在 {@code ObjectBrowserLoader} 中、读锁内完成。
 *
 * <p>表格只持有当前 schema 的对象：切换 schema 由编辑器回调重新取数（一次只查一个 schema）。</p>
 */
public final class ObjectBrowserPanel extends JPanel {

    private static final String ALL_GROUPS = "全部";
    /** 第 0 列是行号：点击它选中整行，复制整行时跳过。 */
    private static final String[] HEADERS = {"#", "对象名", "类型", "Schema", "注释"};
    private static final int[] WIDTHS = {40, 260, 110, 130, 340};

    private final SearchTextField searchField = new SearchTextField();
    private final JComboBox<String> schemaBox = new JComboBox<>();
    private final JComboBox<String> groupBox = new JComboBox<>(
            new String[]{ALL_GROUPS, ObjectEntry.GROUP_TABLE, ObjectEntry.GROUP_VIEW,
                    ObjectEntry.GROUP_SEQUENCE, ObjectEntry.GROUP_ROUTINE});
    private final JButton refreshButton = new JButton("刷新");
    private final JBLabel statusLabel = new JBLabel();

    private final ReadOnlyTableModel model = new ReadOnlyTableModel(HEADERS);
    private final JBTable table = createTable(model);

    private List<ObjectEntry> all = List.of();
    private List<ObjectEntry> visible = List.of();
    private boolean loaded;

    /** 切换 schema 时回调（参数是新 schema）；null 表示本次变更不应触发重新取数。 */
    private Consumer<String> schemaChangeAction = ignored -> { };
    private Consumer<ObjectEntry> openAction = ignored -> { };
    private Consumer<ObjectEntry> editAction = ignored -> { };
    private Consumer<ObjectEntry> viewDataAction = ignored -> { };
    private boolean adjustingSchemaBox;
    private boolean hasOpenListener;

    public ObjectBrowserPanel() {
        super(new BorderLayout());
        setBorder(JBUI.Borders.empty(6));

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.add(searchField, BorderLayout.CENTER);
        left.add(groupBox, BorderLayout.EAST);

        JPanel controls = new JPanel(new BorderLayout(8, 0));
        controls.add(schemaLabel(), BorderLayout.WEST);
        controls.add(left, BorderLayout.CENTER);
        controls.add(refreshButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(controls, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JBScrollPane(table), BorderLayout.CENTER);

        refreshButton.setIcon(AllIcons.Actions.Refresh);
        statusLabel.setForeground(UIUtil.getContextHelpForeground());

        searchField.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                reapply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                reapply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                reapply();
            }
        });
        groupBox.addActionListener(event -> reapply());
        schemaBox.addActionListener(event -> {
            // 程序化填充下拉项时不该触发重新取数
            if (adjustingSchemaBox) {
                return;
            }
            Object selected = schemaBox.getSelectedItem();
            if (selected != null) {
                schemaChangeAction.accept((String) selected);
            }
        });

        installPopup();

        showStatus("正在加载对象…");
    }

    public void setRefreshAction(@NotNull Runnable action) {
        refreshButton.addActionListener(event -> action.run());
    }

    /** schema 下拉切换回调。 */
    public void setSchemaChangeAction(@NotNull Consumer<String> action) {
        this.schemaChangeAction = action;
    }

    /** 双击行为由编辑器注入（表/视图 → 打开结构 tab）。 */
    public void setOpenAction(@NotNull Consumer<ObjectEntry> action) {
        this.openAction = action;
        if (!hasOpenListener) {
            hasOpenListener = true;
            table.addMouseListener(new MouseAdapter() {
                private long previousClickTime;
                private ObjectEntry previousEntry;

                @Override
                public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || event.isControlDown()) {
                        previousEntry = null;
                    }
                }

                @Override
                public void mouseClicked(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || event.isPopupTrigger() || event.isControlDown()) {
                        previousEntry = null;
                        return;
                    }
                    ObjectEntry entry = table.columnAtPoint(event.getPoint()) < 0
                            ? null : entryAtRow(table.rowAtPoint(event.getPoint()));
                    Object interval = Toolkit.getDefaultToolkit().getDesktopProperty("awt.multiClickInterval");
                    long doubleClickInterval = interval instanceof Number number && number.longValue() > 0
                            ? number.longValue() : 500;
                    long elapsed = event.getWhen() - previousClickTime;
                    boolean doubleClick = entry != null && entry == previousEntry && event.getClickCount() == 2
                            && elapsed >= 0 && elapsed <= doubleClickInterval;
                    previousClickTime = event.getWhen();
                    previousEntry = entry;
                    if (doubleClick) {
                        previousEntry = null;
                        openAction.accept(entry);
                    }
                }
            });
        }
    }

    /** 右键「修改表结构」回调（仅表）。 */
    public void setEditAction(@NotNull Consumer<ObjectEntry> action) {
        this.editAction = action;
    }

    /** 右键「查看数据」回调（仅表）。 */
    public void setViewDataAction(@NotNull Consumer<ObjectEntry> action) {
        this.viewDataAction = action;
    }

    public @NotNull JComponent getPreferredFocusedComponent() {
        return searchField;
    }

    /**
     * 填充 schema 下拉项并选中 {@code selected}（选中不触发回调）。
     * 必须在 EDT 调用。
     */
    public void showSchemas(@NotNull List<String> schemas, @Nullable String selected) {
        adjustingSchemaBox = true;
        try {
            String current = (String) schemaBox.getSelectedItem();
            boolean same = schemas.size() == schemaBox.getItemCount();
            for (int i = 0; same && i < schemas.size(); i++) {
                same = schemas.get(i).equals(schemaBox.getItemAt(i));
            }
            if (!same) {
                schemaBox.removeAllItems();
                for (String schema : schemas) {
                    schemaBox.addItem(schema);
                }
            }
            String target = selected != null && schemas.contains(selected)
                    ? selected
                    : (current != null && schemas.contains(current) ? current : null);
            if (target != null) {
                schemaBox.setSelectedItem(target);
            } else if (schemaBox.getItemCount() > 0) {
                schemaBox.setSelectedIndex(0);
            }
        } finally {
            adjustingSchemaBox = false;
        }
        schemaBox.setEnabled(schemaBox.getItemCount() > 1);
    }

    /** 当前选中的 schema；下拉为空时返回 null。 */
    public @Nullable String getSelectedSchema() {
        Object selected = schemaBox.getSelectedItem();
        return selected == null ? null : (String) selected;
    }

    public void showLoading() {
        showStatus("正在加载对象…");
        refreshButton.setEnabled(false);
        table.setPaintBusy(true);
    }

    public void showError(@NotNull String message) {
        showStatus("加载失败：" + message);
        refreshButton.setEnabled(true);
        table.setPaintBusy(false);
    }

    /** 中性提示（如"暂不支持"），不改变加载状态。 */
    public void showMessage(@NotNull String message) {
        showStatus(message);
    }

    /** 展示新的全量对象列表（必须在 EDT 调用）。 */
    public void showObjects(@NotNull List<ObjectEntry> entries) {
        all = List.copyOf(entries);
        loaded = true;
        refreshButton.setEnabled(true);
        table.setPaintBusy(false);
        reapply();
    }

    private void reapply() {
        String group = (String) groupBox.getSelectedItem();
        visible = ObjectFilter.filter(all, searchField.getText(), ALL_GROUPS.equals(group) ? null : group);
        model.setRows(rows(visible));
        if (!loaded) {
            showStatus("正在加载对象…");
        } else if (getSelectedSchema() == null) {
            showStatus("没有可用的 schema。请确认数据源已连接并完成内省。");
        } else if (all.isEmpty()) {
            showStatus("schema “" + getSelectedSchema() + "” 的已内省模型中未找到表/视图/序列/存储过程。"
                    + "请检查 schema、权限和内省设置，先在 DataGrip 刷新数据源，再刷新此列表。");
        } else {
            showStatus("schema “" + getSelectedSchema() + "” · 匹配 " + visible.size()
                    + " / 共 " + all.size() + " 个对象（快速左键双击打开结构，右键显示操作菜单）");
        }
    }

    private @Nullable ObjectEntry selectedEntry() {
        int lead = table.getSelectionModel().getLeadSelectionIndex();
        return entryAtRow(table.isRowSelected(lead) ? lead : table.getSelectedRow());
    }

    private @Nullable ObjectEntry entryAtRow(int row) {
        if (row < 0 || row >= table.getRowCount()) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow >= 0 && modelRow < visible.size() ? visible.get(modelRow) : null;
    }

    /** 右键菜单：复制、查看结构、修改结构、查看数据、刷新。 */
    private void installPopup() {
        DefaultActionGroup group = TableCopySupport.install(table, model, column -> column == 0);
        group.add(Separator.getInstance());
        group.add(new EdtAction("查看表结构", "在编辑器标签页中打开选中的表/视图结构", AllIcons.Actions.Preview) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                if (entry != null) {
                    openAction.accept(entry);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                event.getPresentation().setEnabledAndVisible(entry != null && entry.tableLike());
            }
        });
        group.add(new EdtAction("修改表结构", "用 DataGrip 原生「修改表」对话框修改列/索引/约束", AllIcons.Actions.EditSource) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                if (entry != null) {
                    editAction.accept(entry);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                event.getPresentation().setEnabledAndVisible(entry != null && ObjectEntry.GROUP_TABLE.equals(entry.group()));
            }
        });
        group.add(new EdtAction("查看数据", "打开 DataGrip 原生数据编辑标签页") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                if (entry != null) {
                    viewDataAction.accept(entry);
                }
            }

            @Override
            public void update(@NotNull AnActionEvent event) {
                ObjectEntry entry = selectedEntry();
                event.getPresentation().setEnabledAndVisible(entry != null && ObjectEntry.GROUP_TABLE.equals(entry.group()));
            }
        });
        group.add(Separator.getInstance());
        group.add(new EdtAction("刷新", "重新枚举当前 schema 的对象", AllIcons.Actions.Refresh) {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                refreshButton.doClick();
            }
        });

        // 用 row-selection 版本：右键会先选中鼠标所在行，避免误操作到上一次选中的行
        PopupHandler.installRowSelectionTablePopup(table, group, ActionPlaces.UNKNOWN);
    }

    private @NotNull JPanel schemaLabel() {
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        JBLabel label = new JBLabel("Schema:");
        label.setForeground(UIUtil.getContextHelpForeground());
        panel.add(label, BorderLayout.WEST);
        panel.add(schemaBox, BorderLayout.CENTER);
        panel.setBorder(JBUI.Borders.emptyRight(4));
        return panel;
    }

    private static @NotNull List<String[]> rows(@NotNull List<ObjectEntry> entries) {
        List<String[]> rows = new ArrayList<>(entries.size());
        for (ObjectEntry entry : entries) {
            // 第 0 列留空：序号由渲染器按“当前显示顺序”画，排序后不会出现 3/1/2 这种乱序
            rows.add(new String[]{"", entry.name(), entry.group(), entry.schema(), entry.comment()});
        }
        return rows;
    }

    private void showStatus(@NotNull String message) {
        statusLabel.setText(message);
    }

    private static @NotNull JBTable createTable(@NotNull ReadOnlyTableModel model) {
        JBTable table = new JBTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowSelectionAllowed(true);
        table.setFillsViewportHeight(true);
        table.setStriped(true);
        table.setShowVerticalLines(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        for (int i = 0; i < WIDTHS.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
        }
        // 第 0 列是序号：按“当前显示顺序”画，排序/过滤后仍是 1,2,3…
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, String.valueOf(row + 1), selected, focus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                return this;
            }
        });
        return table;
    }
}
