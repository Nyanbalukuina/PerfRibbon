package org.perfribbon.ui

import org.perfribbon.metrics.GpuMetrics
import org.perfribbon.metrics.FpsMetrics
import org.perfribbon.metrics.PresentMonFpsProvider
import org.perfribbon.metrics.SystemMetrics
import org.perfribbon.metrics.detectSoleGpuProvider
import org.perfribbon.platform.windows.enableClickThrough
import org.perfribbon.metrics.readSystemMetrics
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder
import java.io.IOException
import javax.swing.JOptionPane

fun showOverlay(fpsProcessId: Int? = null) {
    // 枠のない、常に最前面に表示する窓を作る
    val frame = JFrame("PerfRibbon")
    frame.isUndecorated = true
    frame.isAlwaysOnTop = true
    frame.focusableWindowState = false
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

    // 黒背景・白文字の一行表示を作る
    val label = JLabel(
        "■ゲームFPS：--｜■表示FPS：--｜■GPU：--%、--℃｜■CPU：--%｜■RAM：--%"
    )
    label.foreground = Color.WHITE
    label.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)

    val panel = JPanel(BorderLayout())
    panel.background = Color.BLACK
    panel.border = EmptyBorder(3, 6, 3, 6)
    panel.add(label, BorderLayout.CENTER)
    frame.contentPane = panel

    // タスクバーを含むメイン画面全体の範囲を取得する
    val area = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .defaultScreenDevice.defaultConfiguration.bounds

    // 現在選ばれている表示位置を保持する
    // 保存した設定を読み込み、変更後の設定もここで保持する
    var settings = SettingsStore.load()

    // 読み込んだ表示位置を、配置処理で使う
    var currentPosition = settings.position

    // 選択中の位置と現在の窓サイズから、配置座標を計算する
    fun applyPosition() {
        val left = area.x
        val right = area.x + area.width - frame.width
        val top = area.y
        val bottom = area.y + area.height - frame.height

        val (x, y) = when (currentPosition) {
            OverlayPosition.LEFT_TOP -> left to top
            OverlayPosition.LEFT_BOTTOM -> left to bottom
            OverlayPosition.RIGHT_TOP -> right to top
            OverlayPosition.RIGHT_BOTTOM -> right to bottom
        }

        frame.setLocation(x, y)
    }

    fun updateDisplay(system: SystemMetrics, gpu: GpuMetrics, fps: FpsMetrics) {
        // 取得できなかった値は「--」にする
        val gpuUsage = gpu.usagePercent?.let { "$it%" } ?: "--%"
        val gpuTemperature = gpu.temperatureC?.let { "$it℃" } ?: "--℃"
        val cpu = system.cpuPercent?.let { "$it%" } ?: "--%"
        val ram = system.ramPercent?.let { "$it%" } ?: "--%"

        // 決めた一行の形式で表示する
        label.text =
            "■ゲームFPS：${fps.gameFps ?: "--"}｜■表示FPS：${fps.displayedFps ?: "--"}｜■GPU：$gpuUsage,$gpuTemperature｜■CPU：$cpu｜■RAM：$ram"

        // 文字幅に窓を合わせ、メイン画面の左上に配置する
        frame.pack()
        // 窓のサイズに合わせて、選択中の位置へ配置する
        applyPosition()
    }

    // 窓を表示し、クリックを背後へ通す
    frame.pack()
    // 窓のサイズに合わせて、選択中の位置へ配置する
    applyPosition()
    frame.isVisible = true
    enableClickThrough(frame)

    // 通知領域に終了用のアイコンを登録する
    val fpsProvider = PresentMonFpsProvider(fpsProcessId)
    // メニューで選んだ位置を保持し、すぐにリボンを移動する
    // 起動時の位置をメニューに渡し、位置変更時に表示と保存を更新する
    installTrayMenu(
        frame = frame,
        fpsStatus = { fpsProvider.status },
        initialPosition = currentPosition,
        onPositionChanged = { position ->
            // 選択した位置を反映し、リボンを移動する
            currentPosition = position
            applyPosition()

            // 表示位置だけを変更した、新しい設定を作る
            settings = settings.copy(position = position)

            // 設定を保存し、失敗した場合は英語のメッセージで知らせる
            try {
                SettingsStore.save(settings)
            } catch (exception: IOException) {
                JOptionPane.showMessageDialog(
                    frame,
                    "Could not save settings.",
                    "PerfRibbon",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    )

    // 値の取得は画面とは別のスレッドで行う
    val sampler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PerfRibbon-sampler").apply { isDaemon = true }
    }

    sampler.execute {
        // 起動時に利用可能なGPU取得方法を選ぶ
        val gpuProvider = detectSoleGpuProvider()
        Runtime.getRuntime().addShutdownHook(Thread({
            sampler.shutdownNow()
            try { fpsProvider.close() } finally { gpuProvider?.close() }
        }, "PerfRibbon-cleanup"))
        fpsProvider.start()

        // CPU・RAM・GPUの値を約1秒ごとに取得する
        sampler.scheduleAtFixedRate({
            val fps = fpsProvider.read()
            val system = readSystemMetrics()
            val gpu = gpuProvider?.read() ?: GpuMetrics(null, null)

            // 取得結果をSwingの画面処理へ渡す
            SwingUtilities.invokeLater {
                updateDisplay(system, gpu, fps)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }
}
