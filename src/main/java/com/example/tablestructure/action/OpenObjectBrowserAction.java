package com.example.tablestructure.action;

import com.intellij.database.DatabaseDataKeysCore;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbElement;
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
 * 数据库对象树动作：打开所选数据源的对象总览 tab（表/视图/序列/存储过程）。
 * 选中库内任意对象时取其所属数据源。
 */
public final class OpenObjectBrowserAction extends DumbAwareAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        Project project = event.getProject();
        if (project == null) {
            return;
        }
        DbDataSource dataSource = findDataSource(event);
        if (dataSource != null) {
            ObjectBrowserOpener.open(project, dataSource);
        }
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        event.getPresentation().setEnabledAndVisible(
                event.getProject() != null && findDataSource(event) != null);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    private static @Nullable DbDataSource findDataSource(@NotNull AnActionEvent event) {
        return ReadAction.computeBlocking(() -> {
            DbElement fromTree = DatabaseContextFun
                    .getSelectedDbElements(event.getDataContext())
                    .first();
            if (fromTree != null) {
                return ObjectBrowserOpener.dataSourceOf(fromTree);
            }

            DbElement editorObject = event.getData(DatabaseDataKeysCore.DB_EDITOR_OBJECT);
            if (editorObject != null) {
                return ObjectBrowserOpener.dataSourceOf(editorObject);
            }

            PsiElement psiElement = event.getData(CommonDataKeys.PSI_ELEMENT);
            if (psiElement instanceof DbElement element) {
                return ObjectBrowserOpener.dataSourceOf(element);
            }

            PsiElement[] psiElements = event.getData(PlatformCoreDataKeys.PSI_ELEMENT_ARRAY);
            if (psiElements != null) {
                for (PsiElement element : psiElements) {
                    if (element instanceof DbElement dbElement) {
                        return ObjectBrowserOpener.dataSourceOf(dbElement);
                    }
                }
            }
            return null;
        });
    }
}
