import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
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
