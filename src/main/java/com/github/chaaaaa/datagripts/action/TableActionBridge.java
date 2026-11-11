package com.github.chaaaaa.datagripts.action;

import com.intellij.database.model.basic.BasicElement;
import com.intellij.database.psi.DbElement;
import com.intellij.database.psi.DbTable;
import com.intellij.database.view.DatabaseView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * 给 DataGrip 原生动作提供当前表上下文，复用修改表、数据编辑器、CREATE SQL 生成和刷新片段功能。
 */
public final class TableActionBridge {

    private static final Logger LOG = Logger.getInstance(TableActionBridge.class);

    /** 官方 plugin.xml 中的动作 id；该动作标了 internal，但注册后仍可通过 ActionManager 取到。 */
    private static final String MODIFY_OBJECT_ACTION_ID = "DatabaseView.Alterations.ModifyObject";
    /** 数据源右键「刷新」中刷新单个对象的动作；它内部走的正是表结构刷新需要的片段内省。 */
    private static final String REFRESH_FRAGMENT_ACTION_ID = "DatabaseView.RefreshFragment";

    private TableActionBridge() {
    }

    /**
     * 打开原生「修改表…」对话框；非 EDT 时自动切到 EDT。失败时静默（调用方只看返回值）。
     *
     * @return {@code true} 已发起（或已在 EDT 排队），{@code false} 明确失败（例如原生动作不可用）
     */
    public static boolean open(@NotNull Project project, @NotNull DbTable table) {
        return perform(project, table, MODIFY_OBJECT_ACTION_ID, "修改表结构");
    }

    /** 打开原生数据编辑器，表是否可编辑由 DataGrip 判断。 */
    public static boolean openData(@NotNull Project project, @NotNull DbTable table) {
        return perform(project, table, "Jdbc.OpenEditor.Data", "查看数据");
    }

    /** 生成当前表的 CREATE SQL 并打开原生 SQL 控制台，不执行 SQL。 */
    public static boolean openCreateSql(@NotNull Project project, @NotNull DbTable table) {
        return perform(project, table, "DatabaseView.OpenDdlInConsole", "生成创建 SQL");
    }

    /** 让 DataGrip 从数据库重新内省当前表；返回 {@code false} 表示原生动作不可用，调用方退回重读模型。 */
    public static boolean refreshFragment(@NotNull Project project, @NotNull DbTable table) {
        return perform(project, table, REFRESH_FRAGMENT_ACTION_ID, "刷新表结构");
    }

    /** 原生动作除 PSI 的 {@code DB_ELEMENTS} 外还会读模型层的 {@code DATABASE_ELEMENTS}（刷新片段动作只读这个）。 */
    private static @NotNull BasicElement[] basicElementsOf(@NotNull DbTable table) {
        Object delegate = table.getDelegate();
        return delegate instanceof BasicElement element
                ? new BasicElement[]{element} : new BasicElement[0];
    }

    private static boolean perform(@NotNull Project project, @NotNull DbTable table,
                                   @NotNull String actionId, @NotNull String operation) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(
                    () -> perform(project, table, actionId, operation), ModalityState.any());
            return true;
        }
        // 依赖 com.intellij.database 内部动作 id，跨大版本可能变化；失败不能连带搞崩总览 tab。
        try {
            AnAction action = ActionManager.getInstance().getAction(actionId);
            if (action == null) {
                return false;
            }
            DbElement[] elements = {table};
            DataContext context = SimpleDataContext.builder()
                    .add(CommonDataKeys.PROJECT, project)
                    .add(DatabaseView.DB_ELEMENTS, elements)
                    .add(DatabaseView.DATABASE_ELEMENTS, basicElementsOf(table))
                    .add(CommonDataKeys.PSI_ELEMENT, table)
                    .add(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY, elements)
                    .build();
            AnActionEvent event = AnActionEvent.createEvent(
                    action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
            Computable<Boolean> update = () -> ActionUtil.updateAction(action, event).isPerformed()
                    && event.getPresentation().isEnabledAndVisible();
            // 原生动作在 BGT 更新；underModalProgress 同时提供后台线程和读锁。
            boolean enabled = action.getActionUpdateThread() == ActionUpdateThread.EDT
                    ? update.compute()
                    : ActionUtil.underModalProgress(project, "检查" + operation + "状态", update);
            return enabled && ActionUtil.performAction(action, event).isPerformed();
        } catch (CancellationException e) {
            throw e;
        } catch (Exception | LinkageError e) {
            LOG.warn("无法调用原生「" + operation + "」动作", e);
            return false;
        }
    }
}
