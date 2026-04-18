// 1. Padronização: Nomes de variáveis em camelCase (sem underscores)
val ktorVersion = "2.3.12"
val kotlinVersion = "1.9.24"

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.serialization") version "2.0.0"
    id("io.ktor.plugin") version "2.3.12"
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "com.creatorhub"
version = "0.0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-netty-jvm:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktorVersion")

    implementation("org.slf4j:slf4j-simple:2.0.13")

    testImplementation("io.ktor:ktor-server-tests-jvm:$ktorVersion")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.creatorcontenthub.ApplicationKt")
}


