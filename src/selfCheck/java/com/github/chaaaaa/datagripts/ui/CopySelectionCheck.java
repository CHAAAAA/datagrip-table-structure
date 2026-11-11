package com.github.chaaaaa.datagripts.ui;

import javax.swing.JTable;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;

/**
 * 自检：{@link TableCopySupport} 的选中单元格复制文本（跳过序号列、制表符/换行拼接、排序后按显示顺序）。
 * 不在 src/main/java 下，不会打进插件包；由 scripts/copy-selection-check.sh 运行。
 */
public final class CopySelectionCheck {

    private static int checked;

    public static void main(String[] args) {
        // 1) 2×2 矩形区域：列间制表符、行间换行
        JTable plain = table(List.of(
                new String[]{"A1", "B1"},
                new String[]{"A2", "B2"}), "A", "B");
        select(plain, 0, 1, 0, 1);
        expect("2×2 区域", "A1\tB1\nA2\tB2", selectionText(plain, column -> false));

        // 2) 单单元格
        select(plain, 1, 1, 1, 1);
        expect("单单元格", "B2", selectionText(plain, column -> false));

        // 3) 序号列（第 0 列）不参与复制
        JTable withHandle = table(List.of(
                new String[]{"1", "张三", "表"},
                new String[]{"2", "李四", "视图"}), "#", "名称", "类型");
        select(withHandle, 0, 1, 0, 2);
        expect("跳过序号列", "张三\t表\n李四\t视图", selectionText(withHandle, column -> column == 0));

        // 4) 只选中序号列时照常复制
        select(withHandle, 0, 1, 0, 0);
        expect("只选序号列", "1\n2", selectionText(withHandle, column -> column == 0));

        // 5) 没有选中单元格
        withHandle.clearSelection();
        expect("无选中", null, selectionText(withHandle, column -> column == 0));

        // 6) 排序后按显示顺序复制（第 0 行显示的是模型第 1 行）
        JTable sorted = table(List.of(
                new String[]{"B", "2"},
                new String[]{"A", "1"}), "A", "B");
        sorted.setAutoCreateRowSorter(true);
        sorted.getRowSorter().toggleSortOrder(0);
        select(sorted, 0, 1, 0, 1);
        expect("排序后顺序", "A\t1\nB\t2", selectionText(sorted, column -> false));

        System.out.println("复制选中单元格自检通过：" + checked + " 个用例");
    }

    private static JTable table(List<String[]> rows, String... headers) {
        ReadOnlyTableModel model = new ReadOnlyTableModel(headers);
        model.setRows(rows);
        JTable table = new JTable(model);
        table.setCellSelectionEnabled(true);
        return table;
    }

    private static String selectionText(JTable table, IntPredicate isRowHandle) {
        return TableCopySupport.selectionText(table, (ReadOnlyTableModel) table.getModel(), isRowHandle);
    }

    private static void select(JTable table, int firstRow, int lastRow, int firstColumn, int lastColumn) {
        table.setRowSelectionInterval(firstRow, lastRow);
        table.setColumnSelectionInterval(firstColumn, lastColumn);
    }

    private static void expect(String name, Object expected, Object actual) {
        checked++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(name + "：期望\n[" + expected + "]\n实际\n[" + actual + "]");
        }
    }
}
