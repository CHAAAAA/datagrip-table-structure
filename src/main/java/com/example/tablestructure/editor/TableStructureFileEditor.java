package com.example.tablestructure.editor;

import com.example.tablestructure.model.ColumnInfo;
import com.example.tablestructure.model.TableStructure;
import com.example.tablestructure.service.AlterDdlGenerator;
import com.example.tablestructure.service.TableRefreshService;
import com.example.tablestructure.service.TableStructureLoader;
import com.example.tablestructure.ui.AlterDdlDialog;
import com.example.tablestructure.ui.TableStructurePanel;
import com.intellij.database.psi.DbPsiFacade;
import com.intellij.database.psi.DbTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.CancellablePromise;

import javax.swing.JComponent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;

/**
 * 表结构查看器的编辑器实现：一个 {@link FileEditor}，内容是一个 Swing 面板（{@link TableStructurePanel}），
 * 不是弹窗、也不是文本编辑器。
 *
 * <p>线程模型：
 * <ul>
 *   <li>数据库模型（{@code Das*} / PSI）只在后台的 {@link ReadAction#nonBlocking} 中读取；</li>
 *   <li>加载结果转换成纯 DTO（{@link TableStructure}），再切回 EDT 刷新 Swing 表格；</li>
 *   <li>监听 {@link DbPsiFacade#TOPIC}：数据源内省结果变化时自动重新加载。</li>
 * </ul>
 */
public final class TableStructureFileEditor extends UserDataHolderBase implements FileEditor {

    private final Project project;
    private final TableStructureVirtualFile file;
    private final TableStructurePanel panel = new TableStructurePanel();
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

    private final MessageBusConnection connection;
    private volatile boolean disposed;
    @Nullable
    private CancellablePromise<TableStructure> currentLoad;
    /** 最近一次成功加载的结构（生成 ALTER DDL 时要取限定表名）。只在 EDT 读写。 */
    @Nullable
    private TableStructure structure;

    public TableStructureFileEditor(@NotNull Project project, @NotNull TableStructureVirtualFile file) {
        this.project = project;
        this.file = file;
        this.panel.setRefreshAction(this::refreshFromDatabase);
        this.panel.setAlterDdlAction(columns -> showAlterDdl(columns));
        this.connection = project.getMessageBus().connect(this);
        this.connection.subscribe(DbPsiFacade.TOPIC, (DbPsiFacade.Listener) dataSource -> refresh());
        refresh();
    }

    /**
     * 刷新按钮：先回到数据库重新内省这张表，内省结束后再重新读模型。
     *
     * <p>数据库内省拿不到时（未连接 / 模型未加载 / 方言不支持）退化成本来就有的「只重读模型」，
     * 这样按钮在任何状态下都有反馈，不会点了没反应。</p>
     */
    private void refreshFromDatabase() {
        if (disposed) {
            return;
        }
        panel.showLoading();
        // 解析 DbTable 与发起内省都要在后台读锁内做，这里不复用 refresh()（它有自己的 promise 生命周期）
        ReadAction.nonBlocking(() -> {
                    DbTable table = file.getTablePointer().getElement();
                    if (table == null) {
                        throw new IllegalStateException("表对象已失效（可能已被删除，或数据源已断开）");
                    }
                    return table;
                })
                .inSmartMode(project)
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService())
                .onSuccess(table -> TableRefreshService.refreshFromDatabase(project, table, this::refresh))
                .onError(error -> ApplicationManager.getApplication()
                        .invokeLater(() -> {
                            if (!disposed) {
                                panel.showError(error == null || error.getMessage() == null
                                        ? "未知错误"
                                        : error.getMessage());
                            }
                        }, ModalityState.any()));
    }

    /** 重新从 DataGrip 模型读取表结构（异步，可在任意线程调用）。 */
    public void refresh() {
        if (disposed) {
            return;
        }
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(this::refresh, ModalityState.any());
            return;
        }
        panel.showLoading();
        if (currentLoad != null) {
            currentLoad.cancel();
            currentLoad = null;
        }

        CancellablePromise<TableStructure> promise = ReadAction.nonBlocking(() -> {
                    DbTable table = file.getTablePointer().getElement();
                    if (table == null) {
                        throw new IllegalStateException("表对象已失效（可能已被删除，或数据源已断开）");
                    }
                    return TableStructureLoader.load(table);
                })
                .inSmartMode(project)
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService());

        currentLoad = promise;
        promise.onSuccess(loaded -> ApplicationManager.getApplication()
                        .invokeLater(() -> {
                            if (!disposed) {
                                structure = loaded;
                                panel.showStructure(loaded);
                            }
                        }, ModalityState.any()))
                .onError(error -> ApplicationManager.getApplication()
                        .invokeLater(() -> {
                            if (!disposed) {
                                panel.showError(error == null || error.getMessage() == null
                                        ? "未知错误"
                                        : error.getMessage());
                            }
                        }, ModalityState.any()));
    }

    /**
     * 列 Tab 右键「生成 ALTER DDL」：把选中的列拼成 ADD COLUMN 语句并弹窗预览。
     * 纯字符串拼接（{@link AlterDdlGenerator}），不碰数据库模型，因此在 EDT 上直接做即可。
     */
    private void showAlterDdl(@NotNull List<ColumnInfo> columns) {
        TableStructure current = structure;
        if (current == null || columns.isEmpty()) {
            return;
        }
        String ddl = AlterDdlGenerator.addColumns(current.table().schema(), current.table().name(), columns);
        if (ddl.isEmpty()) {
            return;
        }
        AlterDdlDialog.show(project, ddl);
    }

    // ---- FileEditor -------------------------------------------------------------------------
    @Override
    public @NotNull JComponent getComponent() {
        return panel;
    }

    @Override
    public @NotNull JComponent getPreferredFocusedComponent() {
        return panel.getPreferredFocusedComponent();
    }

    /** 编辑器类型名称（用于编辑器类型切换器），不是 tab 标题；tab 标题来自虚拟文件名。 */
    @Override
    public @NotNull String getName() {
        return "表结构";
    }

    /** 必须覆盖：默认实现会记录 “A proper @NotNull implementation required” 的 PluginException。 */
    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
        // 不持久化选中行 / 滚动位置
    }

    @Override
    public @NotNull FileEditorState getState(@NotNull FileEditorStateLevel level) {
        return FileEditorState.INSTANCE;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    @Override
    public boolean isValid() {
        return !disposed;
    }

    @Override
    public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    @Override
    public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
        changeSupport.removePropertyChangeListener(listener);
    }

    @Override
    public void dispose() {
        disposed = true;
        if (currentLoad != null) {
            currentLoad.cancel();
            currentLoad = null;
        }
        connection.disconnect();
    }
}
