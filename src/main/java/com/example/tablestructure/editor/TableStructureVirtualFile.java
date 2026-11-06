package com.example.tablestructure.editor;

import com.example.tablestructure.model.TableKey;
import com.intellij.database.psi.DbTable;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * 承载“某张表的结构视图”的虚拟文件。
 *
 * <p>这是一个 {@link LightVirtualFile}（内存虚拟文件，不落盘），因此：
 * <ul>
 *   <li>平台不会把它当成普通文本文件，也不会创建 Document；</li>
 *   <li>它的虚拟文件系统是平台内部的 “mock” 文件系统，<b>不会被持久化</b>，
 *       所以 IDE 重启后该 tab 不会被自动恢复（重启恢复见实现方案文档的“可选增强”）。</li>
 * </ul>
 *
 * <p>tab 标题取自 {@link LightVirtualFile#getName()}（由 {@link TableKey#tabTitle()} 决定），
 * 图标取自 {@link TableStructureFileType#getIcon()}。</p>
 */
public final class TableStructureVirtualFile extends LightVirtualFile {

    private final TableKey key;
    private final SmartPsiElementPointer<DbTable> tablePointer;

    public TableStructureVirtualFile(@NotNull TableKey key, @NotNull SmartPsiElementPointer<DbTable> tablePointer) {
        super(key.tabTitle(), TableStructureFileType.INSTANCE, "");
        this.key = key;
        this.tablePointer = tablePointer;
        setWritable(false);
    }

    public @NotNull TableKey getKey() {
        return key;
    }

    public @NotNull SmartPsiElementPointer<DbTable> getTablePointer() {
        return tablePointer;
    }

    @Override
    public @NotNull String getPresentableName() {
        return getName();
    }
}
