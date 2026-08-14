pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://repo.essential.gg/repository/maven-releases/")
        maven("https://repo.polyfrost.cc/releases/")
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "gg.essential.loom") {
                useModule("gg.essential:architectury-loom:${requested.version}")
            }
        }
    }
}

// 自动解析/下载构建所需的 JDK 工具链（如编译与运行游戏的 Java 8），无需在机器上手动配置路径。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

// Essential Loom 1.15 要求 Gradle 守护进程运行在 Java 17+（游戏进程的 Java 8 工具链会另行自动解析）。
// 这里给出清晰报错，而不是让 Loom 抛出晦涩异常。
val daemonJavaMajor = System.getProperty("java.specification.version").toFloat().toInt()
if (daemonJavaMajor < 17) {
    throw GradleException(
        "构建需要 Gradle 守护进程运行在 Java 17+ 上（当前: ${System.getProperty("java.version")}）。" +
            "请安装 Java 17+ 并将 JAVA_HOME 指向它，或在 IDE 的 Gradle 设置里选择 17+ 的 JVM。"
    )
}

rootProject.name = "MyauClickgui"
