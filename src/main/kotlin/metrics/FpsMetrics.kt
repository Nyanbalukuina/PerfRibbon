package org.perfribbon.metrics

import kotlin.math.roundToInt

data class FpsMetrics(val gameFps: Int? = null, val displayedFps: Int? = null)

internal data class PresentFrame(
    val processId: Int,
    val swapChain: String,
    val timeSeconds: Double,
    val presentMs: Double?,
    val displayMs: Double?,
    val applicationFrame: Boolean
)

/** PresentMon 2.6.0の通常CSV。列番号を固定せず、ヘッダーで対応を確認する。 */
internal class PresentMonCsv {
    private var columns = emptyMap<String, Int>()
    var hasHeader = false
        private set

    fun parse(line: String): PresentFrame? {
        val cells = splitCsv(line.removePrefix("\uFEFF")) ?: return null
        if ("Application" in cells && "ProcessID" in cells) {
            columns = cells.withIndex().associate { it.value to it.index }
            hasHeader = listOf("ProcessID", "SwapChainAddress", "MsBetweenPresents",
                "MsBetweenDisplayChange", "FrameType").all { it in columns } &&
                ("TimeInMs" in columns || "TimeInSeconds" in columns)
            return null
        }
        if (!hasHeader) return null
        fun field(name: String) = columns[name]?.let { cells.getOrNull(it) }
        fun interval(name: String) = field(name)?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
        val pid = field("ProcessID")?.toIntOrNull()?.takeIf { it > 0 } ?: return null
        val chain = field("SwapChainAddress")?.takeIf { it.isNotBlank() } ?: return null
        val timestamp = if ("TimeInMs" in columns) field("TimeInMs")?.toDoubleOrNull()?.div(1000.0)
            else field("TimeInSeconds")?.toDoubleOrNull()
        val time = timestamp?.takeIf { it.isFinite() && it >= 0 } ?: return null
        val type = field("FrameType") ?: return null
        return PresentFrame(pid, chain, time, interval("MsBetweenPresents"), interval("MsBetweenDisplayChange"),
            type == "Application" || type == "NotSet")
    }

    private fun splitCsv(line: String): List<String>? {
        val values = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var index = 0
        while (index < line.length) {
            val char = line[index++]
            when {
                char == '"' && quoted && index < line.length && line[index] == '"' -> {
                    value.append('"'); index++
                }
                char == '"' -> quoted = !quoted
                char == ',' && !quoted -> { values.add(value.toString()); value.setLength(0) }
                else -> value.append(char)
            }
        }
        if (quoted) return null
        values.add(value.toString())
        return values
    }
}

/** 直近1秒のフレーム間隔からFPSを計算。別プロセス・別swap chainの値は混ぜない。 */
internal class FpsWindow(private val nanoTime: () -> Long = System::nanoTime) {
    private data class Sample(val frame: PresentFrame, val received: Long)
    private val chains = linkedMapOf<String, ArrayDeque<Sample>>()
    private var processId: Int? = null

    @Synchronized
    fun select(pid: Int?) {
        if (pid != processId) {
            chains.clear()
            processId = pid
        }
    }

    @Synchronized
    fun add(frame: PresentFrame) {
        if (frame.processId != processId) return
        val now = nanoTime()
        prune(now)
        if (frame.swapChain !in chains && chains.size >= 64) chains.remove(chains.keys.first())
        val samples = chains.getOrPut(frame.swapChain) { ArrayDeque() }
        // 遅れて到着した旧フレームで直近の計測期間を巻き戻さない。
        if (samples.lastOrNull()?.frame?.timeSeconds?.let { frame.timeSeconds < it } == true) return
        samples.addLast(Sample(frame, now))
        while (samples.isNotEmpty() &&
            (samples.first().frame.timeSeconds < frame.timeSeconds - 1.0 || samples.size > 4096)) {
            samples.removeFirst()
        }
    }

    @Synchronized
    fun read(): FpsMetrics {
        prune(nanoTime())
        // 最も多くPresentされたswap chainを選び、複数窓を合算しない。
        val samples = chains.values.maxByOrNull { queue ->
            queue.count { it.frame.applicationFrame && it.frame.presentMs != null }
        } ?: return FpsMetrics()
        val frames = samples.map { it.frame }
        val presents = frames.filter { it.applicationFrame }.distinctBy { it.timeSeconds }.mapNotNull { it.presentMs }
        val displays = frames.mapNotNull { it.displayMs }
        return FpsMetrics(rate(presents), rate(displays))
    }

    private fun prune(now: Long) {
        chains.values.forEach { samples ->
            while (samples.isNotEmpty() && now - samples.first().received >= 1_000_000_000L) samples.removeFirst()
        }
        chains.entries.removeIf { it.value.isEmpty() }
    }

    private fun rate(intervals: List<Double>): Int? {
        if (intervals.isEmpty()) return null
        val value = 1000.0 / intervals.average()
        return value.takeIf { it.isFinite() && it <= Int.MAX_VALUE }?.roundToInt()
    }
}
