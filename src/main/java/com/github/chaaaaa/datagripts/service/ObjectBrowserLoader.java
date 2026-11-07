package com.github.chaaaaa.datagripts.service;

import com.github.chaaaaa.datagripts.model.ObjectEntry;
import com.intellij.database.model.DasNamespace;
import com.intellij.database.model.DasObject;
import com.intellij.database.model.DasTable;
import com.intellij.database.model.ObjectKind;
import com.intellij.database.model.basic.BasicNamespace;
import com.intellij.database.psi.DbDataSource;
import com.intellij.database.util.DasUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 枚举某数据源下的 schema 及其表 / 视图 / 序列 / 存储过程。必须在读锁内调用。
 *
 * <p>总览一次只看一个 schema：先用 {@link #loadSchemas} 取下拉选项，再按选中的 schema
 * 调用 {@link #load}，不再一次性遍历整库。</p>
 */
public final class ObjectBrowserLoader {

    /** 关注的直接子对象类型，与 {@link #classify} 保持一致。 */
    private static final List<ObjectKind> KINDS = List.of(
            ObjectKind.TABLE, ObjectKind.VIEW, ObjectKind.MAT_VIEW, ObjectKind.MAT_LOG,
            ObjectKind.SEQUENCE, ObjectKind.ROUTINE, ObjectKind.PACKAGE, ObjectKind.BODY,
            ObjectKind.METHOD);

    private ObjectBrowserLoader() {
    }

    /** 该数据源下的全部 schema 名，按名称升序（忽略大小写）；无 schema 概念时返回空 List。必须在读锁内调用。 */
    public static @NotNull List<String> loadSchemas(@NotNull DbDataSource dataSource) {
        Set<String> names = new LinkedHashSet<>();
        for (DasNamespace schema : DasUtil.getSchemas(dataSource)) {
            String name = schema == null ? null : schema.getName();
            if (name != null && !name.isEmpty()) {
                names.add(name);
            }
        }
        List<String> result = new ArrayList<>(names);
        result.sort(String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    /** 连接当前 schema 名；取不到返回 null。必须在读锁内调用。 */
    public static @Nullable String currentSchema(@NotNull DbDataSource dataSource) {
        try {
            // DasModel 接口本身就暴露了当前 schema，无需强转到 BasicModel
            DasNamespace current = dataSource.getModel() == null ? null : dataSource.getModel().getCurrentRootNamespace();
            String name = current == null ? null : current.getName();
            if (name != null && !name.isEmpty()) {
                return name;
            }
            for (DasNamespace schema : DasUtil.getSchemas(dataSource)) {
                if (schema instanceof BasicNamespace basic && basic.isCurrent()) {
                    String schemaName = schema.getName();
                    if (schemaName != null && !schemaName.isEmpty()) {
                        return schemaName;
                    }
                }
            }
        } catch (Exception ignored) {
            // shortcut: 这里只是给下拉框取默认值，平台 API 拿不到就让调用方退到第一个 schema；
            // 若 getCurrentRootNamespace 对所有目标方言都稳定，可删掉这段兜底。
        }
        return null;
    }

    /** 只枚举指定 schema 下的表 / 视图 / 序列 / 存储过程，按名称排序；schema 不存在返回空 List。必须在读锁内调用。 */
    public static @NotNull List<ObjectEntry> load(@NotNull DbDataSource dataSource, @NotNull String schema) {
        List<ObjectEntry> result = new ArrayList<>();
        DasNamespace namespace = null;
        for (DasNamespace candidate : DasUtil.getSchemas(dataSource)) {
            if (candidate != null && schema.equals(candidate.getName())) {
                namespace = candidate;
                break;
            }
        }
        if (namespace != null) {
            for (ObjectKind kind : KINDS) {
                String group = classify(kind);
                if (group == null) {
                    continue;
                }
                for (DasObject object : namespace.getDasChildren(kind)) {
                    result.add(toEntry(object, schema, group));
                }
            }
        }
        if (result.isEmpty()) {
            // shortcut: 少数方言的 namespace 不实现 getDasChildren（枚举为空），此时退回全量遍历再按 schema
            // 过滤，代价是切到空 schema 时会多扫一遍全库；等确认平台已覆盖所有目标方言即删除本分支。
            for (DasObject object : DasUtil.getSchemaElements(dataSource, DasObject.class)) {
                String group = classify(object.getKind());
                if (group == null || !schema.equals(nullToEmpty(DasUtil.getSchema(object)))) {
                    continue;
                }
                result.add(toEntry(object, schema, group));
            }
        }
        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name()));
        return result;
    }

    private static @NotNull ObjectEntry toEntry(@NotNull DasObject object, @NotNull String schema,
                                                @NotNull String group) {
        ObjectKind kind = object.getKind();
        return new ObjectEntry(
                nullToEmpty(object.getName()),
                schema,
                kind == null ? "" : nullToEmpty(kind.getPresentableName()),
                nullToEmpty(object.getComment()),
                group,
                object instanceof DasTable,
                object
        );
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
