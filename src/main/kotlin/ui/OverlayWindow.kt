package org.perfribbon.ui

import org.perfribbon.metrics.GpuMetrics
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

fun showOverlay() {
    // 枠のない、常に最前面に表示する窓を作る
    val frame = JFrame("PerfRibbon")
    frame.isUndecorated = true
    frame.isAlwaysOnTop = true
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

    // 黒背景・白文字の一行表示を作る
    val label = JLabel(
        "■ゲームFPS：--｜■表示FPS：--｜■GPU：--%、--°｜■CPU：--%｜■RAM：--%"
    )
    label.foreground = Color.WHITE
    label.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)

    val panel = JPanel(BorderLayout())
    panel.background = Color.BLACK
    panel.border = EmptyBorder(3, 6, 3, 6)
    panel.add(label, BorderLayout.CENTER)
    frame.contentPane = panel

    // タスクバーを除いた画面の範囲を取得する
    val area = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .maximumWindowBounds

    fun updateDisplay(system: SystemMetrics, gpu: GpuMetrics) {
        // 取得できなかった値は「--」にする
        val gpuUsage = gpu.usagePercent?.let { "$it%" } ?: "--%"
        val gpuTemperature = gpu.temperatureC?.let { "$it°" } ?: "--°"
        val cpu = system.cpuPercent?.let { "$it%" } ?: "--%"
        val ram = system.ramPercent?.let { "$it%" } ?: "--%"

        // 決めた一行の形式で表示する
        label.text =
            "■ゲームFPS：--｜■表示FPS：--｜■GPU：$gpuUsage,$gpuTemperature｜■CPU：$cpu｜■RAM：$ram"

        // 文字幅に窓を合わせ、タスクバーのすぐ上に配置する
        frame.pack()
        frame.setLocation(area.x, area.y + area.height - frame.height)
    }

    // 窓を表示し、クリックを背後へ通す
    frame.pack()
    frame.setLocation(area.x, area.y + area.height - frame.height)
    frame.isVisible = true
    enableClickThrough(frame)

    // 通知領域に終了用のアイコンを登録する
    installTrayMenu(frame)

    // 値の取得は画面とは別のスレッドで行う
    val sampler = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "PerfRibbon-sampler").apply { isDaemon = true }
    }

    sampler.execute {
        // 起動時に利用可能なGPU取得方法を選ぶ
        val gpuProvider = detectSoleGpuProvider()

        // CPU・RAM・GPUの値を約1秒ごとに取得する
        sampler.scheduleAtFixedRate({
            val system = readSystemMetrics()
            val gpu = gpuProvider?.read() ?: GpuMetrics(null, null)

            // 取得結果をSwingの画面処理へ渡す
            SwingUtilities.invokeLater {
                updateDisplay(system, gpu)
            }
        }, 0, 1, TimeUnit.SECONDS)
    }
}