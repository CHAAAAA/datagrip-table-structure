package com.github.chaaaaa.datagripts.action;

import com.github.chaaaaa.datagripts.editor.TableStructureService;
import com.github.chaaaaa.datagripts.editor.TableStructureVirtualFile;
import com.github.chaaaaa.datagripts.model.TableKey;
import com.intellij.database.model.DasTable;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.psi.DbTable;
import com.intellij.database.util.DasUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

/**
 * “打开表结构标签页”的统一入口：从 {@link DbTable} 构造稳定 key + 智能指针，复用/创建虚拟文件，
 * 最后交给 {@link TableStructureService#openEditor} 以普通编辑器 tab 打开。
 */
public final class TableStructureOpener {

    private TableStructureOpener() {
    }

    /**
     * 在编辑器区域打开（或复用已打开的）表结构 tab。
     * 需要在读锁内构造 key 与智能指针，因此整体包在 {@link ReadAction} 中。
     */
    public static void open(@NotNull Project project, @NotNull DbTable table) {
        TableStructureService service = TableStructureService.getInstance(project);
        TableStructureVirtualFile file = ReadAction.computeBlocking(() -> {
            TableKey key = buildKey(table);
            SmartPsiElementPointer<DbTable> pointer =
                    SmartPointerManager.getInstance(project).createSmartPsiElementPointer(table);
            return service.fileFor(key, pointer);
        });
        service.openEditor(project, file);
    }

    /** 构造稳定 key：数据源 uniqueId + schema + 表名。 */
    public static @NotNull TableKey buildKey(@NotNull DbTable table) {
        DbDataSource dataSource = table.getDataSource();
        DasTable dasTable = table.getDasObject();
        String schema = dasTable == null ? "" : nullToEmpty(DasUtil.getSchema(dasTable));
        String tableName = nullToEmpty(table.getName());
        String dataSourceId = nullToEmpty(dataSource.getUniqueId());
        return new TableKey(dataSourceId, schema, tableName);
    }

    private static @NotNull String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
