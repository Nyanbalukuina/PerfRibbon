package org.perfribbon.ui

import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import javax.swing.JFrame
import javax.swing.SwingUtilities
import java.awt.CheckboxMenuItem

// 起動時の表示位置と、位置変更時に呼ぶ処理を受け取る
fun installTrayMenu(
    frame: JFrame,
    fpsStatus: () -> String = { "FPS未対応" },
    initialPosition: OverlayPosition,
    onPositionChanged: (OverlayPosition) -> Unit
) {
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

    // 右クリックメニューを作る
    val menu = PopupMenu()

    // 操作できない見出しを追加する
    val settingsHeading = MenuItem("■ Settings").apply {
        isEnabled = false
    }
    val areaHeading = MenuItem("Menu Area").apply {
        isEnabled = false
    }
    menu.add(settingsHeading)
    menu.add(areaHeading)

    // 各メニュー項目と、それが表す表示位置を組にする
    // 表示名と、対応する表示位置を定義する
    val positionOptions = listOf(
        "Left-Top" to OverlayPosition.LEFT_TOP,
        "Left-Bottom" to OverlayPosition.LEFT_BOTTOM,
        "Right-Top" to OverlayPosition.RIGHT_TOP,
        "Right-Bottom" to OverlayPosition.RIGHT_BOTTOM
    )

    // 保存から読み込んだ位置にチェックを付ける
    val positionItems = positionOptions.map { (label, position) ->
        CheckboxMenuItem(
            label,
            position == initialPosition
        ) to position
    }

    // チェックを1つにそろえ、選んだ位置を表示側へ通知する
    positionItems.forEach { (selectedItem, position) ->
        selectedItem.addItemListener {
            positionItems.forEach { (item, _) ->
                item.state = item === selectedItem
            }

            SwingUtilities.invokeLater {
                onPositionChanged(position)
            }
        }
        menu.add(selectedItem)
    }

    // 区切り線と終了項目を追加する
    menu.addSeparator()
    val exitItem = MenuItem("Exit")
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
