import net.fabricmc.loom.task.RemapJarTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    idea
    java
    id("gg.essential.loom") version "1.15.50"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "2.4.10"
}

val baseGroup: String by project
val mcVersion: String by project
val modid: String by project
val mixinConfig = "mixins.myauclickgui.json"
val mixinRefmap = "mixins.myauclickgui.refmap.json"

group = baseGroup

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

kotlin {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

sourceSets.main {
    output.setResourcesDir(java.classesDirectory)
    java.srcDir(layout.projectDirectory.dir("src/main/kotlin"))
    kotlin.destinationDirectory.set(java.destinationDirectory)
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl(kotlin("stdlib-jdk8"))
    // 使用官方 SpongePowered Mixin（自带 META-INF/services 的 host service 注册）。
    // 注意：本地 libs/ 下的旧 mixin jar 缺少 ServiceLoader 注册，会导致
    // "No mixin host service is available" 启动崩溃。
    shadowImpl("org.spongepowered:mixin:0.8.5-SNAPSHOT")
    // Mixin 0.8.5 的运行时依赖（官方 SNAPSHOT 的 POM 未声明，需显式提供）。
    // ASM 必须进入运行 classpath（AppClassLoader），否则 MixinTransformer 会报
    // "NoClassDefFoundError: org/objectweb/asm/commons/ClassRemapper"。
    shadowImpl("org.ow2.asm:asm:9.2")
    shadowImpl("org.ow2.asm:asm-commons:9.2")
    shadowImpl("org.ow2.asm:asm-tree:9.2")
    shadowImpl("org.ow2.asm:asm-util:9.2")
    shadowImpl("org.ow2.asm:asm-analysis:9.2")
    // Minecraft 1.8.9 ships with Guava 17.0: Objects.firstNonNull and
    // Iterators.emptyIterator are public there, but became package-private or
    // were removed in later versions. Local compatibility classes provide the
    // MoreFiles/RecursiveDeleteOption API required by Mixin 0.8.5.
    shadowImpl("com.google.guava:guava:17.0")
    shadowImpl("com.google.code.gson:gson:2.8.9")
    // Mixin 注解处理器（Loom 的 useLegacyMixinAp 会自动注入并传参；gson/guava 是其运行时依赖）。
    // 本项目 Kotlin 源码没有注解处理器需求，因此不启用 kapt（kapt 会让 Loom 重复注入 Mixin AP，
    // 导致 "Multiple output file properties with name 'mixin-ap-main'" 构建失败）。
    annotationProcessor("com.google.code.gson:gson:2.8.9")
    annotationProcessor("com.google.guava:guava:31.1-jre")
    testImplementation(kotlin("test"))
}

loom {
    runs {
        named("client") {
            property("mixin.debug", "true")
            property("myauclickgui.testModules", "true")
            programArg("--tweakClass")
            programArg("org.spongepowered.asm.launch.MixinTweaker")
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig(mixinConfig)
    }
    mixin {
        useLegacyMixinAp.set(true)
        defaultRefmapName.set(mixinRefmap)
    }
}

// 1.8.9 的 LaunchWrapper 无法在 Java 9+ 上运行（AppClassLoader 不再是 URLClassLoader），
// 因此游戏进程必须使用 Java 8，而 Gradle 守护进程仍用 JDK21（见 gradle.properties）。
tasks.named<JavaExec>("runClient") {
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(8))
    })
}

tasks.compileJava {
    dependsOn(tasks.processResources)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("Myau Click Gui")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest.attributes(
        "FMLCorePluginContainsFMLMod" to "true",
        "ForceLoadAsMod" to "true",
        "TweakClass" to "org.spongepowered.asm.launch.MixinTweaker",
        "MixinConfigs" to mixinConfig
    )
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)

    filesMatching("mcmod.info") {
        expand(inputs.properties)
    }
}

val remapJar by tasks.named<RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    dependsOn(tasks.shadowJar)
    inputFile.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    exclude("META-INF/versions/**")
}

tasks.assemble {
    dependsOn(remapJar)
}

tasks.register<JavaExec>("testParse") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("TestParseKt")
}

tasks.register<JavaExec>("testChatManager") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("TestChatManagerKt")
}
