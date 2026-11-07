package com.github.chaaaaa.datagripts.editor;

import com.github.chaaaaa.datagripts.action.ModifyTableBridge;
import com.github.chaaaaa.datagripts.action.TableStructureOpener;
import com.github.chaaaaa.datagripts.model.ObjectEntry;
import com.github.chaaaaa.datagripts.service.ObjectBrowserLoader;
import com.github.chaaaaa.datagripts.ui.ObjectBrowserPanel;
import com.intellij.database.model.DasObject;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbElement;
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
import java.util.function.Consumer;

/**
 * 对象总览编辑器：后台枚举对象 → EDT 渲染。与 {@link TableStructureFileEditor} 同一套线程模型。
 */
public final class ObjectBrowserFileEditor extends UserDataHolderBase implements FileEditor {

    private final Project project;
    private final ObjectBrowserVirtualFile file;
    private final ObjectBrowserPanel panel = new ObjectBrowserPanel();
    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);

    private final MessageBusConnection connection;
    private volatile boolean disposed;
    @Nullable
    private CancellablePromise<Snapshot> currentLoad;

    /** 当前 schema：一次只查一个 schema 的对象。只在 EDT 读写。 */
    @Nullable
    private String schema;

    public ObjectBrowserFileEditor(@NotNull Project project, @NotNull ObjectBrowserVirtualFile file) {
        this.project = project;
        this.file = file;
        this.panel.setRefreshAction(this::refresh);
        this.panel.setOpenAction(this::openEntry);
        this.panel.setEditAction(this::editTable);
        this.panel.setSchemaChangeAction(this::switchSchema);
        this.connection = project.getMessageBus().connect(this);
        this.connection.subscribe(DbPsiFacade.TOPIC, (DbPsiFacade.Listener) dataSource -> refresh());
        refresh();
    }

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

        // 读一份快照：后台线程全程只读，不写 field，避免切 schema 时旧请求回写覆盖新选择
        String requested = schema;
        CancellablePromise<Snapshot> promise = ReadAction.nonBlocking(() -> {
                    DbDataSource dataSource = findDataSource();
                    if (dataSource == null) {
                        throw new IllegalStateException("数据源已失效或已断开");
                    }
                    List<String> schemas = ObjectBrowserLoader.loadSchemas(dataSource);
                    // 首次打开跟随连接当前 schema；之后保留用户的选择
                    String selected = pickSchema(schemas, requested, ObjectBrowserLoader.currentSchema(dataSource));
                    List<ObjectEntry> entries = selected == null
                            ? List.of()
                            : ObjectBrowserLoader.load(dataSource, selected);
                    return new Snapshot(schemas, selected, entries);
                })
                .inSmartMode(project)
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService());

        currentLoad = promise;
        promise.onSuccess(snapshot -> ApplicationManager.getApplication()
                        .invokeLater(() -> {
                            if (disposed || promise != currentLoad) {
                                return; // 已被更新的刷新取代
                            }
                            schema = snapshot.selected();
                            panel.showSchemas(snapshot.schemas(), snapshot.selected());
                            panel.showObjects(snapshot.entries());
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

    /** 切换 schema：只重新查询这一个 schema 的对象。 */
    private void switchSchema(@NotNull String schema) {
        if (disposed || schema.equals(this.schema)) {
            return;
        }
        this.schema = schema;
        refresh();
    }

    /**
     * 决定刷新后要展示哪个 schema：用户已选的优先，其次跟随连接当前 schema，最后退回第一个。
     *
     * @param previous 上一次选中的 schema（首次打开为 null）
     * @param connected 连接当前 schema，可能为 null
     * @return 选中的 schema；一个都没有时返回 null
     */
    static @Nullable String pickSchema(@NotNull List<String> schemas, @Nullable String previous,
                                       @Nullable String connected) {
        if (previous != null && schemas.contains(previous)) {
            return previous;
        }
        if (connected != null && schemas.contains(connected)) {
            return connected;
        }
        return schemas.isEmpty() ? null : schemas.get(0);
    }

    /** 双击：表/视图打开结构 tab；序列/存储过程暂不支持。 */
    private void openEntry(@NotNull ObjectEntry entry) {
        if (!entry.tableLike()) {
            panel.showMessage("“" + entry.name() + "”（" + entry.group() + "）暂不支持结构视图");
            return;
        }
        resolveTable(entry, table -> TableStructureOpener.open(project, table));
    }

    /** 右键「编辑表结构」：转交给 DataGrip 原生「修改表…」对话框。 */
    private void editTable(@NotNull ObjectEntry entry) {
        if (!ObjectEntry.GROUP_TABLE.equals(entry.group())) {
            panel.showMessage("只能编辑表的结构，“" + entry.name() + "”是" + entry.group());
            return;
        }
        resolveTable(entry, table -> {
            if (!ModifyTableBridge.open(project, table)) {
                panel.showError("无法打开「修改表」对话框：对象可能只读、正在导入，或原生修改动作不可用。");
            }
        });
    }

    /** 把总览行里的 DasObject 解析成 DbTable，再回到 EDT 执行 {@code onTable}。 */
    private void resolveTable(@NotNull ObjectEntry entry, @NotNull Consumer<DbTable> onTable) {
        // shortcut: 总览行只缓存 DasObject，用到时才解析成 DbElement（省一次为全库对象建指针）；
        // 若模型已被清理，findElement 返回 null，重开总览即可。
        ReadAction.nonBlocking(() -> {
                    DbDataSource dataSource = findDataSource();
                    if (dataSource == null) {
                        return null;
                    }
                    DbElement element = dataSource.findElement(entry.dasObject());
                    return element instanceof DbTable dbTable ? dbTable : null;
                })
                .inSmartMode(project)
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService())
                .onError(error -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed) {
                        panel.showError("无法定位对象“" + entry.name() + "”：" + error.getMessage());
                    }
                }, ModalityState.any()))
                .onSuccess(table -> ApplicationManager.getApplication().invokeLater(() -> {
                    if (disposed) {
                        return;
                    }
                    if (table == null) {
                        panel.showError("无法定位对象“" + entry.name() + "”");
                    } else {
                        onTable.accept(table);
                    }
                }, ModalityState.any()));
    }

    /** 一次刷新拿到的完整快照：schema 下拉项 + 选中项 + 该 schema 的对象。 */
    private record Snapshot(@NotNull List<String> schemas, @Nullable String selected,
                            @NotNull List<ObjectEntry> entries) {
    }

    private @Nullable DbDataSource findDataSource() {
        for (DbDataSource dataSource : DbPsiFacade.getInstance(project).getDataSources()) {
            if (file.getDataSourceId().equals(dataSource.getUniqueId())) {
                return dataSource;
            }
        }
        return null;
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

    @Override
    public @NotNull String getName() {
        return "对象总览";
    }

    @Override
    public @NotNull VirtualFile getFile() {
        return file;
    }

    @Override
    public void setState(@NotNull FileEditorState state) {
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
