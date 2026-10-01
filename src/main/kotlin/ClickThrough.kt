package org.example

import com.sun.jna.Native
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.Window

fun enableClickThrough(window: Window) {
    // 表示中のJavaの窓に対応する、Windows側の窓を取得する
    val hwnd = HWND(Native.getWindowPointer(window))
    val user32 = User32.INSTANCE

    // 現在の設定を残したまま、クリックを通す設定を追加する
    val currentStyle = user32.GetWindowLong(hwnd, WinUser.GWL_EXSTYLE)
    val clickThroughStyle =
        currentStyle or WinUser.WS_EX_LAYERED or WinUser.WS_EX_TRANSPARENT
    user32.SetWindowLong(hwnd, WinUser.GWL_EXSTYLE, clickThroughStyle)

    // 黒背景と白文字を不透明のまま表示する
    check(user32.SetLayeredWindowAttributes(
        hwnd, 0, 255.toByte(), WinUser.LWA_ALPHA
    )) {
        "クリックを通す設定に失敗しました"
    }
}       