package com.example.tablestructure.editor;

import com.example.tablestructure.model.TableKey;
import com.intellij.database.psi.DbTable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 项目级服务：保证同一个表只有一个 {@link TableStructureVirtualFile} 实例。
 *
 * <p>原因：{@code FileEditorManager.openFile()} 以 <b>VirtualFile 实例身份</b>（而非文件名）区分 tab，
 * 如果每次点击都 new 一个新的 LightVirtualFile，就会为同一张表打开多个重复 tab。</p>
 */
@Service(Service.Level.PROJECT)
public final class TableStructureService {

    private final Map<String, TableStructureVirtualFile> files = new ConcurrentHashMap<>();
    private final Map<String, ObjectBrowserVirtualFile> browsers = new ConcurrentHashMap<>();

    public static @NotNull TableStructureService getInstance(@NotNull Project project) {
        return project.getService(TableStructureService.class);
    }

    /** 每个数据源一个对象总览 tab。 */
    public @NotNull ObjectBrowserVirtualFile browserFor(@NotNull String dataSourceId, @NotNull String dataSourceName) {
        return browsers.computeIfAbsent(dataSourceId, ignored -> new ObjectBrowserVirtualFile(dataSourceId, dataSourceName));
    }

    /**
     * 获取（或创建）某张表对应的虚拟文件。
     *
     * @param key     表的稳定标识
     * @param pointer 指向 {@link DbTable} 的智能指针（模型刷新后仍可重新解析）
     */
    public @NotNull TableStructureVirtualFile fileFor(
            @NotNull TableKey key,
            @NotNull SmartPsiElementPointer<DbTable> pointer
    ) {
        return files.computeIfAbsent(key.id(), ignored -> new TableStructureVirtualFile(key, pointer));
    }
}
