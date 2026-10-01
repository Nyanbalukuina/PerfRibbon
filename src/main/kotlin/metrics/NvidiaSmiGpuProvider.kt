package org.perfribbon.metrics

import java.io.IOException
import java.util.concurrent.TimeUnit

class NvidiaSmiGpuProvider : GpuMetricsProvider {
    override val vendor = "NVIDIA"

    override fun isAvailable(): Boolean {
        // 使用率か温度を取得できれば、NVIDIAの取得方法を利用可能と判断する
        val sample = read()
        return sample.usagePercent != null || sample.temperatureC != null
    }

    override fun read(): GpuMetrics {
        // NVIDIAのツールからGPU使用率と温度を取得する
        val process = try {
            ProcessBuilder(
                "nvidia-smi",
                "--id=0",
                "--query-gpu=utilization.gpu,temperature.gpu",
                "--format=csv,noheader,nounits"
            )
                .redirectErrorStream(true)
                .start()
        } catch (_: IOException) {
            return GpuMetrics(null, null)
        }

        return try {
            // 応答が3秒以内に返らなければ処理を止める
            if (!process.waitFor(3, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly()
                GpuMetrics(null, null)
            } else {
                // CSVの一行を使用率と温度に分ける
                val line = process.inputStream.bufferedReader().use { it.readLine() }
                val values = line?.split(',') ?: emptyList()
                GpuMetrics(
                    values.getOrNull(0)?.trim()?.toIntOrNull(),
                    values.getOrNull(1)?.trim()?.toIntOrNull()
                )
            }
        } catch (_: IOException) {
            // 出力を読めなかった場合は値なしにする
            process.destroyForcibly()
            GpuMetrics(null, null)
        } catch (_: InterruptedException) {
            // 処理が中断された場合は値なしにする
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            GpuMetrics(null, null)
        }
    }
}