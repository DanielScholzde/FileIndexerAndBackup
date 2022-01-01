
group = "de.danielscholz"
version = "0.1-SNAPSHOT"
description = "FileIndexerAndBackup"

java.sourceCompatibility = JavaVersion.VERSION_11

plugins {
    java
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("de.danielscholz:KArgParser:0.1-SNAPSHOT")
    implementation("com.drewnoakes:metadata-extractor:2.16.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("ch.qos.logback:logback-core:1.2.10")
    implementation("ch.qos.logback:logback-classic:1.2.10")
    implementation("org.xerial:sqlite-jdbc:3.36.0.2")
    implementation("org.tukaani:xz:1.9")
    implementation("com.google.guava:guava:31.0.1-jre")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.6.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.10")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:1.6.10")
    testImplementation("org.hamcrest:hamcrest:2.2")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}
