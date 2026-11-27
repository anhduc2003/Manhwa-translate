import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("com.manhwatranslate.demo.MainKt")
}

dependencies {
    implementation(project(":core"))
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.register<JavaExec>("realPage") {
    group = "application"
    description = "Run the pipeline on a real page: -Pargs=\"image.jpg ocr.json outDir\""
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.manhwatranslate.demo.RealPageMainKt")
    workingDir = rootProject.projectDir
    jvmArgs = listOf("-Xmx3g")
    args = ((project.findProperty("args") as String?) ?: "")
        .split(" ")
        .filter { it.isNotBlank() }
}
