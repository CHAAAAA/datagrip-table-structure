package com.github.chaaaaa.datagripts.editor;

import java.util.List;

/**
 * 自检：{@link ObjectBrowserFileEditor#pickSchema} 的三条优先规则。
 * 不在 src/main/java 下，不会打进插件包；由 scripts/pick-schema-check.sh 运行。
 */
public final class PickSchemaCheck {

    private static int checked;

    public static void main(String[] args) {
        List<String> schemas = List.of("HR", "OE", "PUBLIC");

        // 1) 用户上次的选择优先于连接当前 schema
        expect("上次选择优先", "OE", ObjectBrowserFileEditor.pickSchema(schemas, "OE", "HR"));
        // 2) 没有上次选择时，跟随连接当前 schema
        expect("跟随当前 schema", "HR", ObjectBrowserFileEditor.pickSchema(schemas, null, "HR"));
        // 3) 上次选择已不存在（被删除/改名）时，退回当前 schema
        expect("上次选择失效", "PUBLIC", ObjectBrowserFileEditor.pickSchema(schemas, "GONE", "PUBLIC"));
        // 4) 当前 schema 也不在列表里，退回第一个
        expect("当前 schema 缺失", "HR", ObjectBrowserFileEditor.pickSchema(schemas, null, "NOPE"));
        // 5) 两者都缺失，退回第一个
        expect("都缺失取第一个", "HR", ObjectBrowserFileEditor.pickSchema(schemas, "GONE", "NOPE"));
        // 6) 没有任何 schema
        expect("空列表", null, ObjectBrowserFileEditor.pickSchema(List.of(), null, "HR"));
        // 7) 没有 schema 时 even 有上次选择也给 null（不能返回不存在的 schema）
        expect("空列表+上次选择", null, ObjectBrowserFileEditor.pickSchema(List.of(), "HR", null));

        System.out.println("pickSchema 自检通过：" + checked + " 个用例");
    }

    private static void expect(String name, String expected, String actual) {
        checked++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
