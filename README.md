# Table Structure Viewer

DataGrip 表结构查看插件，在编辑器标签页中展示表和视图的结构，并提供按 schema 浏览数据库对象的总览页面。当前以 Oracle 为主要使用场景。

## 功能与使用

在 Database 工具窗口中选中表，右键选择 **查看表结构**，或使用 `Ctrl+Alt+Shift+S`。同一张表会复用已有标签页。

| 页面 | 展示内容 |
| --- | --- |
| 概要 | 表名、schema、注释、数据源、DBMS 版本及各类对象数量 |
| 列 | 类型、长度、精度、标度、非空、默认值、主外键、自增、计算列、注释 |
| 索引 | 名称、唯一性、函数索引、涉及列、注释 |
| 约束 | 主键、唯一约束及涉及列 |
| 外键 | 本表列、引用表与列、删除和更新规则、延迟性 |

- **对象总览**：从数据库对象树右键菜单或工具栏打开，按 schema 展示表、视图、序列和存储过程，支持名称搜索与类型过滤。双击表或视图查看结构；右键表选择 **编辑表结构…** 可打开 DataGrip 原生修改表对话框。
- **刷新**：表结构页通过 DataGrip 重新内省当前表，再加载模型；不支持单表内省时退回重读模型。数据源模型变化后也会自动刷新。对象总览的刷新会重新读取已内省的模型。
- **复制**：支持复制单元格或整行；点击序号列可选中整行，整行复制使用制表符分隔并省略序号。
- **生成 ALTER DDL**：在列页选择一列或多列，右键预览、复制 Oracle 新增列语句及列注释。插件不会自动执行这些语句。

## 环境要求

| 组件 | 版本或要求 |
| --- | --- |
| DataGrip | 2026.2.6，build `DB-262.10968.148` |
| Java | JDK 25，可使用 DataGrip 自带 JBR |
| Gradle | 9.8.2，仓库已包含 Wrapper，无需另行安装 |
| IntelliJ Platform Gradle Plugin | 2.19.0 |
| 平台依赖 | DataGrip 内置 Database Tools and SQL（`com.intellij.database`） |

构建使用本地 DataGrip 安装。首次运行 Wrapper 和解析未缓存的构建依赖需要网络；不会额外下载目标 IDE。

## 构建与安装

以下命令在项目根目录执行，以 macOS 为例。将 `DATAGRIP_HOME` 改为实际安装位置：

```bash
export DATAGRIP_HOME="$HOME/Applications/DataGrip.app"
export JAVA_HOME="$DATAGRIP_HOME/Contents/jbr/Contents/Home"

./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" buildPlugin
```

`datagripPath` 在 macOS 上必须指向 `.app/Contents`。也可在用户级 `~/.gradle/gradle.properties` 中设置 `datagripPath` 的绝对路径，省略命令中的 `-PdatagripPath=...`；个人安装路径不要提交到仓库。

当前版本安装包为 `build/distributions/datagrip-table-structure-0.1.1.zip`，插件 ID 为 `com.github.chaaaaa.datagripts`。在 DataGrip 中依次打开 **Settings → Plugins → 齿轮菜单 → Install Plugin from Disk…**，选择安装包并重启。

**旧版迁移**：旧插件 ID 为 `com.example.tablestructure`。新 ID 会被 DataGrip 识别为另一款插件，请先卸载旧版再安装，避免重复菜单和快捷键冲突。后续相同 ID 的更新可重复安装，卸载则在 Installed 列表中操作。

开发与兼容性检查：

```bash
# 启动安装本插件的沙箱 DataGrip
./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" runIde

# 运行自检
./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" check

# 检查插件描述与安装包结构
./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" verifyPluginProjectConfiguration verifyPluginStructure

# 使用本地 DataGrip 检查二进制兼容性
./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" verifyPlugin
```

沙箱 DataGrip 仍需要有效授权。普通 `buildPlugin` 产物未签名。

## 本地自检

以下脚本使用 DataGrip 自带 JBR 和平台 JAR，无需 Gradle，也不会启动 IDE。默认安装位置为 `~/Applications/DataGrip.app`，可通过 `DATAGRIP_HOME` 覆盖。

```bash
./scripts/compile-check.sh       # 编译全部主源码
./scripts/pick-schema-check.sh   # schema 默认选择与回退规则
./scripts/alter-ddl-check.sh     # Oracle ALTER DDL 拼接与注释转义
```

自检源码位于独立的 `src/selfCheck/java` source set，通过 `main` 方法运行，不依赖 JUnit。Gradle 的 `check` 已包含 `pickSchemaCheck` 和 `alterDdlCheck`，也可以使用上述脚本分别执行；`gradle test` 不会运行这些自检。

## 工程结构

```text
├── AGENTS.md                         # AI 开发约定
├── README.md                         # 功能、构建与维护说明
├── build.gradle.kts                  # 构建、平台依赖与插件配置
├── settings.gradle.kts
├── gradle.properties                 # 共享版本与构建参数
├── gradle/wrapper/                   # Gradle Wrapper
├── gradlew / gradlew.bat
├── scripts/                          # 编译与自检入口
└── src/
    ├── main/
    │   ├── java/com/github/chaaaaa/datagripts/
    │   │   ├── action/               # 菜单动作、打开入口、原生修改表桥接
    │   │   ├── editor/               # 虚拟文件、编辑器、Provider、标签页复用
    │   │   ├── model/                # 结构快照与对象标识
    │   │   ├── service/              # 模型加载、内省刷新、DDL 生成
    │   │   └── ui/                   # Swing 面板、表格、筛选与复制
    │   └── resources/META-INF/plugin.xml
    └── selfCheck/java/com/github/chaaaaa/datagripts/
        ├── editor/PickSchemaCheck.java
        └── service/AlterDdlCheck.java
```

Java 根包及 Gradle group 为 `com.github.chaaaaa.datagripts`。名称、版本与最低平台 build 在 `gradle.properties` 中维护；菜单动作与编辑器扩展在 `plugin.xml` 中注册。开发约定见 [AGENTS.md](AGENTS.md)。

## 实现边界

- 结构信息来自 DataGrip 已内省的 `Das*` / PSI 模型，插件不自行建立 JDBC 连接。序列、存储过程等未显示时，先检查数据源的内省范围与级别。
- 模型在后台读锁中加载，转换为展示数据后回到 EDT 更新界面；编辑器关闭时取消加载并断开消息订阅。项目级服务复用虚拟文件，避免重复标签页。
- 当前使用 `LightVirtualFile`，IDE 重启后不会恢复结构和对象总览标签页。
- ALTER DDL 仅支持 Oracle 新增列及列注释，不包含索引、主外键或其他约束；自增列、计算列和缺失类型的列会提示跳过。类型与默认值沿用模型文本，执行前需要核对目标库。
- 暂未提供表空间、分区、行数、CHECK 约束、完整建表 DDL 或导出功能。
- 使用了 DataGrip 内部 API；最低平台 build 为 `262`，未限制最高 build，不代表已验证所有后续版本。升级 DataGrip 后应重新编译并验证兼容性。
