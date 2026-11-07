package com.github.chaaaaa.datagripts.action;

import com.intellij.database.DatabaseDataKeysCore;
import com.intellij.database.psi.DbElement;
import com.intellij.database.psi.DbTable;
import com.intellij.database.view.DatabaseContextFun;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 数据库对象树动作：用编辑器标签页打开所选表的结构视图。
 *
 * <p>选中对象优先用 DataGrip 官方的 {@link DatabaseContextFun}（覆盖节点分组、多选、新建对象等情形），
 * 再回退到通用的 DataContext key，兼容编辑器/控制台上下文。</p>
 */
public final class OpenTableStructureAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        DbTable table = findSelectedTable(event);
        if (table != null) {
            TableStructureOpener.open(project, table);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(
                event.getProject() != null && findSelectedTable(event) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    private static @Nullable DbTable findSelectedTable(@NotNull AnActionEvent event) {
        return ReadAction.computeBlocking(() -> {
            DbTable fromTree = DatabaseContextFun
                    .getSelectedDbElements(event.getDataContext(), DbTable.class)
                    .first();
            if (fromTree != null) {
                return fromTree;
            }

            DbElement editorObject = event.getData(DatabaseDataKeysCore.DB_EDITOR_OBJECT);
            if (editorObject instanceof DbTable table) {
                return table;
            }

            PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
            if (psiElement instanceof DbTable table) {
                return table;
            }

            PsiElement[] psiElements = event.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
            if (psiElements != null) {
                for (PsiElement element : psiElements) {
                    if (element instanceof DbTable table) {
                        return table;
                    }
                }
            }
            return null;
        });
    }
}
