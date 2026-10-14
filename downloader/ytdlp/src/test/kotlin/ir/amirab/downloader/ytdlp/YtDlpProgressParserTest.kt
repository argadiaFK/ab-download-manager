package ir.amirab.downloader.ytdlp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtDlpProgressParserTest {
    @Test
    fun `parses standard progress line`() {
        val p = YtDlpProgressParser.parse("[download]  12.3% of ~50.00MiB at  1.20MiB/s ETA 00:36")!!
        assertEquals(12.3, p.percent!!, 0.0001)
        assertTrue(p.totalBytesIsApproximate)
        assertEquals((50.0 * 1024 * 1024).toLong(), p.totalBytes)
        assertEquals((1.2 * 1024 * 1024), p.speedBytesPerSecond!!, 1.0)
        assertEquals(36L, p.etaSeconds)
        assertEquals(((50.0 * 1024 * 1024) * 0.123).toLong(), p.downloadedBytes)
        assertEquals(YtDlpProgress.State.DOWNLOADING, p.state)
    }

    @Test
    fun `parses progress line with hours in eta`() {
        val p = YtDlpProgressParser.parse("[download]   3.1% of 2.40GiB at 800.00KiB/s ETA 01:12:05")!!
        assertEquals(3.1, p.percent!!, 0.0001)
        assertEquals((2.4 * 1024 * 1024 * 1024).toLong(), p.totalBytes)
        assertEquals((800.0 * 1024), p.speedBytesPerSecond!!, 1.0)
        assertEquals(1 * 3600 + 12 * 60 + 5L, p.etaSeconds)
    }

    @Test
    fun `parses exact size without tilde`() {
        val p = YtDlpProgressParser.parse("[download]  55.0% of 10.00MB at 2.00MB/s ETA 00:02")!!
        assertEquals(false, p.totalBytesIsApproximate)
        assertEquals((10.0 * 1000 * 1000).toLong(), p.totalBytes)
    }

    @Test
    fun `parses 100 percent done line`() {
        val p = YtDlpProgressParser.parse("[download] 100% of 50.00MiB in 00:41")!!
        assertEquals(100.0, p.percent!!, 0.0001)
        assertEquals((50.0 * 1024 * 1024).toLong(), p.totalBytes)
        assertEquals(YtDlpProgress.State.DONE, p.state)
        assertEquals(0L, p.etaSeconds)
    }

    @Test
    fun `parses destination line and carries it over`() {
        val d = YtDlpProgressParser.parse("[download] Destination: /tmp/my video.mp4")!!
        assertEquals("/tmp/my video.mp4", d.destination)
        val p = YtDlpProgressParser.parse("[download]   1.0% of 10.00MiB at 1.00MiB/s ETA 00:09", d)!!
        assertEquals("/tmp/my video.mp4", p.destination)
    }

    @Test
    fun `parses already downloaded line`() {
        val p = YtDlpProgressParser.parse("[download] my video.mp4 has already been downloaded")!!
        assertEquals(YtDlpProgress.State.DONE, p.state)
        assertEquals(100.0, p.percent!!, 0.0001)
    }

    @Test
    fun `parses merger line as merging state`() {
        val p = YtDlpProgressParser.parse("""[Merger] Merging formats into "out.mp4"""")!!
        assertEquals(YtDlpProgress.State.MERGING, p.state)
    }

    @Test
    fun `ignores unrelated lines`() {
        assertNull(YtDlpProgressParser.parse("[youtube] Extracting URL: https://..."))
        assertNull(YtDlpProgressParser.parse("[info] Video title: hello"))
        assertNull(YtDlpProgressParser.parse(""))
    }

    @Test
    fun `parseSize handles units`() {
        assertEquals(1.0, YtDlpProgressParser.parseSize("1", "B"))
        assertEquals(1024.0, YtDlpProgressParser.parseSize("1", "KiB"))
        assertEquals(1000.0, YtDlpProgressParser.parseSize("1", "KB"))
        assertEquals(2.5 * 1024 * 1024, YtDlpProgressParser.parseSize("2.5", "MiB"))
        assertTrue(abs(YtDlpProgressParser.parseSize("1.5", "GiB")!! - 1.5 * 1024 * 1024 * 1024) < 1.0)
        assertNull(YtDlpProgressParser.parseSize("abc", "MiB"))
        assertNull(YtDlpProgressParser.parseSize("1", "XB"))
    }
}
