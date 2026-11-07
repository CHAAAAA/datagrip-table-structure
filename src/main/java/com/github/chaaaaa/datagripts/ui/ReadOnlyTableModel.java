package com.github.chaaaaa.datagripts.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * 只读、纯字符串表格模型。所有数据加载完成后一次性替换，避免在 EDT 上做数据库模型访问。
 * 标记为右对齐的列对 {@link #getColumnClass(int)} 报告 {@link Integer}，从而复用 Swing 自带的数字渲染器（右对齐）。
 */
public final class ReadOnlyTableModel extends AbstractTableModel {

    private final String[] columnNames;
    private final boolean[] rightAligned;
    private List<String[]> rows = List.of();

    public ReadOnlyTableModel(@NotNull String... columnNames) {
        this(columnNames, new int[0]);
    }

    /**
     * @param rightAlignedColumns 需要按数字右对齐的列下标；这些列的值必须是十进制整数或空串
     */
    public ReadOnlyTableModel(@NotNull String[] columnNames, @NotNull int[] rightAlignedColumns) {
        this.columnNames = columnNames;
        this.rightAligned = new boolean[columnNames.length];
        for (int index : rightAlignedColumns) {
            if (index >= 0 && index < rightAligned.length) {
                rightAligned[index] = true;
            }
        }
    }

    public void setRows(@NotNull List<String[]> newRows) {
        rows = List.copyOf(newRows);
        fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        // 必须是 Integer 而非 Number：TableRowSorter 只在类实现 Comparable 时按数值比较，
        // 返回 Number 会静默退化成字符串排序（9 排在 100 之后）。
        return rightAligned[columnIndex] ? Integer.class : String.class;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        String value = rows.get(rowIndex)[columnIndex];
        if (!rightAligned[columnIndex]) {
            return value;
        }
        // 右对齐列必须交给排序器真正可比的 Integer：Number 列混入字符串时点击表头会抛 ClassCastException
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * 单元格的原始文本（与 {@link #getValueAt} 的区别：右对齐列返回字符串而非 {@link Integer}）。
     * 复制到剪贴板时用这个，避免把 {@code null}（空值）复制成 "null"。
     */
    public @NotNull String valueAt(int rowIndex, int columnIndex) {
        String value = rows.get(rowIndex)[columnIndex];
        return value == null ? "" : value;
    }
}
