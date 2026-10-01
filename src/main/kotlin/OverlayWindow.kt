package org.example

import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Timer
import javax.swing.border.EmptyBorder
import java.awt.BorderLayout

fun showOverlay() {
    // 枠のない、常に最前面に表示する窓を作る
    val frame = JFrame("PerfRibbon")
    frame.isUndecorated = true
    frame.isAlwaysOnTop = true
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE

    // 表示する文字の色と大きさを設定する
    // 取得できない項目は「--」で表示する
    val label = JLabel(
        "■ゲームFPS：--｜■表示FPS：--｜■GPU：--%、--°｜■CPU：--%｜■RAM：--%"
    )
    label.foreground = Color.WHITE
    label.font = Font(Font.SANS_SERIF, Font.PLAIN, 12)

    // 黒い背景と文字の周囲の余白を設定する
    val panel = JPanel(BorderLayout())
    panel.background = Color.BLACK
    panel.border = EmptyBorder(3, 6, 3, 6)
    panel.add(label, BorderLayout.CENTER)
    frame.contentPane = panel

    // タスクバーを除いた、表示に使える範囲を取得する
    val area = GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .maximumWindowBounds

    fun updateDisplay() {
        // CPU・RAMの現在値を読み、取得できない値は「--」にする
        val metrics = readSystemMetrics()
        val cpu = metrics.cpuPercent?.let { "$it%" } ?: "--"
        val ram = metrics.ramPercent?.let { "$it%" } ?: "--"

        // FPSとGPUは未取得のまま、決めた一行の形式で表示する
        label.text = "■ゲームFPS：--｜■表示FPS：--｜■GPU：--%、--°｜■CPU：$cpu｜■RAM：$ram"

        // 文字の幅に窓を合わせ、タスクバーのすぐ上に配置する
        frame.pack()
        frame.setLocation(area.x, area.y + area.height - frame.height)
    }

    // 起動直後に値を表示し、その後は1秒ごとに更新する
    updateDisplay()
    Timer(1000) { updateDisplay() }.start()

    // 窓を表示してから、クリックを背後へ通す設定を行う
    frame.isVisible = true
    enableClickThrough(frame)

    // 通知領域に終了用のアイコンを登録する
    installTrayMenu(frame)
}