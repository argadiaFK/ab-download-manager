package ir.amirab.downloader.ytdlp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtDlpBinaryManagerTest {
    @Test
    fun `asset names per os and arch`() {
        val p = YtDlpBinaryManager.Platform
        assertEquals("yt-dlp.exe", p.assetNameFor("Windows 11", "amd64"))
        assertEquals("yt-dlp_arm64.exe", p.assetNameFor("windows 10", "aarch64"))
        assertEquals("yt-dlp", p.assetNameFor("Linux", "x86_64"))
        assertEquals("yt-dlp_linux_aarch64", p.assetNameFor("linux", "aarch64"))
        assertEquals("yt-dlp_macos", p.assetNameFor("Mac OS X", "x86_64"))
        assertEquals("yt-dlp_macos_arm64", p.assetNameFor("mac os x", "arm64"))
        assertNull(p.assetNameFor("Linux", "mips"))
        assertNull(p.assetNameFor("FreeBSD", "x86_64"))
    }

    @Test
    fun `binary file name per os`() {
        val p = YtDlpBinaryManager.Platform
        assertEquals("yt-dlp.exe", p.binaryFileName("Windows 11"))
        assertEquals("yt-dlp", p.binaryFileName("Linux"))
        assertEquals("yt-dlp", p.binaryFileName("Mac OS X"))
    }

    @Test
    fun `settings binary path wins over PATH`() {
        val fake = createTempFile("yt-dlp")
        fake.setExecutable(true)
        val manager = YtDlpBinaryManager(
            appDataDir = createTempDir("appdata"),
            settingsProvider = { YtDlpSettings(binaryPath = fake.absolutePath) },
            processRunner = YtDlpBinaryManager.ProcessRunner { _, _ -> 1 to "" },
        )
        assertEquals(fake.absolutePath, manager.resolveBinary(downloadIfMissing = false)?.absolutePath)
    }

    @Test
    fun `returns null when nothing found and download disabled`() {
        val manager = YtDlpBinaryManager(
            appDataDir = createTempDir("appdata-empty"),
            settingsProvider = { YtDlpSettings(autoDownloadBinary = false) },
            processRunner = YtDlpBinaryManager.ProcessRunner { _, _ -> 1 to "" }, // which/where fails
        )
        assertNull(manager.resolveBinary(downloadIfMissing = false))
    }

    @Test
    fun `getVersion parses first line`() {
        val manager = YtDlpBinaryManager(
            appDataDir = createTempDir("appdata-ver"),
            settingsProvider = { YtDlpSettings() },
            processRunner = YtDlpBinaryManager.ProcessRunner { cmd, _ ->
                if (cmd.last() == "--version") 0 to "2025.01.01\n" else 1 to ""
            },
        )
        val fake = createTempFile("yt-dlp")
        fake.setExecutable(true)
        assertEquals("2025.01.01", manager.getVersion(fake))
    }

    @Test
    fun `getVersion returns null on failure`() {
        val manager = YtDlpBinaryManager(
            appDataDir = createTempDir("appdata-verfail"),
            settingsProvider = { YtDlpSettings() },
            processRunner = YtDlpBinaryManager.ProcessRunner { _, _ -> 1 to "error" },
        )
        val fake = createTempFile("yt-dlp")
        fake.setExecutable(true)
        assertNull(manager.getVersion(fake))
    }

    private fun createTempDir(prefix: String): java.io.File =
        java.nio.file.Files.createTempDirectory(prefix).toFile().also { it.deleteOnExit() }

    private fun createTempFile(prefix: String): java.io.File =
        java.io.File.createTempFile(prefix, null).also { it.deleteOnExit() }
}
