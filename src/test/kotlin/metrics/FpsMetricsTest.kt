package org.perfribbon.metrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FpsMetricsTest {
    private val header = "Application,ProcessID,SwapChainAddress,FrameType,TimeInSeconds,MsBetweenPresents,MsBetweenDisplayChange"
    private fun frame(time: Double, present: Double? = 10.0, display: Double? = 20.0,
                      pid: Int = 10, chain: String = "0xA", application: Boolean = true) =
        PresentFrame(pid, chain, time, present, display, application)

    @Test fun `actual PresentMon 2_6 output from official ETL fixture yields separate FPS`() {
        val csv = PresentMonCsv()
        val window = FpsWindow { 0 }
        window.select(10016)
        var frames = 0
        javaClass.getResourceAsStream("/presentmon/presenter.csv")!!.bufferedReader().useLines { lines ->
            lines.forEach { line -> csv.parse(line)?.let { window.add(it); frames++ } }
        }
        assertTrue(csv.hasHeader)
        assertEquals(18, frames)
        assertEquals(FpsMetrics(69, 60), window.read())
    }

    @Test fun `CSV columns are resolved by name including quoted application names`() {
        val csv = PresentMonCsv()
        csv.parse("FrameType,MsBetweenDisplayChange,Application,TimeInSeconds,SwapChainAddress,ProcessID,MsBetweenPresents")
        assertEquals(frame(1.0), csv.parse("Application,20,\"game,\"\"test\"\".exe\",1,0xA,10,10"))
    }

    @Test fun `missing display values do not become zero or game FPS`() {
        val csv = PresentMonCsv()
        csv.parse(header)
        val parsed = csv.parse("game.exe,10,0xA,Application,1,10,NA")!!
        val window = FpsWindow { 0 }
        window.select(10)
        window.add(parsed)
        assertEquals(FpsMetrics(100, null), window.read())
    }

    @Test fun `invalid values and incomplete CSV are ignored`() {
        val csv = PresentMonCsv()
        assertNull(csv.parse("game.exe,10,0xA,Application,1,10,20"))
        csv.parse(header)
        assertTrue(csv.hasHeader)
        assertNull(csv.parse("game.exe,10"))
        assertNull(csv.parse("\"unterminated"))
        assertNull(csv.parse("game.exe,10,0xA,Application,NaN,10,20"))
        assertEquals(frame(1.0, null, null), csv.parse("game.exe,10,0xA,Application,1,Infinity,-10"))
        assertEquals(frame(1.0, null, null), csv.parse("game.exe,10,0xA,Application,1,0,NaN"))
        csv.parse("Application,ProcessID,FrameTime")
        assertFalse(csv.hasHeader)
    }

    @Test fun `FPS uses average frame interval rather than average instantaneous FPS`() {
        val window = FpsWindow { 0 }
        window.select(10)
        window.add(frame(1.0, 10.0, 20.0))
        window.add(frame(1.03, 30.0, 20.0))
        assertEquals(FpsMetrics(50, 50), window.read())
    }

    @Test fun `dropped frames affect presented FPS but not displayed FPS`() {
        val window = FpsWindow { 0 }
        window.select(10)
        repeat(100) { window.add(frame(it / 100.0, 10.0, if (it % 2 == 0) 20.0 else null)) }
        assertEquals(FpsMetrics(100, 50), window.read())
    }

    @Test fun `generated frames are counted only in displayed FPS`() {
        val window = FpsWindow { 0 }
        window.select(10)
        repeat(60) {
            window.add(frame(it / 60.0, 1000.0 / 60, 1000.0 / 120))
            window.add(frame(it / 60.0, 1000.0 / 60, 1000.0 / 120, application = false))
        }
        assertEquals(FpsMetrics(60, 120), window.read())
    }

    @Test fun `duplicate application rows for a present are not counted twice`() {
        val window = FpsWindow { 0 }
        window.select(10)
        window.add(frame(1.0, 10.0))
        window.add(frame(1.0, 10.0))
        window.add(frame(1.03, 30.0))
        assertEquals(50, window.read().gameFps)
    }

    @Test fun `target switching and leaving target immediately clear old values`() {
        val window = FpsWindow { 0 }
        window.select(10)
        window.add(frame(1.0))
        window.add(frame(1.0, 1.0, pid = 20))
        assertEquals(100, window.read().gameFps)
        window.select(20)
        assertEquals(FpsMetrics(), window.read())
        window.add(frame(1.1, 20.0, pid = 20))
        assertEquals(50, window.read().gameFps)
        window.select(null)
        assertEquals(FpsMetrics(), window.read())
    }

    @Test fun `multiple swap chains are not summed`() {
        val window = FpsWindow { 0 }
        window.select(10)
        repeat(60) { window.add(frame(it / 60.0, 1000.0 / 60, 1000.0 / 60)) }
        repeat(30) { window.add(frame(it / 30.0, 1000.0 / 30, 1000.0 / 30, chain = "0xB")) }
        assertEquals(FpsMetrics(60, 60), window.read())
    }

    @Test fun `old samples expire even when no new frames arrive`() {
        var now = 0L
        val window = FpsWindow { now }
        window.select(10)
        window.add(frame(1.0))
        now = 1_000_000_000L
        assertEquals(FpsMetrics(), window.read())
    }

    @Test fun `CSV event times discard old history when records arrive in a batch`() {
        val window = FpsWindow { 0 }
        window.select(10)
        window.add(frame(1.0, 100.0))
        window.add(frame(3.0, 10.0))
        window.add(frame(1.5, 200.0))
        assertEquals(100, window.read().gameFps)
    }

    @Test fun `display FPS expires independently while presents continue`() {
        var now = 0L
        val window = FpsWindow { now }
        window.select(10)
        window.add(frame(1.0))
        now = 1_000_000_000L
        window.add(frame(2.0, display = null))
        assertEquals(FpsMetrics(100, null), window.read())
    }
}
