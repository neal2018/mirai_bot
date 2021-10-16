import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompile

plugins {
    java
    kotlin("jvm") version "1.5.31"
    id("com.github.johnrengelman.shadow") version "7.1.0"
}

group = "org.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

tasks.withType(KotlinJvmCompile::class.java) {
    kotlinOptions.jvmTarget = "11"
}

dependencies {
    val miraiVersion = "2.8.0-M1"
    api("net.mamoe", "mirai-core-api", miraiVersion)     // 编译代码使用
    runtimeOnly("net.mamoe", "mirai-core", miraiVersion) // 运行时使用
    implementation("com.beust:klaxon:5.5")
    implementation("cn.hutool:hutool-all:5.7.14")
}
tasks {
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "com.example.MyLoaderKt"))
        }
    }
}
