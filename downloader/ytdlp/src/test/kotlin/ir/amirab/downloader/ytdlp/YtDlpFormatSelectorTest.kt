package ir.amirab.downloader.ytdlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class YtDlpFormatSelectorTest {
    private fun loadSample(): YtDlpVideoInfo =
        ytdlpJson.decodeFromString(
            javaClass.getResourceAsStream("/ytdlp/sample-video.json")!!
                .bufferedReader().readText()
        )

    @Test
    fun `default format picks best progressive`() {
        // sample: progressive 18 (360p) and 22 (720p); 137 is video-only
        assertEquals("22", YtDlpFormatSelector.defaultFormat(loadSample()))
    }

    @Test
    fun `default format respects max height`() {
        assertEquals("18", YtDlpFormatSelector.defaultFormat(loadSample(), maxHeight = 360))
    }

    @Test
    fun `default format falls back to best when nothing progressive`() {
        val info = YtDlpVideoInfo(
            id = "x",
            formats = listOf(
                YtDlpFormat(formatId = "137", vcodec = "avc1", acodec = "none", height = 1080),
                YtDlpFormat(formatId = "140", vcodec = "none", acodec = "mp4a", height = null),
            )
        )
        assertEquals("best", YtDlpFormatSelector.defaultFormat(info))
    }

    @Test
    fun `sortedForDisplay puts progressive first then by height`() {
        val sorted = YtDlpFormatSelector.sortedForDisplay(loadSample()).map { it.formatId }
        // progressive: 22 (720p), 18 (360p) first; then video-only by height: 137, 133, 160; then audio; then storyboard
        assertTrue(sorted.indexOf("22") < sorted.indexOf("18"))
        assertTrue(sorted.indexOf("18") < sorted.indexOf("137"))
        assertTrue(sorted.indexOf("137") < sorted.indexOf("133"))
        assertEquals(8, sorted.size)
    }
}
