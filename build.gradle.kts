plugins {
    id("java")
    // IntelliJ Platform Gradle Plugin 2.x（当前最新 2.19.0）
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
        // 本机开发首选：复用已安装的 DataGrip（零 IDE 下载，构建环境与运行环境完全一致）。
        // macOS 注意：必须指向 DataGrip.app/Contents，而不是 .app 根目录
        // （Gradle 插件用 ProductInfoPathResolver 查找 product-info.json，.app 根下没有该文件）。
        local(providers.gradleProperty("datagripPath").get())

        // CI / 无本地安装时改用 installer 模式（DataGrip 没有 Maven 坐标，useInstaller 默认 true）：
        // datagrip("2026.2.6")

        // Database Tools and SQL（DataGrip / IntelliJ IDEA Ultimate 内置；插件 id 实测为 com.intellij.database）
        bundledPlugin("com.intellij.database")
    }
}

java {
    // 平台 Gradle 插件会按目标平台 build 262 自动设置 Java 25 toolchain convention，
    // 这里显式写出便于阅读。
    // 注意：不要设置 tasks.compileJava.options.release（会触发 intellij-platform-gradle-plugin #1772）。
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

intellijPlatform {
    // 结构查看器没有可搜索的设置项；跳过 headless IDE 启动，显著加快构建
    buildSearchableOptions = false

    pluginConfiguration {
        name = "Table Structure Viewer"
        version = "0.1.0"
        ideaVersion {
            // 2024.3+ 平台建议不要设置 untilBuild（否则新 IDE 上无法安装插件）
            sinceBuild = "262"
        }
        vendor {
            name = "example"
            url = "https://example.com"
        }
    }

    pluginVerification {
        ides {
            // 用本地 DataGrip 做插件验证，避免默认的 recommended() 去 Marketplace 查找 IDE
            local(providers.gradleProperty("datagripPath").get())
        }
    }
}

// Plugin Verifier 默认把缓存写到 ~/.pluginVerifier；在受限环境（沙箱/CI，home 不可写）下可改到工程内。
tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask>().configureEach {
    jvmArgs("-Dplugin.verifier.home.dir=" + project.layout.projectDirectory.dir(".pluginVerifier").asFile.absolutePath)
}
