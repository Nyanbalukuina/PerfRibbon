package org.perfribbon.metrics

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.ptr.DoubleByReference
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.LongByReference
import com.sun.jna.ptr.PointerByReference
import java.nio.file.Path
import kotlin.math.roundToInt

/** AMDドライバーに同梱されたADLXを使用する。呼び出しと終了は同期して行う。 */
class AmdAdlxGpuProvider : GpuMetricsProvider {
    override val vendor = "AMD"
    var gpuName: String? = null
        private set
    private var library: NativeLibrary? = null
    private var initialized = false
    private var attempted = false
    private var gpu: Pointer? = null
    private var monitoring: Pointer? = null
    private var usageSupported = false
    private var temperatureSupported = false

    @Synchronized
    override fun isAvailable(): Boolean {
        initialize()
        return gpu != null && (usageSupported || temperatureSupported)
    }

    @Synchronized
    override fun read(): GpuMetrics {
        if (!isAvailable()) return GpuMetrics(null, null)
        val metrics = monitoring!!.output(GET_CURRENT_GPU_METRICS, gpu!!) ?: return GpuMetrics(null, null)
        return try {
            GpuMetrics(
                if (usageSupported) metrics.number(GPU_USAGE, 0.0..100.0) else null,
                if (temperatureSupported) metrics.number(GPU_TEMPERATURE, 0.0..200.0) else null
            )
        } finally {
            metrics.release()
        }
    }

    private fun initialize() {
        if (attempted) return
        attempted = true
        if (!Platform.isWindows() || Native.POINTER_SIZE != 8) return
        try {
            // DLL検索パスには依存せず、Windowsのドライバーライブラリを使う。
            val dll = Path.of(System.getenv("SystemRoot") ?: "C:\\Windows", "System32", "amdadlx64.dll")
            val lib = NativeLibrary.getInstance(dll.toString())
            library = lib
            val version = LongByReference()
            if (lib.getFunction("ADLXQueryFullVersion").invokeInt(arrayOf(version)) != 0) return
            val systemRef = PointerByReference()
            if (lib.getFunction("ADLXInitialize").invokeInt(arrayOf(version.value, systemRef)) != 0) return
            initialized = true
            val system = systemRef.value ?: return
            monitoring = system.output(GET_PERFORMANCE_MONITORING) ?: return
            val list = system.output(GET_GPUS) ?: return
            try {
                // 内蔵GPUと外付けGPUが共存する場合は、使用率ではなくGPU種別で選ぶ。
                var selectedType = -1
                for (index in 0 until list.call(LIST_SIZE)) {
                    val candidate = list.output(LIST_AT_GPU, index) ?: continue
                    val type = IntByReference()
                    candidate.call(GPU_TYPE, type)
                    if (gpu == null || (type.value == GPU_DISCRETE && selectedType != GPU_DISCRETE)) {
                        gpu?.release()
                        gpu = candidate
                        selectedType = type.value
                    } else candidate.release()
                }
            } finally {
                list.release()
            }
            val selected = gpu ?: return
            val name = PointerByReference()
            if (selected.call(GPU_NAME, name) == 0) gpuName = name.value?.getString(0, "UTF-8")
            val support = monitoring!!.output(GET_SUPPORTED_GPU_METRICS, selected) ?: return
            try {
                usageSupported = support.supports(SUPPORT_GPU_USAGE)
                temperatureSupported = support.supports(SUPPORT_GPU_TEMPERATURE)
            } finally {
                support.release()
            }
        } catch (e: UnsatisfiedLinkError) {
            System.err.println("AMD ADLX is unavailable: ${e.message}")
        } finally {
            if (gpu == null || (!usageSupported && !temperatureSupported)) close()
        }
    }

    @Synchronized
    override fun close() {
        gpu?.release()
        gpu = null
        monitoring?.release()
        monitoring = null
        if (initialized) library?.getFunction("ADLXTerminate")?.invokeInt(emptyArray())
        initialized = false
        library?.close()
        library = null
        attempted = true
    }

    // ADLX C ABIのvtable順序。IADLXSystemはIADLXInterfaceを継承しない。
    // https://github.com/GPUOpen-LibrariesAndSDKs/ADLX/tree/main/SDK/Include
    private companion object {
        const val GET_GPUS = 1
        const val GET_PERFORMANCE_MONITORING = 9
        const val LIST_SIZE = 3
        const val LIST_AT_GPU = 11
        const val GPU_TYPE = 5
        const val GPU_NAME = 7
        const val GPU_DISCRETE = 2
        const val GET_CURRENT_GPU_METRICS = 18
        const val GET_SUPPORTED_GPU_METRICS = 21
        const val SUPPORT_GPU_USAGE = 3
        const val SUPPORT_GPU_TEMPERATURE = 6
        const val GPU_USAGE = 4
        const val GPU_TEMPERATURE = 7

        fun Pointer.call(slot: Int, vararg args: Any): Int = Function.getFunction(
            getPointer(0).getPointer(slot.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION
        ).invokeInt(arrayOf(this, *args))

        fun Pointer.output(slot: Int, vararg args: Any): Pointer? {
            val result = PointerByReference()
            return if (call(slot, *args, result) == 0) result.value else null
        }

        fun Pointer.release() { call(1) }

        fun Pointer.supports(slot: Int): Boolean = Memory(1).use { supported ->
            supported.clear()
            call(slot, supported) == 0 && supported.getByte(0).toInt() != 0
        }

        fun Pointer.number(slot: Int, range: ClosedFloatingPointRange<Double>): Int? {
            val value = DoubleByReference()
            return if (call(slot, value) == 0 && value.value.isFinite() && value.value in range)
                value.value.roundToInt() else null
        }
    }
}
