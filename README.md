# Table Structure Viewer（DataGrip 插件）

在 **DataGrip 编辑器标签页（editor tab）** 中以只读表格方式查看数据库表结构 —— 不使用弹窗。

- 目标环境：**DataGrip 2026.2.6**（build `DB-262.10968.148`），依赖内置 Database Tools and SQL 插件（`com.intellij.database`）
- 入口一：数据库对象树右键菜单 → **查看表结构**（快捷键 `Ctrl+Alt+Shift+S`）
- 入口二：数据库对象树右键菜单 / 工具栏 → **对象总览**：
  - **按 schema 查看**：顶部 schema 下拉框切换，一次只查一个 schema 的对象（默认跟随连接当前 schema）
  - 列出所选中 schema 下的表、视图、序列、存储过程，页面内模糊搜索（忽略大小写）+ 类型过滤
  - 双击表或视图打开其结构
  - **右键表 → 编辑表结构…**：调用 DataGrip 原生「修改表」对话框（改列/索引/约束并预览 DDL）
- 内容：
  - 顶部概要：表名 / schema / 注释 / 数据源 / DBMS 版本 / 列·索引·约束·外键数量
  - **列**：序号、列名、类型、长度、精度、标度、非空、默认值、主键、外键、自增、计算列、注释
  - **索引**：索引名、唯一、函数索引、索引列、注释
  - **约束**：主键 / 唯一约束及其列
  - **外键**：外键名、列、引用表、引用列、ON DELETE / ON UPDATE、延迟性

完整设计与取舍：[docs/实现方案.md](docs/实现方案.md)

---

## 目录结构

```
├── build.gradle.kts / settings.gradle.kts / gradle.properties
├── scripts/compile-check.sh        # 无 Gradle 时的编译自检（JBR 25 + 真实平台 jar）
├── gradle/wrapper/gradle-wrapper.properties
├── docs/
│   ├── 实现方案.md                 # 设计方案（架构/API/UI/步骤/风险）
│   └── research/                   # 本机 DataGrip 2026.2.6 的 API/机制取证
│       ├── gradle-and-platform-setup.md  # Gradle/平台构建实测（含 14 个坑）
│       ├── editor-tab-mechanics.md       # 编辑器 tab / FileEditorProvider / 自定义 VFS 等（1000+ 行）
│       └── snippets/                     # 可编译的备选实现（自定义 VFS 重启恢复）
└── src/main/
    ├── java/com/example/tablestructure/
    │   ├── action/                 # 菜单动作 + 打开 tab（含对象总览）
    │   ├── editor/                 # FileType / VirtualFile / FileEditorProvider / FileEditor / Service
    │   ├── model/                  # 不可变 DTO
    │   ├── service/                # Das* 模型 → DTO
    │   └── ui/                     # 只读表格模型 + 面板
    └── resources/META-INF/plugin.xml
```

## 环境要求

| 依赖 | 版本 | 说明 |
|------|------|------|
| DataGrip | 2026.2.6 | 本机安装路径默认 `/Users/wangyc/Applications/DataGrip.app`，`gradle.properties` 的 `datagripPath` 需指向 `DataGrip.app/Contents`（Gradle 的 `local()` 要求） |
| JDK | 25 | 2026.2 平台字节码为 Java 25；可直接用 DataGrip 自带 JBR：`Contents/jbr/Contents/Home` |
| Gradle | 9.8.2 | 本机未安装、`~/.gradle` 也不存在；首次构建会下载 Gradle/插件依赖（需网络），见下方 wrapper 生成方法 |
| IntelliJ Platform Gradle Plugin | 2.19.0 | `build.gradle.kts` 已声明 |

## 构建与运行（Gradle）

```bash
# 1) 首次生成 wrapper：任选其一
#    a) 本机已有 Gradle 9.8.2：gradle wrapper --gradle-version 9.8.2 --distribution-type bin
#    b) 用 IntelliJ IDEA / DataGrip 打开工程，让 IDE 生成 wrapper
#    c) 临时下载 Gradle：
#       curl -L -o /tmp/gradle-9.8.2-bin.zip https://services.gradle.org/distributions/gradle-9.8.2-bin.zip
#       unzip -q /tmp/gradle-9.8.2-bin.zip -d /tmp
#       /tmp/gradle-9.8.2/bin/gradle wrapper --gradle-version 9.8.2 --distribution-type bin

# 2) 启动带插件的沙箱 DataGrip，手工验证
./gradlew runIde

# 3) 打插件安装包 -> build/distributions/datagrip-table-structure-0.1.0.zip
./gradlew buildPlugin

# 4) 兼容性检查（针对目标平台）
./gradlew verifyPlugin
```

安装：DataGrip → `Settings | Plugins | ⚙ | Install Plugin from Disk…` 选择上面的 zip，重启。

> **构建状态**：本机无 Gradle、网络极慢，未实际执行 Gradle 构建；构建脚本依据官方源码/文档编写
> （见 [docs/research/gradle-and-platform-setup.md](docs/research/gradle-and-platform-setup.md)），
> Java 源码已用真实平台 jar 编译通过（见下节）。
>
> 沙箱 `runIde` 需要 DataGrip 授权；插件开发自用通常可直接试用。CI 请用
> CI 或无本地安装时，用 `datagrip("2026.2.6")` 替代 `local(...)`
> （DataGrip 没有 Maven 坐标，`useInstaller` 默认 `true`，会从 JetBrains CDN 下载安装包）。


## 安装到本地 DataGrip（2026.2.x）

构建产物：`build/distributions/datagrip-table-structure-0.1.0.zip`（plugin id `com.example.tablestructure`，
`since-build=262`，无 `until-build`）。

**方式一：IDE 安装（推荐）**

1. DataGrip → `Settings…`（macOS `⌘,`）→ `Plugins`
2. 点齿轮 ⚙ → `Install Plugin from Disk…`
3. 选择 `build/distributions/datagrip-table-structure-0.1.0.zip` → `Restart IDE`
4. 验证：`Settings → Plugins → Installed` 中能看到 **Table Structure Viewer**

**方式二：命令行解压到插件目录（适合脚本化）**

```bash
PLUGINS="$HOME/Library/Application Support/JetBrains/DataGrip2026.2/plugins"
cd "$PLUGINS"
unzip -q /Users/wangyc/Documents/project/my/datagrip/build/distributions/datagrip-table-structure-0.1.0.zip
# 重启 DataGrip
```

zip 顶层已含 `datagrip-table-structure/` 目录，直接解压到 `plugins/` 下即可。

**使用**：打开 Database 工具窗 → 选一个 Oracle 数据源 → 展开 schema → 右键表 →
**查看表结构**（在右键菜单顶部）；或焦点在数据库树时按 `Ctrl+Alt+Shift+S`。

**卸载/更新**：`Settings → Plugins` 中卸载；更新时重复 Install from Disk 覆盖安装后重启。

> 本产物是本地构建、未签名，仅建议自用；升级 DataGrip 大版本后需要重新构建。

## 本地编译验证（无需 Gradle）

本机没有 Gradle，代码已用 DataGrip 自带 JBR 25 的 `javac` + 真实平台 jar 验证通过（exit 0、无 warning）。
可重复执行：

```bash
./scripts/compile-check.sh            # 默认使用 ~/Applications/DataGrip.app
# 或指定 DataGrip 路径：
DATAGRIP_HOME=/Applications/DataGrip.app ./scripts/compile-check.sh
```

分支逻辑自检（无需 Gradle / 不启动 IDE）：

```bash
./scripts/pick-schema-check.sh   # 总览默认选中哪个 schema
./scripts/alter-ddl-check.sh     # ALTER DDL 生成（含注释转义、默认值/非空顺序）
```

脚本内部等价于：

```bash
DG=~/Applications/DataGrip.app
CP=$(ls "$DG/Contents/lib"/*.jar | tr '\n' ':')$(ls "$DG/Contents/plugins/DatabaseTools/lib"/*.jar | tr '\n' ':')$(ls "$DG/Contents/plugins/DatabaseTools/lib/modules"/*.jar | tr '\n' ':')
"$DG/Contents/jbr/Contents/Home/bin/javac" -Xlint:deprecation -proc:none -d /tmp/out -cp "$CP" $(find src/main/java -name '*.java')
```

## 当前实现范围（v1）

- ✅ 编辑器 tab 打开 / 复用（同一张表不会开重复 tab）
- ✅ 对象总览按 schema 切换（一次只查一个 schema），默认跟随连接当前 schema
- ✅ 对象总览右键表 → 编辑表结构（复用 DataGrip 原生「修改表」对话框，不自研 DDL）
- ✅ 模型取数：列 / 索引 / 主键唯一约束 / 外键 / 注释 / 类型与长度（不发起 JDBC 查询）
- ✅ 后台读锁加载 + EDT 渲染；刷新按钮；数据源模型变化自动刷新
- ✅ 表格复制：Ctrl+C 或右键复制**单元格**内容；点序号列选中整行后复制整行（制表符分隔，不含序号）
- ✅ 「刷新」按钮会**回到数据库重新内省当前这张表**（fragment 刷新，与对象树右键刷新同一条路径），
  不只是重读本地模型；不支持 fragment 内省的方言自动退化为重读模型
- ✅ 列 Tab 多选列 → 右键「生成 ALTER DDL…」→ 弹窗预览 `ALTER TABLE … ADD (...)` + `COMMENT ON COLUMN`，可一键复制
  （Oracle 语法；对象名称保留大小写并正确引用，自增列、计算列会明确提示跳过，需手工补全定义）
- ✅ 表失效 / 加载失败的提示态
- ⏳ 待增强：重启后 tab 恢复（自定义 VFS）、DDL 内页、Oracle 深度信息（表空间/分区/行数/CHECK 约束）、导出
   （见 [docs/实现方案.md](docs/实现方案.md) §8.3、§9）

## 注意事项

- 插件使用了 `com.intellij.database.*`（DataGrip 内部 API）：跨 IDE 大版本升级需回归验证，并建议固定
  `since-build` / `until-build`。
- **DataGrip 2026.2 其实有一个实验性的“表编辑器 tab”**（内含 Data / Structure / DDL 内页），
  由注册表开关 `database.editor.composite` 控制（默认关闭）。如果只是自己用、不要求定制 UI，
  可以先试它；本插件的价值在于**只读、轻量、一屏展示列+索引+约束+外键**，且不依赖实验开关。
  详见 [docs/实现方案.md](docs/实现方案.md) 第 0 节。
