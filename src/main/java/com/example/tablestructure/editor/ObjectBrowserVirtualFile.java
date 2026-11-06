package com.example.tablestructure.editor;

import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 承载“某数据源的对象总览”的虚拟文件。与 {@link TableStructureVirtualFile} 同构，
 * 只是标识从 {@code TableKey} 换成数据源 uniqueId（一个数据源一个总览 tab）。
 */
public final class ObjectBrowserVirtualFile extends LightVirtualFile {

    private final String dataSourceId;

    public ObjectBrowserVirtualFile(@NotNull String dataSourceId, @NotNull String dataSourceName) {
        super("对象总览: " + dataSourceName, TableStructureFileType.INSTANCE, "");
        this.dataSourceId = dataSourceId;
        setWritable(false);
    }

    public @NotNull String getDataSourceId() {
        return dataSourceId;
    }

    @Override
    public @NotNull String getPresentableName() {
        return getName();
    }
}
