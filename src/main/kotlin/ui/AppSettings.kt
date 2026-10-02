package org.perfribbon.ui

import kotlinx.serialization.Serializable

// JSONへ保存・読み込みする設定項目と初期値を定義する
@Serializable
data class AppSettings(
    val position: OverlayPosition = OverlayPosition.LEFT_TOP
)