plugins {
    kotlin("jvm") version "2.4.0"

    application
}

group = "org.perfribbon"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass.set("org.perfribbon.MainKt")
}