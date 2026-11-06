package com.example.tablestructure.editor;

import com.example.tablestructure.action.TableStructureOpener;
import com.example.tablestructure.model.ObjectEntry;
import com.example.tablestructure.service.ObjectBrowserLoader;
import com.example.tablestructure.ui.ObjectBrowserPanel;
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
    private CancellablePromise<List<ObjectEntry>> currentLoad;

    public ObjectBrowserFileEditor(@NotNull Project project, @NotNull ObjectBrowserVirtualFile file) {
        this.project = project;
        this.file = file;
        this.panel.setRefreshAction(this::refresh);
        this.panel.setOpenAction(this::openEntry);
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

        CancellablePromise<List<ObjectEntry>> promise = ReadAction.nonBlocking(() -> {
                    DbDataSource dataSource = findDataSource();
                    if (dataSource == null) {
                        throw new IllegalStateException("数据源已失效或已断开");
                    }
                    return ObjectBrowserLoader.load(dataSource);
                })
                .inSmartMode(project)
                .expireWith(this)
                .submit(AppExecutorUtil.getAppExecutorService());

        currentLoad = promise;
        promise.onSuccess(entries -> ApplicationManager.getApplication()
                        .invokeLater(() -> {
                            if (!disposed) {
                                panel.showObjects(entries);
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

    /** 双击：表/视图打开结构 tab；序列/存储过程暂不支持。 */
    private void openEntry(@NotNull ObjectEntry entry) {
        if (!entry.tableLike()) {
            panel.showMessage("“" + entry.name() + "”（" + entry.group() + "）暂不支持结构视图");
            return;
        }
        // shortcut: 总览行只缓存 DasObject，双击时才解析成 DbElement（省一次为全库对象建指针）；
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
                        TableStructureOpener.open(project, table);
                    }
                }, ModalityState.any()));
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
