import java.net.URI
import java.security.MessageDigest

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
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

// 解析対象のCSV仕様を固定する。初回ビルド時だけ公式リリースを取得する。
val presentMonVersion = "2.6.0"
val presentMonSha256 = "b2a706bc6ad475749e3b7e3409263aa1e6906d45bdcf993f6dbc0f660188f1af"
val presentMonFile = layout.buildDirectory.file("presentmon/PresentMon-$presentMonVersion-x64.exe")
val preparePresentMon = tasks.register("preparePresentMon") {
    inputs.property("version", presentMonVersion)
    inputs.property("sha256", presentMonSha256)
    outputs.file(presentMonFile)
    doLast {
        val destination = presentMonFile.get().asFile
        destination.parentFile.mkdirs()
        val url = URI("https://github.com/GameTechDev/PresentMon/releases/download/v$presentMonVersion/${destination.name}")
        val connection = url.toURL().openConnection().apply {
            connectTimeout = 30_000
            readTimeout = 60_000
        }
        val bytes = connection.getInputStream().use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        check(hash == presentMonSha256) { "PresentMon SHA-256 mismatch" }
        destination.writeBytes(bytes)
    }
}

tasks.processResources {
    dependsOn(preparePresentMon)
    from(presentMonFile) { into("presentmon") }
}
