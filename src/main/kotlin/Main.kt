package org.perfribbon

import org.perfribbon.ui.showOverlay
import org.perfribbon.metrics.AmdAdlxGpuProvider
import org.perfribbon.metrics.PresentMonFpsProvider
import javax.swing.SwingUtilities


fun main(args: Array<String>) {
    val fpsProcessId = args.firstOrNull { it.startsWith("--fps-pid=") }?.substringAfter('=')?.let {
        requireNotNull(it.toIntOrNull()?.takeIf { pid -> pid > 0 }) { "--fps-pidには正のプロセスIDを指定してください" }
    }
    if ("--check-fps" in args) {
        PresentMonFpsProvider(fpsProcessId).use { provider ->
            val cleanup = Thread({ provider.close() }, "PerfRibbon-FPS-cleanup")
            Runtime.getRuntime().addShutdownHook(cleanup)
            try {
                provider.start()
                repeat(15) {
                    val fps = provider.read()
                    println("Game FPS: ${fps.gameFps ?: "--"}, Displayed FPS: ${fps.displayedFps ?: "--"}; ${provider.status}")
                    Thread.sleep(1000)
                }
            } finally {
                provider.close()
                Runtime.getRuntime().removeShutdownHook(cleanup)
            }
        }
        return
    }
    // 表示窓を出さずに、実機のAMDセンサーと終了処理を確認できる。
    if ("--check-amd" in args) {
        AmdAdlxGpuProvider().use { provider ->
            check(provider.isAvailable()) { "AMD ADLXのセンサーを利用できません" }
            println("AMD GPU: ${provider.gpuName}")
            repeat(5) { index ->
                val metrics = provider.read()
                println("GPU: ${metrics.usagePercent ?: "--"}%, ${metrics.temperatureC ?: "--"} C")
                if (index < 4) Thread.sleep(1000)
            }
        }
        return
    }
    // 画面の処理をSwing専用のスレッドで開始する
    SwingUtilities.invokeLater {
        showOverlay(fpsProcessId)
    }
}
