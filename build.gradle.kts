import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "de.danielscholz"
version = "0.1-SNAPSHOT"
description = "FileIndexerAndBackup"

java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11

val kotlinVersion = "1.6.10"
val coroutinesVersion = "1.6.0"

plugins {
   java
   application
   kotlin("jvm") version "1.6.10"
   id("org.jetbrains.compose") version "1.0.1"
}

application {
   mainClass.set("de.danielscholz.fileIndexer.MainKt")
}

repositories {
   mavenLocal()
   mavenCentral()
}

dependencies {
   implementation(compose.desktop.currentOs)
   implementation("de.danielscholz:KArgParser:0.1-SNAPSHOT")
   implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
   implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:$coroutinesVersion")
   implementation("com.drewnoakes:metadata-extractor:2.16.0")
   implementation("org.apache.commons:commons-lang3:3.12.0")
   implementation("org.apache.commons:commons-compress:1.21")
   implementation("org.slf4j:slf4j-api:1.7.32")
   implementation("ch.qos.logback:logback-core:1.2.10")
   implementation("ch.qos.logback:logback-classic:1.2.10")
   implementation("org.xerial:sqlite-jdbc:3.36.0.2")
   implementation("org.tukaani:xz:1.9")
   implementation("com.google.guava:guava:31.0.1-jre") {
      exclude(group = "com.google.code.findbugs", module = "jsr305")
      exclude(group = "com.google.errorprone", module = "error_prone_annotations")
      exclude(group = "com.google.j2objc", module = "j2objc-annotations")
      exclude(group = "org.codehaus.mojo", module = "animal-sniffer-annotations")
      exclude(group = "org.checkerframework", module = "checker-qual")
      exclude(group = "com.google.guava", module = "listenablefuture")
   }

   testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
   testImplementation("org.hamcrest:hamcrest:2.2")
}

tasks.withType<JavaCompile>() {
   options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>() {
   kotlinOptions.jvmTarget = "11"
}