package org.perfribbon.ui

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

// 設定ファイルの保存先と、読み書きの処理をまとめる
object SettingsStore {
    // Windowsのユーザー別アプリデータフォルダーに保存する
    private val settingsPath = Path.of(
        System.getenv("LOCALAPPDATA")
            ?: Path.of(
                System.getProperty("user.home"),
                "AppData",
                "Local"
            ).toString(),
        "PerfRibbon",
        "settings.json"
    )

    // 読みやすく整形し、初期値も保存し、未知の項目は読み飛ばす
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    // ファイルがなければ初期設定を返し、存在すればJSONを読み込む
    fun load(): AppSettings {
        return try {
            if (Files.notExists(settingsPath)) {
                AppSettings()
            } else {
                val text = Files.readString(settingsPath)
                json.decodeFromString<AppSettings>(text)
            }
        } catch (exception: IOException) {
            System.err.println("Could not read settings: ${exception.message}")
            AppSettings()
        } catch (exception: SerializationException) {
            System.err.println("Invalid settings JSON: ${exception.message}")
            AppSettings()
        }
    }

    // 保存先フォルダーを作り、設定をJSONに変換して書き込む
    fun save(settings: AppSettings) {
        Files.createDirectories(settingsPath.parent)

        val text = json.encodeToString(settings)
        Files.writeString(settingsPath, text)
    }
}