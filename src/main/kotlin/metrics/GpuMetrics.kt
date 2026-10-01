package org.perfribbon.metrics

data class GpuMetrics(
    val usagePercent: Int?,
    val temperatureC: Int?
)

// GPUメーカーごとの取得処理に共通する約束を定義する
interface GpuMetricsProvider : AutoCloseable {
    val vendor: String
    fun isAvailable(): Boolean
    fun read(): GpuMetrics
    override fun close() {}
}

fun detectSoleGpuProvider(): GpuMetricsProvider? {
    val providers: List<GpuMetricsProvider> = listOf(NvidiaSmiGpuProvider(), AmdAdlxGpuProvider())
    val available = providers.filter { it.isAvailable() }

    // 対応GPUが一つだけなら選び、複数なら勝手に選ばない
    val selected = available.singleOrNull()
    providers.filter { it !== selected }.forEach { it.close() }
    return selected
}
