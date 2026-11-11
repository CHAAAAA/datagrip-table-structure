plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 默认下载固定版本 SDK；有本地安装时可通过 datagripPath 复用。
        val localPath = providers.gradleProperty("datagripPath").orNull
        if (localPath.isNullOrBlank()) {
            datagrip(providers.gradleProperty("datagripVersion"))
        } else {
            local(localPath)
        }

        bundledPlugin("com.intellij.database")
    }
}

java {
    // 不设置 options.release，避免平台 Gradle 插件的变体属性冲突（#1772）。
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.jar {
    from("LICENSE") {
        into("META-INF")
    }
}

// 现有自检使用 main 入口，独立于 JUnit 的 test 源集。
val selfCheck = sourceSets.create("selfCheck") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += compileClasspath
}

val pickSchemaCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(selfCheck.classesTaskName)
    classpath = selfCheck.runtimeClasspath
    mainClass = "com.github.chaaaaa.datagripts.editor.PickSchemaCheck"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

val alterDdlCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(selfCheck.classesTaskName)
    classpath = selfCheck.runtimeClasspath
    mainClass = "com.github.chaaaaa.datagripts.service.AlterDdlCheck"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
}

val copySelectionCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    dependsOn(selfCheck.classesTaskName)
    classpath = selfCheck.runtimeClasspath
    mainClass = "com.github.chaaaaa.datagripts.ui.CopySelectionCheck"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    // 只构造 JTable/模型验证复制文本，不需要真实显示环境
    systemProperty("java.awt.headless", "true")
}

tasks.check {
    dependsOn(pickSchemaCheck, alterDdlCheck, copySelectionCheck)
}

intellijPlatform {
    // 结构查看器没有可搜索的设置项；跳过 headless IDE 启动，显著加快构建
    buildSearchableOptions = false

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("platformBuild")
        }
        vendor {
            name = "CHAAAAA"
            url = "https://github.com/CHAAAAA/datagrip-table-structure"
        }
    }

    pluginVerification {
        ides {
            // 复用当前构建使用的 SDK，保持本地覆盖与默认下载行为一致。
            current()
        }
    }
}

// Plugin Verifier 默认把缓存写到 ~/.pluginVerifier；在受限环境（沙箱/CI，home 不可写）下可改到工程内。
tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>().configureEach {
    jvmArgs("-Dplugin.verifier.home.dir=" + project.layout.projectDirectory.dir(".pluginVerifier").asFile.absolutePath)
}
