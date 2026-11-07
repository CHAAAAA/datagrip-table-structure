package com.github.chaaaaa.datagripts.action;

import com.intellij.database.actions.ddl.ModifyObjectAction;
import com.intellij.database.psi.DbElement;
import com.intellij.database.psi.DbTable;
import com.intellij.database.view.DatabaseView;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;

/**
 * 桥接到 DataGrip 原生「修改表…」动作：自己拼 DDL 生成与 diff 等于重写半个 DataGrip，
 * 直接喂一个 {@link DatabaseView#DB_ELEMENTS} 上下文给官方动作，弹窗、方言差异、预览全部沿用官方实现。
 */
public final class ModifyTableBridge {

    private static final Logger LOG = Logger.getInstance(ModifyTableBridge.class);

    /** 官方 plugin.xml 中的动作 id；该动作标了 internal，部分版本可能取不到。 */
    private static final String MODIFY_OBJECT_ACTION_ID = "DatabaseView.Alterations.ModifyObject";

    private ModifyTableBridge() {
    }

    /**
     * 打开原生「修改表…」对话框；非 EDT 时自动切到 EDT。失败时静默（调用方只看返回值）。
     *
     * @return {@code true} 已发起（或已在 EDT 排队），{@code false} 明确失败（例如原生动作不可用）
     */
    public static boolean open(@NotNull Project project, @NotNull DbTable table) {
        if (!ApplicationManager.getApplication().isDispatchThread()) {
            ApplicationManager.getApplication().invokeLater(() -> open(project, table), ModalityState.any());
            return true;
        }
        // 依赖 com.intellij.database 内部 API，跨大版本可能变化；失败不能连带搞崩总览 tab。
        try {
            AnAction registered = ActionManager.getInstance().getAction(MODIFY_OBJECT_ACTION_ID);
            AnAction action = registered == null ? new ModifyObjectAction() : registered;
            DataContext context = SimpleDataContext.builder()
                    .add(LangDataKeys.PROJECT, project)
                    .add(DatabaseView.DB_ELEMENTS, new DbElement[]{table})
                    .build();
            AnActionEvent event = AnActionEvent.createEvent(
                    action, context, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
            Computable<Boolean> update = () -> ActionUtil.updateAction(action, event).isPerformed()
                    && event.getPresentation().isEnabledAndVisible();
            // 原生修改动作在 BGT 更新；underModalProgress 同时提供后台线程和读锁。
            boolean enabled = action.getActionUpdateThread() == ActionUpdateThread.EDT
                    ? update.compute()
                    : ActionUtil.underModalProgress(project, "检查表结构修改状态", update);
            return enabled && ActionUtil.performAction(action, event).isPerformed();
        } catch (Exception | LinkageError e) {
            LOG.warn("无法打开原生「修改表…」对话框", e);
            return false;
        }
    }
}
