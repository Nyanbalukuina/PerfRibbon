package org.example

import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.SwingUtilities

fun installTrayMenu(frame: JFrame) {
    // Windowsの通知領域が利用できるか確認する
    check(SystemTray.isSupported()) {
        "この環境では通知領域を利用できません"
    }
    val tray = SystemTray.getSystemTray()

    // 通知領域に表示する仮の「P」アイコンを作る
    val size = tray.trayIconSize
    val image = BufferedImage(
        size.width, size.height, BufferedImage.TYPE_INT_ARGB
    )
    val graphics = image.createGraphics()
    graphics.color = Color.BLACK
    graphics.fillRect(0, 0, size.width, size.height)
    graphics.color = Color.WHITE
    graphics.font = Font(
        Font.SANS_SERIF,
        Font.BOLD,
        (size.height * 0.7).toInt()
    )
    val fontMetrics = graphics.fontMetrics
    graphics.drawString(
        "P",
        (size.width - fontMetrics.stringWidth("P")) / 2,
        (size.height - fontMetrics.height) / 2 + fontMetrics.ascent
    )
    graphics.dispose()

    // 右クリックメニューに「終了」を追加する
    val menu = PopupMenu()
    val exitItem = MenuItem("終了")
    menu.add(exitItem)
    val trayIcon = TrayIcon(image, "PerfRibbon", menu)
    trayIcon.isImageAutoSize = true

    // 「終了」を選んだらアイコンと表示窓を片付けて終了する
    exitItem.addActionListener {
        SwingUtilities.invokeLater {
            tray.remove(trayIcon)
            frame.dispose()
            System.exit(0)
        }
    }

    // 作成したアイコンを通知領域へ登録する
    tray.add(trayIcon)
}