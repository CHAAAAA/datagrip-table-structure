package com.example.tablestructure.ui;

import com.example.tablestructure.model.ObjectEntry;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 对象总览面板：搜索框 + 类型过滤 + 计数 + 只读表格。
 * 只做渲染与过滤，模型访问在 {@code ObjectBrowserLoader} 中、读锁内完成。
 */
public final class ObjectBrowserPanel extends JPanel {

    private static final String ALL_GROUPS = "全部";
    private static final String[] HEADERS = {"对象名", "类型", "Schema", "注释"};
    private static final int[] WIDTHS = {260, 100, 140, 320};

    private final SearchTextField searchField = new SearchTextField();
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

    public ObjectBrowserPanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel controls = new JPanel(new BorderLayout(8, 0));
        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.add(searchField, BorderLayout.CENTER);
        left.add(groupBox, BorderLayout.EAST);
        controls.add(left, BorderLayout.CENTER);
        controls.add(refreshButton, BorderLayout.EAST);

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(controls, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);
        add(new JBScrollPane(table), BorderLayout.CENTER);

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

        showStatus("正在加载对象…");
    }

    public void setRefreshAction(@NotNull Runnable action) {
        refreshButton.addActionListener(event -> action.run());
    }

    /** 双击行为由编辑器注入（表/视图 → 打开结构 tab）。 */
    public void setOpenAction(@NotNull Consumer<ObjectEntry> action) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) {
                    return;
                }
                ObjectEntry entry = selectedEntry();
                if (entry != null) {
                    action.accept(entry);
                }
            }
        });
    }

    public @NotNull JComponent getPreferredFocusedComponent() {
        return searchField;
    }

    public void showLoading() {
        showStatus("正在加载对象…");
        refreshButton.setEnabled(false);
    }

    public void showError(@NotNull String message) {
        showStatus("加载失败：" + message);
        refreshButton.setEnabled(true);
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
        reapply();
    }

    private void reapply() {
        String group = (String) groupBox.getSelectedItem();
        visible = ObjectFilter.filter(all, searchField.getText(), ALL_GROUPS.equals(group) ? null : group);
        model.setRows(rows(visible));
        if (!loaded) {
            showStatus("正在加载对象…");
        } else if (all.isEmpty()) {
            showStatus("没有对象。可能是数据源未加载，或内省级别未包含序列/存储过程——"
                    + "请在数据源属性中提高内省级别后点击“刷新”。");
        } else {
            showStatus("匹配 " + visible.size() + " / 共 " + all.size() + " 个对象（双击表或视图可查看结构）");
        }
    }

    private @Nullable ObjectEntry selectedEntry() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return null;
        }
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow >= 0 && modelRow < visible.size() ? visible.get(modelRow) : null;
    }

    private static @NotNull List<String[]> rows(@NotNull List<ObjectEntry> entries) {
        List<String[]> rows = new ArrayList<>(entries.size());
        for (ObjectEntry entry : entries) {
            rows.add(new String[]{entry.name(), entry.group(), entry.schema(), entry.comment()});
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
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int i = 0; i < WIDTHS.length && i < table.getColumnModel().getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
        }
        return table;
    }
}
