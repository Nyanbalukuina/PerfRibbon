package org.perfribbon.metrics

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import kotlin.math.roundToInt

data class SystemMetrics(
    val cpuPercent: Int?,
    val ramPercent: Int?
)

fun readSystemMetrics(): SystemMetrics {
    // OS全体のCPU・メモリ情報を取得する
    val os = ManagementFactory.getOperatingSystemMXBean()
            as? OperatingSystemMXBean

    // CPU使用率を0～100の整数に変換する
    val cpuPercent = os?.cpuLoad
        ?.takeIf { it >= 0.0 }
        ?.let { (it * 100).roundToInt() }

    // 物理メモリの総量と空き容量から使用率を計算する
    val total = os?.totalMemorySize ?: -1L
    val free = os?.freeMemorySize ?: -1L
    val ramPercent = if (total > 0 && free >= 0) {
        ((total - free).toDouble() / total * 100).roundToInt()
    } else {
        null
    }

    // 取得できなかった項目はnullのまま表示側へ渡す
    return SystemMetrics(cpuPercent, ramPercent)
}