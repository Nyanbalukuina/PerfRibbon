package org.example

import javax.swing.SwingUtilities

fun main() {
    // 画面の処理をSwing専用のスレッドで開始する
    SwingUtilities.invokeLater {
        showOverlay()
    }
}