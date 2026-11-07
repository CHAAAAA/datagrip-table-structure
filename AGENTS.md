# 项目开发指引

## 协作要求

1. 使用中文回复。
2. 对不能通过代码、配置或检查结果确定的问题，先向用户确认，不猜测需求或行为。
3. 未得到明确指令，不执行 `git commit` 或 `git push`。
4. 开发完成后不自动打开浏览器或开展视觉检查；需要时由用户明确提出。
5. 代码评审的每项结论都要编号，便于用户逐项反馈。
6. 以可读性和最小完整改动为目标，复用现有代码，避免过度抽象、封装和拆分，不为未提出的需求增加依赖或配置。
7. 可以编写临时测试验证任务，完成后删除本次新增的临时测试与临时产物；保留并维护仓库已有自检。
8. 开始修改前检查工作区状态，保留用户已有改动；不要为整理目录而覆盖个人 IDE 配置。

## 项目定位

这是基于 Java 25、IntelliJ Platform Gradle Plugin 的 DataGrip 插件，目标环境为 DataGrip 2026.2.6（平台 build 262），依赖 `com.intellij.database`。主要提供表结构只读标签页、对象总览以及 Oracle 新增列 DDL 预览。

根包及插件 ID 为 `com.github.chaaaaa.datagripts`。保持现有单模块结构，不引入新的框架或分层。

## 修改位置

| 位置 | 职责与入口 |
| --- | --- |
| `src/main/java/com/github/chaaaaa/datagripts/action/` | 菜单动作、`TableStructureOpener` / `ObjectBrowserOpener`、原生修改表桥接 |
| `src/main/java/com/github/chaaaaa/datagripts/editor/` | `FileEditor`、Provider、VirtualFile；`TableStructureService` 负责项目级标签页复用 |
| `src/main/java/com/github/chaaaaa/datagripts/model/` | 表结构快照、列与约束信息、对象标识 |
| `src/main/java/com/github/chaaaaa/datagripts/service/` | `TableStructureLoader`、`ObjectBrowserLoader`、`TableRefreshService`、`AlterDdlGenerator` |
| `src/main/java/com/github/chaaaaa/datagripts/ui/` | Swing 面板、只读表格、过滤、复制和 DDL 预览 |
| `src/main/resources/META-INF/plugin.xml` | 插件描述、扩展注册、菜单与快捷键 |
| `src/selfCheck/java/com/github/chaaaaa/datagripts/` | 独立 source set 中的 `main` 自检，包结构与被测代码对应 |
| `scripts/` | 编译和自检脚本；不放 Java 源码 |
| `build.gradle.kts` / `gradle.properties` | 构建逻辑、平台依赖、共享版本参数 |

项目说明统一维护在 `README.md`，AI 协作约定维护在本文件，不新增 `docs/` 或重复的方案文档。

## 实现约束

- 先检查调用方、插件注册、测试和脚本引用，再修改类名、包名或方法；迁移 Java 包时同步目录、`package`、`import`、XML 中的实现类路径及自检启动类名。
- Java 包名、插件 ID、Action ID、Editor Type ID 是不同概念。修改安装身份或持久化标识前确认兼容影响，不随重构自动更名。
- 数据来自 DataGrip 已内省的模型。模型和 PSI 访问遵守读锁要求，较重加载使用后台 `ReadAction.nonBlocking`；Swing 状态和组件更新在 EDT 执行。
- UI 负责展示和用户交互，数据库模型加载放在现有 Loader 中。不要在渲染器、筛选器或动作状态更新中加入耗时模型读取。
- 异步工作绑定编辑器生命周期，保留取消、过期与错误处理；回调更新 UI 前检查编辑器是否已释放，避免旧结果覆盖新请求。关闭编辑器时取消任务并断开消息订阅。
- 通过 `TableStructureService` 复用 VirtualFile；不要每次打开同一对象都新建虚拟文件。保留 `FileEditor.getFile()`、Provider 的 `DumbAware` 和轻量 `accept()` 实现。
- 自定义文件类型只用于合成虚拟文件，不绑定 `.sql` 等真实文件扩展名。当前不支持重启恢复标签页，不把它描述为已实现能力。
- 刷新表结构复用 `TableRefreshService` 的 DataGrip 内省流程，保留不支持内省时重读模型的回退行为。编辑表结构复用 `ModifyTableBridge` 的原生对话框。
- 不自行建立 JDBC 连接或自动执行 DDL。`AlterDdlGenerator` 只生成 Oracle 预览文本；保留标识符引用、注释转义、默认值顺序和特殊列跳过处理。
- 复用平台 Swing 组件与主题颜色；保留 HTML 展示文本的转义、只读属性以及排序/筛选后表格索引到模型索引的转换。
- 不把个人绝对路径、构建缓存、安装包或临时输出加入源码；本地路径通过环境变量或用户级 Gradle 配置提供。

## 验证与交付

在项目根目录运行；脚本默认使用 `~/Applications/DataGrip.app`，其他安装位置设置 `DATAGRIP_HOME`：

```bash
./scripts/compile-check.sh
./scripts/pick-schema-check.sh
./scripts/alter-ddl-check.sh
```

现有自检使用 `main` 入口，不依赖 JUnit。Gradle 的 `check` 已通过 JavaExec 任务 `pickSchemaCheck` 和 `alterDdlCheck` 执行它们，`gradle test` 不会运行这些自检。根据改动选择相关检查，包迁移或目录调整应运行全部三项脚本，或执行 Gradle `check`。

涉及构建配置或插件注册时，使用本地 JBR 和平台路径检查安装包：

```bash
export DATAGRIP_HOME="$HOME/Applications/DataGrip.app"
export JAVA_HOME="$DATAGRIP_HOME/Contents/jbr/Contents/Home"
./gradlew -PdatagripPath="$DATAGRIP_HOME/Contents" check buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
```

修改平台依赖或版本时再运行 `verifyPlugin`。用户要求手工验证时使用 `runIde` 启动沙箱；不自动连接数据库、修改表结构或安装到日常使用的 IDE。

完成后检查是否残留旧包名、失效路径和已删除文档的链接。说明实际改动、执行的验证及未验证项；构建受环境限制时如实报告，不把计划执行的检查写成已通过。
