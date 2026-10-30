import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.0.21"
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
}

// The installer version comes from CI (APP_VERSION=1.<run>.0). Local builds use 1.0.0.
val appVersion: String = System.getenv("APP_VERSION") ?: "1.0.0"

// Writes AppVersion.kt so the app knows its own version for the self-update check.
val generatedSrc = layout.buildDirectory.dir("generated/appversion/kotlin")
val generateAppVersion = tasks.register("generateAppVersion") {
    val outDir = generatedSrc
    val version = appVersion
    inputs.property("appVersion", version)
    outputs.dir(outDir)
    doLast {
        val file = outDir.get().file("com/provisionstore/desktop/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package com.provisionstore.desktop\n\nconst val APP_VERSION = \"$version\"\n")
    }
}

sourceSets["main"].kotlin.srcDir(generatedSrc)

tasks.named("compileKotlin") {
    dependsOn(generateAppVersion)
}

repositories {
    google()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "com.provisionstore.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "Provision Store"
            packageVersion = appVersion
            description = "Provision store billing, stock and khata"
            vendor = "ashrafwebdev"
            windows {
                menuGroup = "Provision Store"
                upgradeUuid = "6f1c2a7e-4b3d-4e8a-9c2f-7d5b1a8e0f31"
                shortcut = true
            }
        }
    }
}
