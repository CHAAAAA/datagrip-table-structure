package com.github.chaaaaa.datagripts.action;

import com.github.chaaaaa.datagripts.editor.ObjectBrowserVirtualFile;
import com.github.chaaaaa.datagripts.editor.TableStructureService;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbElement;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/** 打开（或复用）某数据源的对象总览 tab。 */
public final class ObjectBrowserOpener {

    private ObjectBrowserOpener() {
    }

    public static void open(@NotNull Project project, @NotNull DbDataSource dataSource) {
        TableStructureService service = TableStructureService.getInstance(project);
        ObjectBrowserVirtualFile file = service.browserFor(dataSource.getUniqueId(), dataSource.getName());
        service.openEditor(project, file);
    }

    /** 选中数据源节点时用它本身，选中库里其他对象时用其所属数据源。 */
    public static DbDataSource dataSourceOf(@NotNull DbElement element) {
        return element instanceof DbDataSource dataSource ? dataSource : element.getDataSource();
    }
}
