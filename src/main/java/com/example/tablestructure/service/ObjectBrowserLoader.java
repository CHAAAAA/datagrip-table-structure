package com.example.tablestructure.service;

import com.example.tablestructure.model.ObjectEntry;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.util.DasUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 枚举某数据源下的表 / 视图 / 序列 / 存储过程。必须在读锁内调用。
 *
 * <p>一次 {@code getSchemaElements} 遍历即可拿到全部对象（内部会跳过 namespace 节点），
 * 无需按类型分别查询。</p>
 */
public final class ObjectBrowserLoader {

    private ObjectBrowserLoader() {
    }

    public static @NotNull List<ObjectEntry> load(@NotNull DbDataSource dataSource) {
        List<ObjectEntry> result = new ArrayList<>();
        // shortcut: 会遍历到列/索引等子对象再过滤掉（全内存、无 I/O，数千表量级可接受）；
        // 若超大库首次打开变慢，改为按 namespace.getDbChildren(对象类型) 分层取。
        for (DasObject object : DasUtil.getSchemaElements(dataSource, DasObject.class)) {
            ObjectKind kind = object.getKind();
            String group = classify(kind);
            if (group == null) {
                continue;
            }
            result.add(new ObjectEntry(
                    nullToEmpty(object.getName()),
                    nullToEmpty(DasUtil.getSchema(object)),
                    kind == null ? "" : nullToEmpty(kind.getPresentableName()),
                    nullToEmpty(object.getComment()),
                    group,
                    object instanceof DasTable,
                    object
            ));
        }
        return result;
    }

    /** 返回过滤分组；不在范围内的对象类型返回 null。 */
    private static @Nullable String classify(@Nullable ObjectKind kind) {
        if (kind == null) {
            return null;
        }
        if (kind == ObjectKind.TABLE) {
            return ObjectEntry.GROUP_TABLE;
        }
        if (kind == ObjectKind.VIEW || kind == ObjectKind.MAT_VIEW || kind == ObjectKind.MAT_LOG) {
            return ObjectEntry.GROUP_VIEW;
        }
        if (kind == ObjectKind.SEQUENCE) {
            return ObjectEntry.GROUP_SEQUENCE;
        }
        if (kind == ObjectKind.ROUTINE || kind == ObjectKind.PACKAGE
                || kind == ObjectKind.BODY || kind == ObjectKind.METHOD) {
            return ObjectEntry.GROUP_ROUTINE;
        }
        return null;
    }

    private static @NotNull String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }
}
