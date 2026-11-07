package com.github.chaaaaa.datagripts.editor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileTypes.FileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/**
 * 自定义文件类型，仅用于给“表结构”虚拟文件提供：
 * <ul>
 *   <li>tab 图标（{@link AllIcons.Nodes#DataTables}）；</li>
 *   <li>稳定的类型标识，便于 {@code FileEditorProvider#accept} 与插件扩展点过滤；</li>
 *   <li>阻止平台为该虚拟文件创建 Document（{@link #isBinary()} 返回 {@code true}）。</li>
 * </ul>
 *
 * 通过 {@code <fileType implementationClass="..." fieldName="INSTANCE"/>} 注册，
 * 这里不注册任何文件扩展名（避免把真实磁盘文件误判成该类型）。
 */
public final class TableStructureFileType implements FileType {

    /** 与 plugin.xml 中 {@code <fileType name="..."/>} 保持一致（平台要求全局唯一）。 */
    public static final String NAME = "DataGripTableStructure";

    /** {@code <fileType fieldName="INSTANCE">} 通过反射读取该字段。 */
    public static final TableStructureFileType INSTANCE = new TableStructureFileType();

    private TableStructureFileType() {
    }

    @Override
    public @NotNull String getName() {
        return NAME;
    }

    @Override
    public @NotNull String getDescription() {
        return "Database table structure view";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "tablestructure";
    }

    @Override
    public @Nullable Icon getIcon() {
        return AllIcons.Nodes.DataTables;
    }

    @Override
    public boolean isBinary() {
        // 该虚拟文件不承载文本内容，结构视图完全由 FileEditor 渲染
        return true;
    }
}
