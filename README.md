# Table Structure Viewer

**English short description:** Read-only table and view structures, schema browsing, and Oracle ADD COLUMN SQL previews for DataGrip.

**中文简述：** 在 DataGrip 中查看表和视图的只读结构、按 schema 浏览对象，并预览 Oracle 新增列 SQL。

## English overview

Table Structure Viewer adds read-only table and view structure tabs and a schema-based object browser to DataGrip. Inspect columns, data types, default values, comments, indexes, primary and unique keys, and foreign keys without leaving the editor area.

- Browse tables, views, sequences, and stored routines by schema, with name search and object-type filters.
- Drag-select cells and copy the selection, or copy a row, as tab-separated text.
- For tables, open DataGrip's native Modify Table dialog, data editor, or generated CREATE SQL in a SQL console.
- Preview and copy Oracle ADD COLUMN statements and column comments for selected columns. This preview always uses Oracle syntax; it does not adapt to other database dialects or execute SQL automatically.

**The plugin UI is currently in Simplified Chinese.** In the Database tool window, right-click a table or view and choose **查看表结构** (View Table Structure). Choose **对象总览** (Object Overview) to browse a data source. In the Columns tab, select columns and choose **生成 Oracle ALTER DDL…** to preview their SQL.

Requires DataGrip with Database Tools and SQL. Built and binary-compatibility checked against DataGrip **2026.2.6 (build 262)**; other IDE releases and database dialects have not been comprehensively tested. Structure data comes from DataGrip's introspected model. The overview refresh reloads that model; if objects are missing, check the selected schema, permissions, and introspection settings, refresh the data source in DataGrip, and then reload the overview.

The structure viewer is read-only, but the native table tools can modify data or structure when you apply changes there. Generated SQL is not executed automatically. The Oracle selected-column preview skips identity, computed, and untyped columns with an explanatory SQL comment. Custom structure and overview tabs are not restored after an IDE restart.

## 中文介绍

Table Structure Viewer 为 DataGrip 提供表和视图的只读结构标签页，以及按 schema 浏览数据库对象的总览。可以查看列、数据类型、默认值、注释、索引、主键、唯一约束和外键，拖动选中并复制单元格（或复制整行），并快捷打开 DataGrip 原生表操作工具。支持预览和复制 Oracle 新增列 SQL，生成的语句不会自动执行。**当前插件界面为简体中文。**

## 功能与使用

在 Database 工具窗口中选中表或视图，右键选择 **查看表结构**，或使用默认快捷键 `Ctrl+Alt+Shift+S`（可在 Keymap 中调整）。同一对象会复用已有标签页。

| 区域 / 标签页 | 展示内容 |
| --- | --- |
| 顶部概要 | 表名、schema、注释、数据源、DBMS 版本及各类对象数量 |
| 列 | 类型、长度、精度、标度、非空、默认值、注释、主外键、自增、计算列 |
| 索引 | 名称、唯一性、函数索引、涉及列、注释 |
| 约束 | 主键、唯一约束及涉及列 |
| 外键 | 本表列、引用表与列、删除和更新规则、延迟性 |

- **对象总览**：从数据库对象树右键菜单或工具栏打开，按 schema 展示表、视图、序列和存储过程，支持名称搜索与类型过滤。快速左键双击表或视图查看结构，双击间隔遵循系统设置；右键菜单提供 **复制单元格、复制行、查看表结构、修改表结构、查看数据、刷新**，其中修改表结构与查看数据仅对表可用。
- **对象操作**：表结构页右上角的 **更多** 菜单提供 **修改表结构、查看数据、生成创建 SQL**，三项均仅对表可用，视图只支持查看结构。修改表结构打开 DataGrip 原生修改表对话框，查看数据打开原生数据编辑标签页；生成创建 SQL 由 DataGrip 生成 DDL 并打开原生 SQL 控制台，不会自动执行。
- **只读范围**：插件的结构视图为只读；通过原生修改表对话框或数据编辑器应用修改时，仍可能更改数据库结构或数据。
- **刷新**：表结构页通过 DataGrip 重新内省当前表，再加载模型；不支持单表内省时退回重读模型。数据源模型变化后也会自动刷新。对象总览的刷新会重新读取当前 schema 已内省的对象列表。
- **复制**：在对象总览和对象页签的表格中按住左键拖动，会选中起点到当前单元格的矩形区域；`Ctrl+C` 或右键「复制单元格」只复制选中的单元格，列间制表符、行间换行。序号列不参与，仅选中序号列时复制其文本。右键「复制行」复制当前行全部列（制表符分隔，不含序号）。
- **生成 Oracle ALTER DDL**：在列页选择一列或多列，右键预览、复制 Oracle 新增列语句及列注释。该预览始终使用 Oracle 语法，不自动适配其他数据库，也不自动执行语句。

对象列表来自 DataGrip 已内省的模型。若缺少对象，请检查 schema、权限和内省设置，先在 DataGrip 刷新数据源，再刷新对象总览。序列和存储过程目前仅在总览中列出，不提供自定义结构标签页。

## 开发环境要求

以下环境用于从源码构建。安装已经打包的插件只需兼容的 DataGrip 环境，无需另外安装 Gradle 或配置项目目录。

| 组件 | 版本或要求 |
| --- | --- |
| DataGrip SDK | 默认使用 2026.2.6，版本由 `gradle.properties` 的 `datagripVersion` 固定 |
| Java | JDK 25，可使用 DataGrip 自带 JBR |
| Gradle | 9.8.2，仓库已包含 Wrapper，无需另行安装 |
| IntelliJ Platform Gradle Plugin | 2.19.0 |
| 平台依赖 | DataGrip 内置 Database Tools and SQL（`com.intellij.database`） |

默认由 Gradle 下载固定版本的 DataGrip SDK，无需预先安装 DataGrip。首次获取 SDK、Gradle Wrapper 发行包和未缓存的构建依赖需要网络，后续构建复用缓存；也可以指定已有 DataGrip 安装。

## 构建与安装

先安装 JDK 25，并将 `JAVA_HOME` 指向 JDK 根目录。以下命令在项目根目录执行。

macOS / Linux：

```bash
./gradlew buildPlugin
```

Windows PowerShell：

```powershell
.\gradlew.bat buildPlugin
```

可选本地覆盖：为同一命令添加 `-PdatagripPath="实际安装路径"`，macOS 指向 `DataGrip.app/Contents`，Windows / Linux 指向 DataGrip 安装根目录。也可在用户级 Gradle 配置（默认 `~/.gradle/gradle.properties`）中设置 `datagripPath`，个人路径不要写入仓库的 `gradle.properties`。设置后优先使用该本地安装，移除该属性即可恢复默认 SDK 下载。若使用 DataGrip 自带 JBR 作为 JDK，请将 `JAVA_HOME` 指向该 JBR 的 Java Home。

当前插件版本为 **1.0.1**，构建后安装包为 `build/distributions/datagrip-table-structure-1.0.1.zip`，插件 ID 为 `com.github.chaaaaa.datagripts`。在 DataGrip 中依次打开 **Settings → Plugins → 齿轮菜单 → Install Plugin from Disk…**，选择安装包并重启。

如果此前安装过插件 ID 为 `com.example.tablestructure` 的旧包，请先卸载旧包，避免重复菜单和快捷键。当前插件保持 ID `com.github.chaaaaa.datagripts`，后续更新沿用同一安装身份。

开发与兼容性检查（Windows 将 `./gradlew` 替换为 `.\gradlew.bat`，本地 SDK 覆盖同样适用）：

```bash
# 启动安装本插件的沙箱 DataGrip
./gradlew runIde

# 运行自检
./gradlew check

# 检查插件描述与安装包结构
./gradlew verifyPluginProjectConfiguration verifyPluginStructure

# 使用本次构建所选的 SDK 检查二进制兼容性
./gradlew verifyPlugin
```

沙箱 DataGrip 仍需要有效授权。普通 `buildPlugin` 产物未签名。

## 可选的 macOS 本地自检

标准自检入口是 Gradle `check`。下面四个脚本仅作为 macOS 上的离线快捷入口，使用已安装 DataGrip 的 JBR 和平台 JAR，无需 Gradle，也不会启动 IDE。默认安装位置为 `~/Applications/DataGrip.app`，可通过 `DATAGRIP_HOME` 覆盖；脚本中的 `.app/Contents` 目录结构不适用于 Windows / Linux。

```bash
./scripts/compile-check.sh          # 编译全部主源码
./scripts/pick-schema-check.sh      # schema 默认选择与回退规则
./scripts/alter-ddl-check.sh        # Oracle ALTER DDL 拼接与注释转义
./scripts/copy-selection-check.sh   # 复制选中单元格的文本拼接
```

自检源码位于独立的 `src/selfCheck/java` source set，通过 `main` 方法运行，不依赖 JUnit。Gradle 的 `check` 已包含 `pickSchemaCheck`、`alterDdlCheck` 和 `copySelectionCheck`，也可以使用上述脚本分别执行；`gradle test` 不会运行这些自检。

## 工程结构

```text
├── AGENTS.md                         # AI 开发约定
├── README.md                         # 功能、构建与维护说明
├── LICENSE                           # MIT 许可证
├── build.gradle.kts                  # 构建、平台依赖与插件配置
├── settings.gradle.kts
├── gradle.properties                 # 共享版本与构建参数
├── gradle/wrapper/                   # Gradle Wrapper
├── gradlew / gradlew.bat
├── scripts/                          # 可选的 macOS 离线编译与自检入口
└── src/
    ├── main/
    │   ├── java/com/github/chaaaaa/datagripts/
    │   │   ├── action/               # 菜单动作、打开入口、原生对象操作桥接
    │   │   ├── editor/               # 虚拟文件、编辑器、Provider、标签页复用
    │   │   ├── model/                # 结构快照与对象标识
    │   │   ├── service/              # 模型加载、内省刷新、DDL 生成
    │   │   └── ui/                   # Swing 面板、表格、筛选与复制
    │   └── resources/META-INF/plugin.xml
    └── selfCheck/java/com/github/chaaaaa/datagripts/
        ├── editor/PickSchemaCheck.java
        ├── service/AlterDdlCheck.java
        └── ui/CopySelectionCheck.java
```

Java 根包及 Gradle group 为 `com.github.chaaaaa.datagripts`。插件名称、版本、默认 SDK 版本与最低平台 build 在 `gradle.properties` 中维护；菜单动作与编辑器扩展在 `plugin.xml` 中注册。开发约定见 [AGENTS.md](AGENTS.md)。

## 实现边界

- 结构信息来自 DataGrip 已内省的 `Das*` / PSI 模型，插件不自行建立 JDBC 连接。序列、存储过程等未显示时，先检查数据源的内省范围与级别。
- 模型在后台读锁中加载，转换为展示数据后回到 EDT 更新界面；编辑器关闭时取消加载并断开消息订阅。项目级服务复用虚拟文件，避免重复标签页。
- 当前使用 `LightVirtualFile`，IDE 重启后不会恢复结构和对象总览标签页。
- ALTER DDL 仅支持 Oracle 新增列及列注释，不包含索引、主外键或其他约束；自增列、计算列和缺失类型的列会提示跳过。类型与默认值沿用模型文本，执行前需要核对目标库。
- 创建 SQL 由 DataGrip 原生 DDL 生成能力提供，内容范围和完整性取决于已内省的模型及 DataGrip 设置。
- 结构页暂未展示表空间、分区、行数或 CHECK 约束，也未提供独立导出功能。
- 使用了 DataGrip 内部 API；最低平台 build 为 `262`，未限制最高 build，不代表已验证所有后续版本。升级 DataGrip 后应重新编译并验证兼容性。
