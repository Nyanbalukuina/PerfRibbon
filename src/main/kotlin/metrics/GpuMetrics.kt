package org.perfribbon.metrics

data class GpuMetrics(
    val usagePercent: Int?,
    val temperatureC: Int?
)

// GPUメーカーごとの取得処理に共通する約束を定義する
interface GpuMetricsProvider {
    val vendor: String
    fun isAvailable(): Boolean
    fun read(): GpuMetrics
}

fun detectSoleGpuProvider(): GpuMetricsProvider? {
    // 現在対応している取得方法を調べる。AMD対応時に候補を追加する
    val providers: List<GpuMetricsProvider> = listOf(NvidiaSmiGpuProvider())
    val available = providers.filter { it.isAvailable() }

    // 対応GPUが一つだけなら選び、複数なら勝手に選ばない
    return available.singleOrNull()
}