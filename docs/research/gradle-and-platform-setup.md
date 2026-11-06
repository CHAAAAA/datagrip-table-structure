# DataGrip 2026.2 插件构建配置：Gradle / IntelliJ Platform 实测报告

> **验证时间**：2026-11-03
> **验证方式**：仅用 `curl` 抓取 JetBrains 官方源码与文档 + 本机安装包/字节码核对（按要求未使用 `web_search`/`web_fetch`）。
> **标注约定**：`[V]` = 已在本机或源码/文档中直接核实；`[I]` = 由源码推断（未端到端运行）；`[X]` = 未确认。
> 目标工程：`/Users/wangyc/Documents/project/my/datagrip`，插件依赖内置 Database Tools and SQL 插件。

---

## TL;DR (English)

- `IntelliJPlatformType.DataGrip` **exists**, `code = "DB"` (not `"DG"`), and **`maven = null`** — DataGrip is *not* published to the IntelliJ Platform Maven repo. A convenience DSL method **`datagrip("2026.2.6")`** exists and works out of the box because **`useInstaller` defaults to `true`** (resolves a `.dmg` from `download.jetbrains.com`).
- Bundled plugin id is exactly **`com.intellij.database`** (read from `DatabaseTools/lib/database-plugin.jar!/META-INF/plugin.xml`); use `bundledPlugin("com.intellij.database")` + `<depends>com.intellij.database</depends>`.
- Local-IDE targeting uses **`local(...)`** and **must point at `DataGrip.app/Contents`**, not the `.app` bundle root (verified in `ProductInfoPathResolver`).
- Java 25 needs **Gradle ≥ 9.1.0**; plugin 2.x needs Gradle ≥ 9.0.0. Current: **Gradle 9.8.2**, **IntelliJ Platform Gradle Plugin 2.19.0**.
- The Gradle plugin already derives the **Java 25 toolchain** from build 262 and deliberately does **not** set `options.release` — don't set it either.
- `~/.gradle` **does not exist** on this machine — nothing is cached, no offline build is possible.

---

## 1. 本机环境核对 `[V]`

| 项 | 值 | 来源 |
|---|---|---|
| 产品 / 版本 | DataGrip **2026.2.6** | `Contents/Resources/product-info.json` |
| buildNumber | **262.10968.148** | 同上（`productCode = "DB"`） |
| `minRequiredJavaVersion` | **25** | 同上 |
| 随附 JBR | **JBR-25.0.4+1-508.27**（`JAVA_VERSION="25.0.4"`） | `Contents/jbr/Contents/Home/release` |
| JBR 是否有 `javac` | **有**，且 `javac --release 25` 实测编译通过 | 本机实测（见 §4.4） |
| JBR 是否有 `javap` | **无** | 本机实测 |
| JBR 是否有 `jmods/` | **无**（不影响编译，只影响 `jlink`） | 本机实测 |
| 系统 JDK | 默认 **21.0.5**（`/usr/libexec/java_home`），另有 17.x / 8.x | `java_home -V` |
| Gradle | **未安装**（无 `gradle`、无 `~/.sdkman`） | 本机实测 |
| `~/.gradle` | **不存在**（与任务描述中的“已存在 caches/wrapper/settings.xml”不符） | 本机实测 |
| `~/.m2` | 存在，2.7 GB（Maven 仓库，含 `settings.xml`） | 本机实测 |

> ⚠️ 任务描述里“`~/.gradle` 存在并已缓存”的前提**不成立**：`test -d ~/.gradle` → ABSENT，全盘 `find` 也没有第二个 `.gradle`。因此**不可能离线构建**，见 §8。

---

## 2. 目标平台：`IntelliJPlatformType.DataGrip`

### 2.1 枚举定义：code 是 `DB`，且没有 Maven 坐标 `[V]`

`src/main/kotlin/org/jetbrains/intellij/platform/gradle/IntelliJPlatformType.kt`：

```kotlin
DataGrip(
    code = "DB",
    maven = null,
    installer = Coordinates("datagrip", "datagrip"),
),
```

对比 `IntellijIdeaUltimate(code = "IU", maven = Coordinates("com.jetbrains.intellij.idea", "ideaIU"), ...)`。

**两个关键结论**：

1. **类型码是 `DB` 而不是 `DG`**。它出现在 `productInfo.productCode`、平台的 `build.txt`/`product-info.json`、以及插件自身描述符里（`DB-262.10968.148`）。
2. **`maven = null`** → DataGrip 没有 `com.jetbrains.intellij.datagrip:datagrip:...` 这样的 Maven 构件，**不能**像 IDEA 那样从 `intellij-repository/releases` 拉取。

### 2.2 因此：必须走 installer，或直接用本地安装

`IntelliJPlatformDependenciesHelper.kt`：

```kotlin
private fun createIntelliJPlatformDependency(requestedIntelliJPlatform: RequestedIntelliJPlatform) =
    with(requestedIntelliJPlatform) {
        ...
        when {
            productMode == ProductMode.FRONTEND -> createIntelliJPlatformInstaller(IntelliJPlatformType.JetBrainsClient, version)
            useInstaller -> createIntelliJPlatformInstaller(type, version)   // ← 默认分支
            else -> createIntelliJPlatform(type, version)
        }
    }

private fun createIntelliJPlatform(type: IntelliJPlatformType, version: String): Dependency {
    requireNotNull(type.maven) { "Specified type '$type' has no artifact coordinates available." }  // ← DataGrip 在这里炸
    ...
}
```

即：**如果把 `useInstaller` 设成 `false`，DataGrip 会直接报
`Specified type 'DataGrip' has no artifact coordinates available.`**

### 2.3 `useInstaller` 的默认值是 `true` `[V]`

源码里是硬编码的（`IntelliJPlatformDependenciesHelper.kt`）：

```kotlin
internal val requestedIntelliJPlatforms by lazy {
    gradle.registerClassLoaderScopedBuildService(RequestedIntelliJPlatformsService::class, projectPath) {
        parameters {
            useInstaller = true
        }
    }.get()
}
```

官方文档同样写明 *“useInstaller … Default: **true**”*（Dependency Configuration Parameters 表）。

➡️ **所以 `datagrip("2026.2.6")` 开箱即用**，不需要额外写 `useInstaller = true`（写了也无害，更自证意图）。

### 2.4 便捷 DSL 方法确实存在：`datagrip(...)`（全小写，G 不大写）`[V]`

`extensions/IntelliJPlatformDependenciesExtension.kt`：

```kotlin
/**
 * Adds a dependency on DataGrip.
 *
 * @param version The version of DataGrip.
 */
@JvmOverloads
fun datagrip(
    version: String,
    configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
) = create(IntelliJPlatformType.DataGrip, version, configure)

/** 同上，Provider 重载 */
@JvmOverloads
fun datagrip(
    version: Provider<String>,
    configure: Action<IntelliJPlatformDependencyConfiguration> = Action {},
) = create(IntelliJPlatformType.DataGrip, version, configure)
```

- 方法名是 **`datagrip`**（不是 `dataGrip`，也不是 `dataGrip("...")`）。文档的函数表里同样写作 `datagrip(version, configure = {})`。
- `create(IntelliJPlatformType.DataGrip, "2026.2.6")` 与 `datagrip("2026.2.6")` **完全等价**（前者就是后者的实现）。
- `create(type: Any, ...)` 的第一个参数是 `Any`，所以也可以写 `create("DB", "2026.2.6")`（内部走 `toIntelliJPlatformType`）。

### 2.5 版本 2026.2.6 ⇔ build 262.10968.148 完全对应 `[V]`

`https://data.services.jetbrains.com/products/releases?code=DG&type=release&latest=false`：

| version | build | date |
|---|---|---|
| 2026.2.6.**1** | 262.11879.103 | 2026-10-30 |
| **2026.2.6** | **262.10968.148** | 2026-09-29 ← 与本机安装一致 |
| 2026.2.5 | 262.10315.132 | 2026-09-03 |
| 2026.2 | 262.8665.272 | 2026-07-16 |

> 注意已有更新的补丁版 `2026.2.6.1`。本机装的是 `2026.2.6`，所以构建脚本应锁 `2026.2.6`。

---

## 3. 内置插件依赖：`com.intellij.database`

### 3.1 真实插件 id（从已安装 jar 读出）`[V]`

```bash
cd ~/Applications/DataGrip.app
unzip -p Contents/plugins/DatabaseTools/lib/database-plugin.jar META-INF/plugin.xml | head -40
```

```xml
<idea-plugin xmlns:xi="http://www.w3.org/2001/XInclude" allow-bundled-update="true">
  <name>Database Tools and SQL</name>
  <id>com.intellij.database</id>
  <version>262.10968.148</version>
  <idea-version since-build="262.10968.148" until-build="262.10968.148" />
  <incompatible-with>com.intellij.jetbrains.client</incompatible-with>
  ...
  <dependencies>
    <module name="intellij.spellchecker" />
    <module name="intellij.json.backend" />
    <module name="intellij.platform.rpc.backend" />
    <module name="intellij.regexp" />
    <plugin id="com.intellij.modules.database-capable" />
  </dependencies>
```

- **插件 id = `com.intellij.database`**（精确，无后缀）。
- 名称 `Database Tools and SQL`，版本与 IDE build 同步（262.10968.148）。
- 它自身 `<depends>` 的是 module alias `com.intellij.modules.database-capable`，**那不是给第三方插件当依赖用的**（alias 不含任何类，只表示“该 IDE 具备数据库能力”）。

本地二次确认 `[V]`：`Contents/Resources/product-info.json` 的 `bundledPlugins`（58 项）包含
`com.intellij.database`、`com.intellij.database.ide`、`intellij.database.artifactsBundle`、`intellij.database.cloudExplorer.aws`、`intellij.database.cloudExplorer.gcloud`。

### 3.2 Gradle 侧：`bundledPlugin("com.intellij.database")` `[V]`

官方依赖扩展文档：

> **Bundled Plugin** — Use `bundledPlugin(id)` or `bundledPlugins(ids)` to add a dependency on the bundled plugin within the currently targeted IntelliJ Platform. The list of bundled plugin IDs is available via `printBundledPlugins` task.

```kotlin
dependencies {
    intellijPlatform {
        datagrip("2026.2.6")
        bundledPlugin("com.intellij.database")   // ✅ 正确
    }
}
```

不要用 `plugin("com.intellij.database:...")`（那是 Marketplace 插件的写法，会去仓库里找一个根本不在 Marketplace 的内置插件）。
测试作用域用 `testBundledPlugin("com.intellij.database")`。

### 3.3 `plugin.xml` 侧：`<depends>` 怎么写 `[V]/[I]`

```xml
<idea-plugin>
    <id>com.example.tablestructure</id>
    <name>Table Structure Viewer</name>
    <vendor>example</vendor>

    <!-- 基线：只依赖平台，保证在任何 IntelliJ 平台产品里都能被加载 -->
    <depends>com.intellij.modules.platform</depends>
    <!-- 真正的 API 来源：Database Tools and SQL -->
    <depends>com.intellij.database</depends>
</idea-plugin>
```

- **`com.intellij.database` 是必须的**，否则 `Das*` / `DbPsiFacade` 等类在运行期不可见。
- **`com.intellij.modules.platform` 建议加上**：它是“我用了平台 API、但不绑定具体 IDE”的标准声明；不加也能在 DataGrip 里跑（因为 `com.intellij.database` 本身就隐含平台），但加上更规范，也便于以后在 IDEA Ultimate / 其他含 Database 插件的 IDE 中加载。
- **不要**把 `com.intellij.modules.database-capable` 当依赖：它是 module alias，编译期拿不到任何类。`[I]`（基于该 alias 在平台中的定义方式；本项目未实测编译）
- 不需要 `<depends>` 任何 `intellij.*` module（如 `intellij.database`）——那是内置插件自己的 `content` 模块，第三方插件不应直接依赖。

---

## 4. 工具链要求

### 4.1 Gradle 版本

| 约束 | 版本 | 来源 |
|---|---|---|
| IntelliJ Platform Gradle Plugin 2.x 最低 Gradle | **9.0.0** | 官方 Requirements |
| 2.x 最低 Java Runtime（跑 Gradle 本身） | **17** | 同上 |
| 2.x 最低目标平台 | 2023.3 | 同上 |
| **Java 25 toolchain / 运行 Gradle** | **9.1.0 及以上** | Gradle 官方兼容表 |
| 当前 Gradle | **9.8.2** | docs.gradle.org（Version 9.8.2） |
| 当前 IntelliJ Platform Gradle Plugin | **2.19.0** | Gradle Plugin Portal `maven-metadata.xml`（`lastUpdated 20260914`） |

Gradle 官方 Java 兼容表（`docs.gradle.org/current/userguide/compatibility.html`，Table 1）：

```
Java version | Support for toolchains | Support for running Gradle
24           | 8.14                   | 8.14 and after
25           | 9.1.0                  | 9.1.0 and after
26           | 9.4.0                  | 9.4.0 and after
27           | 9.8.0                  | 9.8.0 and after
```

➡️ 本机默认 JDK 21 可以**运行** Gradle 9.8.2（21 在支持区间内），编译则用 25 的 toolchain。

> 插件仓库自身也在用 `gradleVersion=9.8.1`、`testKotlinVersion=2.2.20`（`gradle.properties`），可作为版本组合的旁证。

### 4.2 平台 build 262 → Java 25（插件会自动设置 toolchain）`[V]`

`utils/PlatformJavaVersions.kt`：

```kotlin
val PlatformJavaVersions = mapOf(
    Version(262) to JavaVersion.VERSION_25,
    Version(242) to JavaVersion.VERSION_21,
    Version(222) to JavaVersion.VERSION_17,
    Version(203) to JavaVersion.VERSION_11,
    Version(0)   to JavaVersion.VERSION_1_8,
)
```

`IntelliJPlatformDependenciesHelper.intellijPlatformJavaLanguageVersionProvider()` 把平台版本映射成
`JavaLanguageVersion.of(25)`；`IntelliJPlatformBasePlugin.kt` 再把它作为 **convention** 装上：

```kotlin
// Setup default JVM targets based on the IntelliJ Platform dependency.
val intellijPlatformJavaLanguageVersion = dependenciesHelper.intellijPlatformJavaLanguageVersionProvider()
val javaExtension = project.extensions.getByType<JavaPluginExtension>()
javaExtension.toolchain.languageVersion.convention(intellijPlatformJavaLanguageVersion)
```

**所以 `java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }` 其实是可选的**（写出来更直观、且能防止 convention 被意外覆盖）。

### 4.3 不要设置 `tasks.compileJava.options.release` `[V]`

同一文件里的注释（原文，很重要）：

```kotlin
// The target IntelliJ Platform Java version is applied only as the Java toolchain language-version
// convention above. We intentionally do NOT set an
// `options.release` convention here: doing so used to override any `sourceCompatibility`/
// `targetCompatibility` the user configured ... because Gradle emits `--release` instead of `-source`/`-target`
// when it is set. As a result the published `org.gradle.jvm.version` variant attribute ...
// advertised the platform version instead of the requested one.
// ... See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1772
```

➡️ **只声明 toolchain，别碰 `options.release`**。Gradle 会从 toolchain 推导 `sourceCompatibility`/`targetCompatibility`。
（若你手动设 `options.release = 25`，在单模块项目里通常也能编过，但会踩 #1772 的 variant attribute 坑，多模块时尤其糟。）

### 4.4 Kotlin 的 `jvmTarget` 也会被自动对齐 `[V]`

`plugins/KotlinJvmToolchainConventions.kt` 通过反射把 `KotlinJvmCompile.compilerOptions.jvmTarget` 的 convention 设为
`JvmTarget.fromTarget("25")`。所以用 Kotlin 时**不需要**手写 `kotlin { jvmToolchain(25) }`（写了也无害）。

### 4.5 JBR 25 作为 toolchain 实测可用 `[V]`

```bash
JBR=~/Applications/DataGrip.app/Contents/jbr/Contents/Home
$JBR/bin/javac --release 25 -d /tmp/jt /tmp/jt/T.java   # → 成功
$JBR/bin/java -cp /tmp/jt T                              # → ok
ls $JBR/lib/ct.sym                                       # → 存在（10.8 MB，--release 需要）
ls $JBR/jmods                                            # → 不存在（只影响 jlink，不影响编译）
```

**但 JBR 不在 Gradle 的自动探测路径里**（Gradle 只扫 `JAVA_HOME`、`/Library/Java/JavaVirtualMachines`、
`~/Library/Java/JavaVirtualMachines`、SDKMAN 等）。必须显式注册，见 `gradle.properties` 中的
`org.gradle.java.installations.paths`。否则会得到：

```
No matching toolchains found for requested specification: {languageVersion=25, vendor=any, implementation=vendor-specific}
```

**Java 还是 Kotlin？→ 推荐 Java**（理由见 §5.5）。

---

## 5. 四个构建文件（可直接使用）

> 目录：`/Users/wangyc/Documents/project/my/datagrip`
> 约定沿用 `docs/实现方案.md`：`group=com.example`、插件 id `com.example.tablestructure`。

### 5.1 `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "datagrip-table-structure"
```

> `pluginManagement { }` 必须是文件里的**第一个**语句块。
> 只有用 snapshot 版插件时才需要额外加 `maven("https://central.sonatype.com/repository/maven-snapshots/")`。

### 5.2 `gradle.properties`

```properties
# ---- 项目元信息 ----
pluginGroup=com.example
pluginName=Table Structure Viewer
pluginVersion=0.1.0
platformBuild=262

# ---- 目标 IDE ----
# macOS 关键点：必须指向 .app 内部的 Contents，而不是 .app 本身（原因见 §7 P2）
datagripPath=/Users/wangyc/Applications/DataGrip.app/Contents

# 把 DataGrip 自带的 JBR 25 注册为 Gradle toolchain
# （Gradle 本体仍可用系统 JDK 21 启动；编译用 25）
org.gradle.java.installations.paths=/Users/wangyc/Applications/DataGrip.app/Contents/jbr/Contents/Home

# ---- Gradle 行为 ----
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m
org.gradle.caching=true
# 首次跑通前建议先关掉配置缓存，排错更直观；稳定后再打开
# org.gradle.configuration-cache=true
```

### 5.3 `build.gradle.kts`

```kotlin
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        // 含 jetbrainsIdeInstallers() / releases() / snapshots() /
        // localPlatformArtifacts() / jetbrainsRuntime() / marketplace() / intellijDependencies()
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 方案 A（本机开发首选）：复用已安装的 DataGrip，零下载、build 完全一致。
        // 注意路径是 .app/Contents（见 §7 P2）
        local(providers.gradleProperty("datagripPath").get())

        // 方案 B（CI / 无本地安装）：走 installer，从 download.jetbrains.com 拉 dmg。
        // DataGrip 没有 Maven 坐标，useInstaller 默认就是 true，因此这一行即可：
        // datagrip("2026.2.6")

        // Database Tools and SQL（内置插件，id 实测为 com.intellij.database）
        bundledPlugin("com.intellij.database")

        // 需要写测试时再加：
        // testFramework(TestFrameworkType.Platform)
    }
}

java {
    // 平台插件已按 build 262 自动设置该 convention；显式写出来是为了可读性与防覆盖。
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// ⚠️ 不要设置 tasks.compileJava { options.release = 25 }，理由见 §4.3

intellijPlatform {
    // 会启动一次 headless IDE 生成 searchable options，很慢；开发期关掉
    buildSearchableOptions = false

    pluginConfiguration {
        id = "com.example.tablestructure"
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformBuild") // "262"
            untilBuild = provider { null }                          // 不限上界（文档支持写法）
        }
    }

    pluginVerification {
        ides {
            // 用本地 DataGrip 做验证，避免 verifier 去下载 IDE / 依赖 Marketplace 元数据
            local(file(providers.gradleProperty("datagripPath").get()))
            // 或者按产品类型选（DataGrip 在 IntelliJPlatformType 中存在）：
            // select { types = listOf(IntelliJPlatformType.DataGrip) }
        }
    }
}
```

### 5.4 `gradle/wrapper/gradle-wrapper.properties`

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip
distributionSha256Sum=21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

> SHA-256 取自 `https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256` `[V]`。

**⚠️ 只有 `.properties` 是不够的**：`gradlew`、`gradlew.bat`、`gradle/wrapper/gradle-wrapper.jar` 也必须在。
本机没有 Gradle，无法直接 `gradle wrapper`。两种做法：

```bash
# 做法 1：临时下载 Gradle，生成 wrapper（推荐）
curl -L -o /tmp/gradle-9.8.2-bin.zip https://services.gradle.org/distributions/gradle-9.8.2-bin.zip
unzip -q /tmp/gradle-9.8.2-bin.zip -d /tmp
cd /Users/wangyc/Documents/project/my/datagrip
/tmp/gradle-9.8.2/bin/gradle wrapper --gradle-version 9.8.2 --distribution-type bin

# 做法 2：用 IntelliJ IDEA / DataGrip 打开工程，让 IDE 生成 Gradle wrapper
```

### 5.5 为什么选 Java 而不是 Kotlin

1. **零版本矩阵风险**：Kotlin Gradle Plugin 与 Gradle 9.8 的兼容区间需要额外核对；Java 只要 toolchain 25 即可，Gradle 9.8.2 原生支持。
2. **不打包 `kotlin-stdlib`**：平台插件会把 runtime 依赖复制进插件 `lib/`，多一个 stdlib 就多一份类加载与冲突风险（平台本身也自带 Kotlin 运行时，版本未必一致）。
3. **DataGrip 的 `Das*` API 是纯 Java**：`DasUtil`、`DbPsiFacade`、`DasTable` 等没有任何 Kotlin 扩展/DSL 糖，Kotlin 带来的收益很小。
4. **平台插件已自动处理 Java 25**：toolchain + `sourceCompatibility/targetCompatibility` 全自动，Java 侧零配置。
5. 现有 `docs/实现方案.md` 的骨架就是 Java，保持一致。

> 若确实想用 Kotlin：`plugins { id("org.jetbrains.kotlin.jvm") version "2.2.20" }`，再加
> `kotlin { jvmToolchain(25) }`（可选，插件会自动设 `jvmTarget`），其余不变。

---

## 6. 常见任务说明

| 任务 | 作用 | 备注 |
|---|---|---|
| `./gradlew runIde` | 启动一个带本插件的沙箱 DataGrip | 首次会先 `prepareSandbox`；`local()` 模式下直接从已安装 IDE 拷贝，很快 |
| `./gradlew buildPlugin` | 产出 `build/distributions/<name>-<version>.zip` | 依赖 `prepareSandbox` + `jarSearchableOptions`；`buildSearchableOptions=false` 时跳过后者 |
| `./gradlew verifyPlugin` | 用 IntelliJ Plugin Verifier 检查二进制兼容性 | 默认 ides 走 `recommended()`（需插件已上 Marketplace）；本项目已在 DSL 里显式配 `local(...)`，见 §7 P6 |
| `./gradlew verifyPluginProjectConfiguration` | 静态检查 `plugin.xml` 与 Gradle 配置一致性 | 缺 `<id>`/`<vendor>`/`<description>` 或 `since-build` 非法会失败 |
| `./gradlew verifyPluginStructure` | 检查产物结构（不应把三方库打进主 jar） | — |
| `./gradlew printBundledPlugins` | **打印目标平台所有内置插件 id** | 用来确认 `com.intellij.database` 的确切 id，非常有用 |
| `./gradlew printBundledModules` | 打印内置模块 id | 需要 `bundledModule(...)` 时用它查 |
| `./gradlew printProductsReleases` | 打印可用的产品/版本列表 | 排查 installer 版本时用 |
| `./gradlew instrumentCode` | 字节码插桩（`@NotNull` 断言） | 内部任务，默认随编译执行；见 §7 P8 |

> 任务分组（官方文档）：`intellij platform building and running`（`buildPlugin`、`runIde`、`signPlugin`、`publishPlugin`）、
> `intellij platform verification`（`verifyPlugin`、`verifyPluginProjectConfiguration`、`verifyPluginStructure`、`verifyPluginSignature`）、
> `intellij platform info`（`printBundledPlugins`、`printBundledModules`、`printProductsReleases`）。
> `prepareSandbox`、`instrumentCode` 属于内部任务，不归组。

---

## 7. 坑（按踩到概率排序）

**P1. 把 IDEA 的写法照搬到 DataGrip。** `intellijIdea("2026.2.6")` / `intellijIdeaUltimate(...)` 有 Maven 坐标，DataGrip 没有。写 `datagrip("2026.2.6") { useInstaller = false }` 会得到
`Specified type 'DataGrip' has no artifact coordinates available.` `[V]`

**P2. `local()` 指向 `.app` 根目录 → 直接抛异常。** `[V]`（源码级确认）
`Path.productInfo()`（`models/ProductInfo.kt:291`）直接用 `ProductInfoPathResolver(this)`，**没有**先做 `resolvePlatformPath()` 归一化；该 resolver 只尝试三个位置：

1. `<path>` 本身叫 `product-info.json`
2. `<path>/product-info.json`
3. `<path>/Resources/product-info.json`

对 `DataGrip.app` 而言是 `DataGrip.app/product-info.json` 和 `DataGrip.app/Resources/...`，**都不存在**（真正的位置是
`DataGrip.app/Contents/Resources/product-info.json`）→ 失败。
✅ 正确写法：`local("/Users/wangyc/Applications/DataGrip.app/Contents")`（命中第 3 条预测）。
（`utils/utils.kt` 里的 `resolvePlatformPath()` 确实有 `.app → Contents` 的规则，但它不在这条调用链上。）

**P3. 首次构建要下 ~1.3 GB。** 走 `datagrip("2026.2.6")`（installer）时，插件从 `download.jetbrains.com` 下 `.dmg` 再解包（源码里有 `providers/DmgExtractorValueSource.kt`，说明 macOS dmg 解包是原生支持的 `[I]`）。用 `local()` 则零下载——本机开发强烈建议 `local()`。

**P4. `No matching toolchains found ... languageVersion=25`。** JBR 在 `~/Applications/...` 下，不在 Gradle 自动探测路径。必须设 `org.gradle.java.installations.paths`（§5.2）。可用 `./gradlew -q javaToolchains` 验证是否识别到 25。 `[V]`

**P5. 不要设 `options.release`。** 见 §4.3，会触发 issue #1772 的 variant attribute 问题。 `[V]`

**P6. `verifyPlugin` 默认去 Marketplace 找“推荐 IDE”。** 文档原文：*“When no IDEs are configured explicitly, `recommended()` is applied by default. Disable this fallback with the `verifyPluginDefaultRecommendedIdes` Gradle property.”* 插件尚未发布时会失败。对策：显式 `ides { local(...) }`（本报告已配），或在 `gradle.properties` 加 `verifyPluginDefaultRecommendedIdes=false`。 `[V]`

**P7. `verifyPluginProjectConfiguration` 的硬性要求。** `plugin.xml` 必须有 `<id>`、`<name>`、`<vendor>`、`<description>`，且 `since-build` 合法；`pluginConfiguration.id` 与 `plugin.xml` 的 `<id>` 不一致也会报。`[I]`（基于任务用途与文档，未实际触发）

**P8. `instrumentCode` 默认开启。** 它依赖 Ant 的 javac（`JavaCompilerAware.javaCompilerConfiguration`）。若遇到插桩相关报错，可临时 `intellijPlatform { instrumentCode = false }` 排查，但**发布版应保持开启**（`@NotNull` 断言依赖插桩）。 `[V]`（默认值来自文档示例与 2.x 行为）

**P9. `buildSearchableOptions` 很慢。** 它会启动一次 headless IDE。开发期设 `false`（本报告已设）。 `[V]`

**P10. IDEA Ultimate 不能等价替代 DataGrip。** IU 也内置 `com.intellij.database`（需 Ultimate 授权），编译大概率能过；但 DataGrip 还有
`datagrip-impl`、`com.intellij.database.ide`、`database-artifactsBundle` 等专有模块/插件，运行期行为与可用 API 不完全一致。
**做 DataGrip 插件就用 DataGrip 作为目标平台。** `[I]`

**P11. 没有 Gradle、没有 `~/.gradle`。** 必须先装/生成 wrapper（§5.4），且首次构建全量下载依赖，**无法离线**。 `[V]`

**P12. 版本漂移。** 已有 `2026.2.6.1`（262.11879.103）。锁 `2026.2.6` 才能和本机 `262.10968.148` 对齐；升 IDE 时记得同步改 `platformBuild` 与 `datagripPath`。 `[V]`

**P13. JBR 缺 `javap`/`jmods`。** 对构建无影响（`javap` 用系统 JDK 21 读签名即可；`jmods` 只影响 `jlink`）。别为此换 JDK。 `[V]`

**P14. `since-build` 写成 `262.10968.148` 还是 `262`？** 内置插件自己用的是全 build 号（`since-build="262.10968.148"`），第三方插件建议用 `262`（更宽松，允许同 major 的补丁版）。 `[I]`

---

## 8. 本地依赖缓存现状（是否可离线构建）

| 位置 | 状态 |
|---|---|
| `~/.gradle` | **不存在**（`test -d` → ABSENT；`find /Users/wangyc -maxdepth 3 -name .gradle` 无结果） |
| `~/.gradle/caches/modules-2/files-2.1` | 不存在，**没有** `com.jetbrains.intellij.datagrip` / `ideaIU` |
| `~/.gradle/wrapper/dists` | 不存在，**没有任何 Gradle 发行版缓存** |
| `org.jetbrains.intellij.platform` 插件 jar | 未缓存 |
| `gradle` 可执行文件 | 未安装（无 homebrew/sdkman/系统包） |
| `~/.m2/repository` | 2.7 GB，但只有 `org/jetbrains/intellij/deps`（576 KB，Maven 侧的辅助库）；**无** `datagrip`、`ideaIU`、`ideaIC`、intellij-platform-gradle-plugin |

**结论：无法离线构建。** 首次 `./gradlew` 必须联网下载：
Gradle 9.8.2 发行版（~130 MB）→ 插件 2.19.0 及其依赖 → 平台依赖（`local()` 模式下不下载 IDE，仅解析本地目录，但**仍会**下载插件自身的依赖与 `intellij-platform-gradle-plugin` 的运行期依赖）。

> 补充：即使用 `local()`，`defaultRepositories()` 仍会配置远程仓库；只是不会去下 IDE 本体。

---

## 9. 未验证 / 待确认（Open questions）

1. **`.app` 根路径是否 100% 失败**：结论来自源码阅读（`ProductInfoPathResolver` 的三个预测 + `Path.productInfo()` 未归一化），**未实际跑 Gradle 验证**（本机无 Gradle）。置信度高，但建议首次构建时用 `local(.../Contents)` 并在跑通后再试 `.app`。
2. **`verifyPlugin` 的 Plugin Verifier 是否支持 `DG` 产品码**：官方 “Verifying Plugin Compatibility” 页面未列出支持的产品；`intellij-plugin-verifier` 仓库路径探测返回 404，未能确认。用 `ides { local(...) }` 可绕过该问题（本报告已采用）。
3. **installer 模式（`datagrip("2026.2.6")`）的端到端可行性**：dmg 下载 + 解包逻辑（`DmgExtractorValueSource`）存在，但本次未实际执行；`2026.2.6` 在 `data.services.jetbrains.com` 上确实有 macOS 产物（`macM1`/`mac` 键存在）。
4. **Kotlin 变体**（KGP 2.2.20 + Gradle 9.8.2）未实测兼容性；本报告推荐 Java 以规避该风险。
5. **`bundledPlugin("com.intellij.database")` 是否足以覆盖全部 `Das*` API**：可能需要补 `bundledModule(...)`（DataGrip 把实现拆到 `intellij.database` / `intellij.database.impl` 等模块）。跑 `./gradlew printBundledModules` 可确认；编译报 “cannot find symbol” 时优先怀疑这点。
6. **`verifyPluginProjectConfiguration` 的具体报错清单**未逐条实测。
7. **`gradle-wrapper.jar` 的来源**：本机无法生成，需按 §5.4 的做法 1/2 之一补齐。

---

## 10. 来源清单（均为本次实际抓取/读取）

**源码（GitHub raw / jsDelivr）**

- `IntelliJPlatformType.kt` — https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/IntelliJPlatformType.kt
- `IntelliJPlatformDependenciesExtension.kt` — https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/extensions/IntelliJPlatformDependenciesExtension.kt
- `IntelliJPlatformDependenciesHelper.kt` — https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/extensions/IntelliJPlatformDependenciesHelper.kt
- `IntelliJPlatformBasePlugin.kt` — https://cdn.jsdelivr.net/gh/JetBrains/intellij-platform-gradle-plugin@main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/plugins/project/IntelliJPlatformBasePlugin.kt
- `KotlinJvmToolchainConventions.kt` — https://cdn.jsdelivr.net/gh/JetBrains/intellij-platform-gradle-plugin@main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/plugins/KotlinJvmToolchainConventions.kt
- `PlatformJavaVersions.kt` — https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/src/main/kotlin/org/jetbrains/intellij/platform/gradle/utils/PlatformJavaVersions.kt
- `models/ProductInfo.kt`, `resolvers/path/ProductInfoPathResolver.kt`, `resolvers/path/PathResolver.kt`, `utils/utils.kt`, `services/RequestedIntelliJPlatformsService.kt` — 同仓库对应路径
- 仓库 `gradle.properties`（`version=2.19.1`、`snapshotVersion=2.20.0`、`gradleVersion=9.8.1`）— https://raw.githubusercontent.com/JetBrains/intellij-platform-gradle-plugin/main/gradle.properties

**官方文档**

- Requirements / Setup — https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
- Dependencies Extension（`datagrip()`、`bundledPlugin()`、`local()`、`useInstaller` 默认值）— https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
- Extension（`pluginConfiguration`、`ideaVersion`、`untilBuild = provider { null }`、`pluginVerification.ides.local()`）— https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html
- Repositories Extension（`defaultRepositories()` 的组成）— https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
- Tasks（任务分组、`printBundledPlugins`）— https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-tasks.html
- Verifying Plugin Compatibility — https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html
- Gradle Java 兼容表 — https://docs.gradle.org/current/userguide/compatibility.html

**版本元数据**

- 插件版本列表（最新 **2.19.0**）— https://plugins.gradle.org/m2/org/jetbrains/intellij/platform/org.jetbrains.intellij.platform.gradle.plugin/maven-metadata.xml
- DataGrip 发行列表（2026.2.6 → 262.10968.148）— https://data.services.jetbrains.com/products/releases?code=DG&type=release&latest=false
- Gradle 9.8.2 SHA-256 — https://services.gradle.org/distributions/gradle-9.8.2-bin.zip.sha256

**本机物证**

- `~/Applications/DataGrip.app/Contents/Resources/product-info.json`（version/buildNumber/productCode/minRequiredJavaVersion/bundledPlugins）
- `~/Applications/DataGrip.app/Contents/plugins/DatabaseTools/lib/database-plugin.jar!/META-INF/plugin.xml`（`<id>com.intellij.database</id>`）
- `~/Applications/DataGrip.app/Contents/jbr/Contents/Home`（JBR 25.0.4，`javac --release 25` 实测通过）
