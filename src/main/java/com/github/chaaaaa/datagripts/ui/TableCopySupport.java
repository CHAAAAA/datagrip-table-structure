package com.github.chaaaaa.datagripts.ui;

import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.DefaultListSelectionModel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * 给只读表格挂上“拖动选中单元格 / 复制”能力：
 * <ul>
 *   <li>按住左键拖动 → 起点到当前单元格的矩形区域全部选中（表格开启单元格选择模式）；</li>
 *   <li>Ctrl+C → 只复制选中的单元格，列间制表符、行间换行；序号列不参与，仅选中序号列时照常复制；</li>
 *   <li>右键菜单「复制单元格」「复制行」，整行复制制表符分隔且不含序号列。</li>
 * </ul>
 *
 * <p>走平台标准的 {@link com.intellij.openapi.actionSystem.PlatformDataKeys#COPY_PROVIDER}，
 * 即编辑器「复制」动作本来就走的那条路径；不自己监听 Ctrl+C 键位，菜单项启用状态也与选中状态一致。</p>
 */
public final class TableCopySupport {

    private TableCopySupport() {
    }

    /**
     * 注册复制能力到表格上，并返回「复制单元格 / 复制行」菜单组。
     *
     * <p>调用方负责把这个组放进自己的右键菜单；同一个表格重复调用是安全的——
     * provider 与鼠标监听只装一次（见 {@link #PROVIDER_KEY}）。</p>
     *
     * @param isRowHandle 哪些列是序号列：复制整行与复制选中单元格时都跳过，
     *                    仅当没有其他列被选中时才复制序号列本身
     */
    public static @NotNull DefaultActionGroup install(@NotNull JBTable table, @NotNull ReadOnlyTableModel model,
                                                     @NotNull IntPredicate isRowHandle) {
        Provider provider = providerOf(table, model, isRowHandle);
        return copyActions(provider);
    }

    /** 安装复制 provider（幂等），返回该表格的 provider。 */
    private static @NotNull Provider providerOf(@NotNull JBTable table, @NotNull ReadOnlyTableModel model,
                                                @NotNull IntPredicate isRowHandle) {
        // provider 存在表格自己的 client property 上：跟随表格生命周期，天然幂等且不会泄漏
        // （用静态 WeakHashMap 会因 provider 反向强引用 table 而永远回收不掉）
        Object existing = table.getClientProperty(PROVIDER_KEY);
        if (existing instanceof Provider provider) {
            return provider;
        }
        Provider provider = new Provider(table, model, isRowHandle);
        table.putClientProperty(PROVIDER_KEY, provider);
        registerCopyProvider(table, provider);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                if (!SwingUtilities.isRightMouseButton(event) && !event.isPopupTrigger()) {
                    return; // 左键拖动选择由 Swing 处理
                }
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) {
                    return;
                }
                if (table.isCellSelected(row, column)) {
                    // 右键点在已选区域内：保留拖选好的整块单元格，只把复制行的目标移到右键所在行
                    moveLeadIndex(table.getSelectionModel(), row);
                    moveLeadIndex(table.getColumnModel().getSelectionModel(), column);
                    return;
                }
                table.changeSelection(row, column, false, false);
            }
        });
        return provider;
    }

    /** 只移动 lead、不改选中范围，让依赖 lead 的「复制行」指向右键位置。 */
    private static void moveLeadIndex(@NotNull ListSelectionModel selection, int index) {
        if (selection instanceof DefaultListSelectionModel model) {
            model.moveLeadSelectionIndex(index);
        }
    }

    /** 表格 → 已注册 provider 的 client property key。 */
    private static final String PROVIDER_KEY = "tablestructure.copyProvider";

    private static @NotNull DefaultActionGroup copyActions(@NotNull Provider provider) {
        return new DefaultActionGroup(
                new EdtAction("复制单元格", "复制选中的单元格（列间制表符分隔，行间换行）") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        provider.copySelection();
                    }

                    @Override
                    public void update(@NotNull AnActionEvent event) {
                        event.getPresentation().setEnabled(provider.hasSelection());
                    }
                },
                new EdtAction("复制行", "复制选中行的全部列（制表符分隔，不含序号）") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        provider.copy(provider.rowText());
                    }

                    @Override
                    public void update(@NotNull AnActionEvent event) {
                        event.getPresentation().setEnabled(provider.selectedRow() >= 0);
                    }
                });
    }

    private static void registerCopyProvider(@NotNull JBTable table, @NotNull DataProvider provider) {
        // shortcut: registerDataProvider 在 262 标了 forRemoval，但组件级 DataProvider 目前没有等价替代
        // （平台仍靠 client property + 父链查找来喂 PlatformDataKeys.COPY_PROVIDER）；等平台给出替代 API 再换。
        @SuppressWarnings("removal")
        Runnable register = () -> DataManager.registerDataProvider(table, provider);
        register.run();
    }

    /**
     * 选中单元格的复制文本：列间制表符、行间换行，按表格显示顺序。
     * 序号列不参与，仅选中序号列时照常复制以免复制为空；没有选中单元格时返回 {@code null}。
     */
    static @Nullable String selectionText(@NotNull JTable table, @NotNull ReadOnlyTableModel model,
                                          @NotNull IntPredicate isRowHandle) {
        int[] rows = table.getSelectedRows();
        if (rows.length == 0) {
            return null;
        }
        int[] columns = Arrays.stream(table.getSelectedColumns())
                .filter(column -> !isRowHandle.test(table.convertColumnIndexToModel(column)))
                .toArray();
        if (columns.length == 0) {
            columns = table.getSelectedColumns();
        }
        if (columns.length == 0) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (int r = 0; r < rows.length; r++) {
            if (r > 0) {
                text.append('\n');
            }
            int modelRow = table.convertRowIndexToModel(rows[r]);
            for (int c = 0; c < columns.length; c++) {
                if (c > 0) {
                    text.append('\t');
                }
                text.append(model.valueAt(modelRow, table.convertColumnIndexToModel(columns[c])));
            }
        }
        return text.toString();
    }

    private static final class Provider implements CopyProvider, DataProvider {
        private final JTable table;
        private final ReadOnlyTableModel model;
        private final IntPredicate isRowHandle;

        Provider(@NotNull JTable table, @NotNull ReadOnlyTableModel model, @NotNull IntPredicate isRowHandle) {
            this.table = table;
            this.model = model;
            this.isRowHandle = isRowHandle;
        }

        @Override
        public @Nullable Object getData(@NotNull String dataId) {
            return PlatformDataKeys.COPY_PROVIDER.is(dataId) ? this : null;
        }

        @Override
        public void performCopy(@NotNull DataContext dataContext) {
            copySelection();
        }

        @Override
        public boolean isCopyEnabled(@NotNull DataContext dataContext) {
            return hasSelection();
        }

        @Override
        public boolean isCopyVisible(@NotNull DataContext dataContext) {
            return true;
        }

        /** 复制动作会在 BGT 上读 provider 状态，这里要读 Swing 选中态。 */
        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
            return ActionUpdateThread.EDT;
        }

        void copySelection() {
            copy(selectionText(table, model, isRowHandle));
        }

        boolean hasSelection() {
            return table.getSelectedRowCount() > 0 && table.getSelectedColumnCount() > 0;
        }

        void copy(@Nullable String text) {
            if (text != null) {
                CopyPasteManager.getInstance().setContents(new StringSelection(text));
            }
        }

        private int selectedRow() {
            int lead = table.getSelectionModel().getLeadSelectionIndex();
            return table.isRowSelected(lead) ? lead : table.getSelectedRow();
        }

        private @Nullable String rowText() {
            int row = selectedRow();
            if (row < 0) {
                return null;
            }
            int modelRow = table.convertRowIndexToModel(row);
            StringBuilder text = new StringBuilder();
            for (int column = 0; column < table.getColumnCount(); column++) {
                int modelColumn = table.convertColumnIndexToModel(column);
                if (isRowHandle.test(modelColumn)) {
                    continue;
                }
                if (text.length() > 0) {
                    text.append('\t');
                }
                text.append(model.valueAt(modelRow, modelColumn));
            }
            return text.toString();
        }
    }
}
