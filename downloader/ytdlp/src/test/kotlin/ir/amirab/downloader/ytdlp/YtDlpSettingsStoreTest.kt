package ir.amirab.downloader.ytdlp

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class YtDlpSettingsStoreTest {
    @Test
    fun `defaults when no file exists`() = runTest {
        val dir = createTempDir("ytdlp-settings")
        val store = YtDlpSettingsStore(dir, TestScope(UnconfinedTestDispatcher()))
        val settings = store.settings.first()
        assertNull(settings.binaryPath)
        assertTrue(settings.autoDownloadBinary)
        assertEquals("best", settings.defaultFormat)
    }

    @Test
    fun `update persists and reloads`() = runTest {
        val dir = createTempDir("ytdlp-settings")
        val scope = TestScope(UnconfinedTestDispatcher())
        val store = YtDlpSettingsStore(dir, scope)
        store.update { it.copy(binaryPath = "/usr/bin/yt-dlp", autoDownloadBinary = false) }
        // wait for the async save
        scope.testScheduler.advanceUntilIdle()
        val reloaded = YtDlpSettingsStore(dir, TestScope(UnconfinedTestDispatcher()))
        val settings = reloaded.settings.first()
        assertEquals("/usr/bin/yt-dlp", settings.binaryPath)
        assertEquals(false, settings.autoDownloadBinary)
    }

    private fun createTempDir(prefix: String): java.io.File =
        java.nio.file.Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }
}
