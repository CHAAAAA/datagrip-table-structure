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

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.IntPredicate;

/**
 * 给只读表格挂上“复制单元格 / 复制整行”能力：
 * <ul>
 *   <li>点单元格 → Ctrl+C 复制该单元格文本；</li>
 *   <li>点序号列（行把手）→ 选中整行，Ctrl+C 复制整行所有列，制表符分隔，序号列不参与；</li>
 *   <li>右键菜单「复制单元格」「复制整行」，规则同上。</li>
 * </ul>
 *
 * <p>走平台标准的 {@link com.intellij.openapi.actionSystem.PlatformDataKeys#COPY_PROVIDER}，
 * 即编辑器「复制」动作本来就走的那条路径；不自己监听 Ctrl+C 键位，菜单项启用状态也与选中状态一致。</p>
 */
public final class TableCopySupport {

    private TableCopySupport() {
    }

    /**
     * 注册复制能力到表格上，并返回「复制单元格 / 复制整行」菜单组。
     *
     * <p>调用方负责把这个组放进自己的右键菜单；同一个表格重复调用是安全的——
     * provider 与鼠标监听只装一次（见 {@link #PROVIDER_KEY}）。</p>
     *
     * @param isRowHandle 哪些列是序号/行把手列：点这些列等于选中整行，复制整行时也不输出
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
                    return; // 左键选择（含 Shift / Ctrl / Meta 多选）由 Swing 处理
                }
                int row = table.rowAtPoint(event.getPoint());
                int column = table.columnAtPoint(event.getPoint());
                if (row < 0 || column < 0) {
                    return;
                }
                if (table.isRowSelected(row)) {
                    // 保留多选，同时把复制目标移到右键所在行。
                    table.addRowSelectionInterval(row, row);
                } else {
                    table.setRowSelectionInterval(row, row);
                }
                table.setColumnSelectionInterval(column, column);
            }
        });
        return provider;
    }

    /** 表格 → 已注册 provider 的 client property key。 */
    private static final String PROVIDER_KEY = "tablestructure.copyProvider";

    private static @NotNull DefaultActionGroup copyActions(@NotNull Provider provider) {
        return new DefaultActionGroup(
                new EdtAction("复制单元格", "复制选中单元格的内容") {
                    @Override
                    public void actionPerformed(@NotNull AnActionEvent event) {
                        provider.copy(provider.cellOrWholeRowText());
                    }

                    @Override
                    public void update(@NotNull AnActionEvent event) {
                        event.getPresentation().setEnabled(provider.cellOrWholeRowText() != null);
                    }
                },
                new EdtAction("复制整行", "复制选中行的全部列（制表符分隔，不含序号）") {
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
            copy(cellOrWholeRowText());
        }

        @Override
        public boolean isCopyEnabled(@NotNull DataContext dataContext) {
            return cellOrWholeRowText() != null;
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

        void copy(@Nullable String text) {
            if (text != null) {
                CopyPasteManager.getInstance().setContents(new StringSelection(text));
            }
        }

        private int selectedRow() {
            int lead = table.getSelectionModel().getLeadSelectionIndex();
            return table.isRowSelected(lead) ? lead : table.getSelectedRow();
        }

        private int selectedColumn() {
            int lead = table.getColumnModel().getSelectionModel().getLeadSelectionIndex();
            return table.isColumnSelected(lead) ? lead : table.getSelectedColumn();
        }

        /** 序号列没有可复制的数据，回退成整行。 */
        private @Nullable String cellOrWholeRowText() {
            int row = selectedRow();
            int column = selectedColumn();
            if (row < 0 || column < 0) {
                return null;
            }
            if (isRowHandle.test(table.convertColumnIndexToModel(column))) {
                return rowText();
            }
            return model.valueAt(table.convertRowIndexToModel(row), table.convertColumnIndexToModel(column));
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
