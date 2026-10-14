package ir.amirab.downloader.ytdlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtDlpVideoInfoTest {
    private fun loadSample(): String =
        javaClass.getResourceAsStream("/ytdlp/sample-video.json")!!
            .bufferedReader().readText()

    @Test
    fun `parses dump-json sample`() {
        val info = ytdlpJson.decodeFromString<YtDlpVideoInfo>(loadSample())
        assertEquals("BaW_jenozKc", info.id)
        assertEquals("youtube-dl test video", info.title)
        assertEquals("youtube-dl test video \"'\\/äöü℗\"", info.fullTitle)
        assertEquals(10L, info.durationSeconds)
        assertEquals("Philipp Hagemeister", info.uploader)
        assertEquals(8, info.formats.size)
        assertEquals("youtube-dl test video \"'\\/äöü℗\"", info.displayTitle)
    }

    @Test
    fun `parses format fields`() {
        val info = ytdlpJson.decodeFromString<YtDlpVideoInfo>(loadSample())
        val f22 = info.formats.first { it.formatId == "22" }
        assertEquals("mp4", f22.ext)
        assertEquals(720, f22.height)
        assertEquals(1280, f22.width)
        assertTrue(f22.hasVideo)
        assertTrue(f22.hasAudio)
        assertEquals(1500000L, f22.effectiveSize) // falls back to filesize_approx
        assertEquals("720p", f22.displayResolution)

        val f140 = info.formats.first { it.formatId == "140" }
        assertFalse(f140.hasVideo)
        assertTrue(f140.hasAudio)
        assertEquals("audio only", f140.displayResolution)
        assertEquals(170000L, f140.effectiveSize)

        val f18 = info.formats.first { it.formatId == "18" }
        assertEquals("Mozilla/5.0", f18.httpHeaders["User-Agent"])
        assertEquals(500000L, f18.effectiveSize) // prefers filesize over approx
    }

    @Test
    fun `tolerates minimal json`() {
        val info = ytdlpJson.decodeFromString<YtDlpVideoInfo>("""{"id":"abc","weird_field":[1,2]}""")
        assertEquals("abc", info.id)
        assertNull(info.title)
        assertTrue(info.formats.isEmpty())
        assertEquals("abc", info.displayTitle)
    }

    @Test
    fun `parses flat playlist entry`() {
        val entry = ytdlpJson.decodeFromString<YtDlpPlaylistEntry>(
            """{"_type":"url","id":"abc123","title":"Some video","url":"abc123","webpage_url":"https://example.com/watch?v=abc123","duration":61.0}"""
        )
        assertEquals("abc123", entry.id)
        assertEquals("Some video", entry.title)
        assertTrue(entry.entries.isEmpty())
    }
}
