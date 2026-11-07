package com.example.tablestructure.service;

import com.example.tablestructure.model.ColumnInfo;

import java.util.List;

/**
 * 自检：{@link AlterDdlGenerator} 生成的 ALTER 语句。
 * 不在 src/main/java 下，不会打进插件包；由 scripts/alter-ddl-check.sh 运行。
 */
public final class AlterDdlCheck {

    private static int checked;

    public static void main(String[] args) {
        // 1) 单列 + 注释：ADD (...) 与 COMMENT ON COLUMN 各一条
        String one = AlterDdlGenerator.addColumns("HR", "EMP",
                List.of(column("BONUS", "NUMBER(10,2)", false, "", "年度奖金")));
        expect("单列 ADD", "ALTER TABLE \"HR\".\"EMP\" ADD (\"BONUS\" NUMBER(10,2));\n"
                + "COMMENT ON COLUMN \"HR\".\"EMP\".\"BONUS\" IS '年度奖金';", one);

        // 2) 多列：每列一条独立的 ALTER，中间换行
        String many = AlterDdlGenerator.addColumns("HR", "EMP", List.of(
                column("A", "VARCHAR2(10)", false, "", ""),
                column("B", "NUMBER", true, "", "")));
        expect("多列 ADD", "ALTER TABLE \"HR\".\"EMP\" ADD (\"A\" VARCHAR2(10));\n"
                + "ALTER TABLE \"HR\".\"EMP\" ADD (\"B\" NUMBER NOT NULL);", many);

        // 3) 默认值放在 NOT NULL 之前（Oracle 语法要求 DEFAULT 在 NOT NULL 前）
        String withDefault = AlterDdlGenerator.addColumns("HR", "EMP",
                List.of(column("FLAG", "CHAR(1)", true, "'N'", "")));
        expect("默认值+非空", "ALTER TABLE \"HR\".\"EMP\" ADD (\"FLAG\" CHAR(1) DEFAULT 'N' NOT NULL);", withDefault);

        // 4) 注释里的单引号要转义成两个
        String quoted = AlterDdlGenerator.addColumns("HR", "EMP",
                List.of(column("C", "VARCHAR2(5)", false, "", "it's ok")));
        expect("注释转义", "ALTER TABLE \"HR\".\"EMP\" ADD (\"C\" VARCHAR2(5));\n"
                + "COMMENT ON COLUMN \"HR\".\"EMP\".\"C\" IS 'it''s ok';", quoted);

        // 5) 没有注释就不生成 COMMENT ON
        expect("无注释不生成 COMMENT", false, many.contains("COMMENT ON COLUMN"));

        // 6) 空列表生成空串（调用方据此跳过弹窗）
        expect("空列表", "", AlterDdlGenerator.addColumns("HR", "EMP", List.of()));

        // 7) 没有默认值/非空时不多出空格
        expect("最小定义", "ALTER TABLE \"T\" ADD (\"X\" NUMBER);",
                AlterDdlGenerator.addColumns("", "T", List.of(column("X", "NUMBER", false, "", ""))));

        // 8) 类型没读到（空）时不生成不可执行的 SQL，只留一条注释
        expect("类型为空跳过", "-- 跳过 Y：未读到类型，请手工补全",
                AlterDdlGenerator.addColumns("", "T", List.of(column("Y", "", false, "", ""))));

        // 9) 有类型的一列 + 类型为空的一列：只生成前者
        expect("混合", "ALTER TABLE \"T\" ADD (\"X\" NUMBER);\n-- 跳过 Y：未读到类型，请手工补全",
                AlterDdlGenerator.addColumns("", "T", List.of(
                        column("X", "NUMBER", false, "", ""),
                        column("Y", "", false, "", ""))));

        System.out.println("ALTER DDL 自检通过：" + checked + " 个用例");
    }

    private static ColumnInfo column(String name, String type, boolean notNull, String defaultValue, String comment) {
        return new ColumnInfo(0, name, type, 0, 0, 0, notNull, defaultValue,
                false, false, false, false, comment);
    }

    private static void expect(String name, String expected, String actual) {
        checked++;
        if (!expected.equals(actual)) {
            throw new AssertionError(name + "：期望\n[" + expected + "]\n实际\n[" + actual + "]");
        }
    }

    private static void expect(String name, boolean expected, boolean actual) {
        checked++;
        if (expected != actual) {
            throw new AssertionError(name + "：期望 " + expected + "，实际 " + actual);
        }
    }
}
