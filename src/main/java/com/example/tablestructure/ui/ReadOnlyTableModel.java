package com.example.tablestructure.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.table.AbstractTableModel;
import java.util.List;

/**
 * 只读、纯字符串表格模型。所有数据加载完成后一次性替换，避免在 EDT 上做数据库模型访问。
 */
public final class ReadOnlyTableModel extends AbstractTableModel {

    private final String[] columnNames;
    private List<String[]> rows = List.of();

    public ReadOnlyTableModel(@NotNull String... columnNames) {
        this.columnNames = columnNames;
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
    public Object getValueAt(int rowIndex, int columnIndex) {
        return rows.get(rowIndex)[columnIndex];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }
}
