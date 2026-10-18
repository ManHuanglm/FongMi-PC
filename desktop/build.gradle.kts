import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.10"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10"
    id("org.jetbrains.compose") version "1.9.0"
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
        freeCompilerArgs.add("-Xjvm-default=all")
    }
}

// 挂载的安卓源码先经 Sync 过滤进 build 目录，避免与桌面端同名替换类的路径冲突
val syncAndroidSources = tasks.register<Sync>("syncAndroidSources") {
    // 安卓主源集（排除 UI/播放域与桌面端替换类）
    from("../app/src/main/java") {
        exclude(
            // 根目录：桌面端提供同名替换类
            "com/fongmi/android/tv/App.java",
            "com/fongmi/android/tv/Updater.java",
            "com/fongmi/android/tv/Startup.java",
            // bean 中依赖播放域的类（Sub 经桌面 TrackUtil 替换实现支撑；SubtitleSearchItem 依赖播放域排除）
            "com/fongmi/android/tv/bean/SubtitleSearchItem.java",
            // UI / 播放 / 系统服务域：不挂载
            "com/fongmi/android/tv/ui/**",
            "com/fongmi/android/tv/player/**",
            "com/fongmi/android/tv/service/**",
            "com/fongmi/android/tv/receiver/**",
            "com/fongmi/android/tv/dlna/**",
            "com/fongmi/android/tv/browse/**",
            "com/fongmi/android/tv/playback/**",
            "com/fongmi/android/tv/model/**",
            // api 包：M3/M4 功能与桌面替换类（LiveApi/LiveParser/EpgParser 依赖可满足随挂载）
            "com/fongmi/android/tv/api/DanmakuApi.java",
            "com/fongmi/android/tv/api/SubtitleApi.java",
            "com/fongmi/android/tv/api/loader/**",
            "com/fongmi/android/tv/api/config/WallConfig.java",
            // 依赖 NewPipe/Glide/PermissionX 的类（桌面端无对应运行时）
            "com/fongmi/android/tv/bean/SubtitleSearchPage.java",
            "com/fongmi/android/tv/impl/CustomTarget.java",
            "com/fongmi/android/tv/impl/NewPipeImpl.java",
            "com/fongmi/android/tv/impl/PermissionCallback.java",
            // db：桌面端用 Room KMP 重建 AppDatabase
            "com/fongmi/android/tv/db/AppDatabase.java",
            "com/fongmi/android/tv/db/Migrations.java",
            "com/fongmi/android/tv/db/BackupManager.java",
            // server：Nano/Handlers 牵引 media3 与 PlaybackService，桌面端替换 Server 后整体排除
            "com/fongmi/android/tv/server/**",
            // utils：Android 重耦合类不挂载（Notify/ResUtil/ImgUtil/Util/FileUtil 桌面替换）
            "com/fongmi/android/tv/utils/Notify.java",
            "com/fongmi/android/tv/utils/ResUtil.java",
            "com/fongmi/android/tv/utils/ImgUtil.java",
            "com/fongmi/android/tv/utils/FileChooser.java",
            "com/fongmi/android/tv/utils/FileUtil.java",
            "com/fongmi/android/tv/utils/PermissionUtil.java",
            "com/fongmi/android/tv/utils/Traffic.java",
            "com/fongmi/android/tv/utils/Util.java",
            "com/fongmi/android/tv/utils/WebViewUtil.java",
            "com/fongmi/android/tv/utils/OkGlideModule.java",
            "com/fongmi/android/tv/utils/KeyUtil.java",
            "com/fongmi/android/tv/utils/Clock.java",
            "com/fongmi/android/tv/utils/ColorGenerator.java",
            "com/fongmi/android/tv/utils/SliderUtil.java",
            // setting 中播放域设置类（依赖 player/effect 与 media3.ui）
            "com/fongmi/android/tv/setting/AudioSetting.java",
            "com/fongmi/android/tv/setting/DanmakuSetting.java",
            "com/fongmi/android/tv/setting/DecodeSetting.java",
            "com/fongmi/android/tv/setting/SpeedSetting.java",
            "com/fongmi/android/tv/setting/SubtitleSetting.java",
            "com/fongmi/android/tv/setting/VideoSetting.java",
        )
    }
    // mobile flavor 的 ViewType（bean/Style 引用，纯常量类）
    from("../app/src/mobile/java") {
        include("com/fongmi/android/tv/ui/base/ViewType.java")
    }
    // catvod 网络与工具层全量挂载（Android API 由桌面 shim 层支撑）
    from("../catvod/src/main/java")
    into(layout.buildDirectory.dir("androidSrc"))
}

sourceSets {
    main {
        java {
            srcDir(syncAndroidSources.map { it.destinationDir })
            srcDir("src/main/java")
        }
    }
}

dependencies {
    // Compose Desktop
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // 协程
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // 网络 / 序列化（版本与安卓端一致）
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.5.0")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("com.google.guava:guava:33.7.1-jre")
    implementation("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")
    // XML/JSON 解析（bean 类注解与 Result 解析用）
    implementation("org.simpleframework:simple-xml:2.7.1")
    implementation("org.json:json:20240303")

    // 内置服务 / 事件 / 日志（eventbus-java 为 EventBus 官方 JVM 发布变体，包名一致；logger 为 AAR 由桌面桩替代）
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.greenrobot:eventbus-java:3.3.1")
    implementation("com.squareup.okhttp3:logging-interceptor:5.5.0")

    // androidx JVM 侧可用的纯 Java 构件
    implementation("androidx.annotation:annotation:1.10.0")
    implementation("androidx.collection:collection:1.5.0")

    // DLNA 设备模型（Device bean 引用 org.jupnp 类型；核心库为纯 Java）
    implementation("org.jupnp:org.jupnp:3.0.5")

    // Room 注解（仅编译期注解；桌面端数据库为手写实现，运行时用 bundled SQLite 驱动）
    implementation("androidx.room:room-common:2.8.5")
    implementation("androidx.sqlite:sqlite-bundled-jvm:2.6.2")

    // libmpv JNA 绑定（播放核心；版本与安卓端一致）
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
}

application {
    mainClass = "com.fongmi.desktop.MainKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}
