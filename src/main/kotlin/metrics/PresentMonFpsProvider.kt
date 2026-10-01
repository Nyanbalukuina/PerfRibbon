package org.perfribbon.metrics

import com.sun.jna.Platform
import org.perfribbon.platform.windows.foregroundProcessId
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** PresentMonを1つだけ起動し、CSVを逐次読む。計測用ファイルは保存しない。 */
class PresentMonFpsProvider(private val fixedProcessId: Int? = null) : AutoCloseable {
    private val window = FpsWindow()
    private val session = "PerfRibbon-${UUID.randomUUID()}"
    private var process: Process? = null
    private var executable: Path? = null
    private var temporaryDirectory: Path? = null
    private var reader: Thread? = null
    private var errorReader: Thread? = null
    private var started = false
    @Volatile private var closed = false
    @Volatile private var headerSeen = false
    @Volatile private var failure: String? = null
    @Volatile private var errorText = ""
    @Volatile var targetProcessId: Int? = null
        private set

    val status: String
        get() {
            failure?.let { return it }
            if (errorText.contains("access denied", ignoreCase = true))
                return "FPS計測の権限がありません。PerfRibbonを管理者として起動してください。"
            if (closed) return "FPS計測は終了しました"
            val child = process ?: return "FPS計測を開始していません"
            if (!child.isAlive) return "PresentMonが終了しました (code=${child.exitValue()})。${errorText.takeLast(1000)}"
            if (!headerSeen) return "PresentMonの計測開始を待っています"
            return "FPS計測中: PID=${targetProcessId ?: "--"}（値がない場合は対象のフレームを待っています）"
        }

    @Synchronized
    fun start() {
        if (started || closed) return
        started = true
        if (!Platform.isWindows() || !Platform.is64Bit()) {
            failure = "FPS取得は64ビットWindowsで利用できます"
            return
        }
        try {
            val exe = unpackPresentMon()
            executable = exe
            val command = mutableListOf(exe.toString(), "--output_stdout", "--no_console_stats",
                "--no_track_input", "--no_track_gpu", "--track_frame_type", "--session_name", session,
                "--exclude", "dwm.exe", "--exclude", "explorer.exe")
            fixedProcessId?.let { command.addAll(listOf("--process_id", it.toString())) }
            val child = ProcessBuilder(command).start()
            process = child
            child.outputStream.close()
            errorReader = thread(name = "PerfRibbon-PresentMon-errors", isDaemon = true) {
                try {
                    child.errorStream.bufferedReader().useLines { lines ->
                        lines.forEach { line -> errorText = (errorText + "\n" + line).takeLast(8192) }
                    }
                } catch (_: IOException) { /* 終了時はストリームが閉じられる。 */ }
            }
            reader = thread(name = "PerfRibbon-PresentMon", isDaemon = true) {
                val csv = PresentMonCsv()
                try {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            csv.parse(line)?.let(window::add)
                            headerSeen = csv.hasHeader
                            if (line.startsWith("Application,") && !csv.hasHeader)
                                failure = "PresentMonのCSV形式に必要なFPS列がありません"
                        }
                    }
                } catch (e: IOException) {
                    if (!closed) failure = "FPS計測結果の読み取りに失敗しました: ${e.message}"
                }
            }
        } catch (e: IOException) {
            failure = "PresentMonを起動できません: ${e.message}"
        }
    }

    fun read(): FpsMetrics {
        targetProcessId = fixedProcessId ?: foregroundProcessId()
        window.select(targetProcessId)
        if (closed || failure != null || process?.isAlive != true) return FpsMetrics()
        return window.read()
    }

    private fun unpackPresentMon(): Path {
        val directory = Files.createTempDirectory("PerfRibbon-PresentMon-")
        temporaryDirectory = directory
        val file = directory.resolve("PresentMon-2.6.0-x64.exe")
        val resource = javaClass.getResourceAsStream("/presentmon/PresentMon-2.6.0-x64.exe")
            ?: throw IOException("同梱のPresentMonがありません。Gradleで再ビルドしてください")
        resource.use { Files.copy(it, file) }
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            .joinToString("") { "%02x".format(it) }
        if (hash != PRESENTMON_SHA256) throw IOException("PresentMonのSHA-256が一致しません")
        return file
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val child = process
        try {
            // プロセスの強制終了だけではETWセッションが残るため、自分の固有名だけを停止する。
            if (child != null && executable != null) {
                val stop = ProcessBuilder(executable.toString(), "--session_name", session,
                    "--terminate_existing_session", "--no_console_stats", "--no_csv")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start()
                if (!stop.waitFor(3, TimeUnit.SECONDS)) stop.destroyForcibly()
            }
        } catch (_: IOException) {
            // 既に終了した子プロセスも後処理する。
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            child?.destroy()
            try {
                if (child != null && !child.waitFor(2, TimeUnit.SECONDS)) {
                    child.destroyForcibly().waitFor(2, TimeUnit.SECONDS)
                }
                reader?.join(1000)
                errorReader?.join(1000)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            window.select(null)
            temporaryDirectory?.let { directory ->
                // 作成した1ファイルとその空ディレクトリだけを片付ける。
                try {
                    Files.deleteIfExists(directory.resolve("PresentMon-2.6.0-x64.exe"))
                    Files.deleteIfExists(directory)
                } catch (_: IOException) { /* OSがDLL/EXEを解放するまで残る場合がある。 */ }
            }
        }
    }

    private companion object {
        const val PRESENTMON_SHA256 = "b2a706bc6ad475749e3b7e3409263aa1e6906d45bdcf993f6dbc0f660188f1af"
    }
}
