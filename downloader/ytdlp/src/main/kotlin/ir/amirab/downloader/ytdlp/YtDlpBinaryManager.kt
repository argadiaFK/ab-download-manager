package ir.amirab.downloader.ytdlp

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Locates the yt-dlp binary and optionally downloads the official build
 * from GitHub releases (https://github.com/yt-dlp/yt-dlp).
 *
 * Resolution order:
 * 1. [YtDlpSettings.binaryPath] when set and executable
 * 2. `yt-dlp` on PATH
 * 3. `<appDataDir>/ytdlp/<binaryName>` (where auto-downloaded copies live)
 *
 * @param appDataDir directory owned by the app, e.g. `~/.abdownloadmanager` on desktop.
 * @param settingsProvider current user settings.
 * @param processRunner abstraction over [ProcessBuilder], injectable for tests.
 */
class YtDlpBinaryManager(
    private val appDataDir: File,
    private val settingsProvider: () -> YtDlpSettings,
    private val processRunner: ProcessRunner,
) {
    constructor(
        appDataDir: File,
        settingsProvider: () -> YtDlpSettings,
    ) : this(appDataDir, settingsProvider, RealProcessRunner)

    constructor(
        appDataDir: File,
    ) : this(appDataDir, { YtDlpSettings() })
    fun interface ProcessRunner {
        /** Runs the command, returns (exitCode, combinedOutput). */
        fun run(command: List<String>, timeoutSeconds: Long): Pair<Int, String>
    }

    object RealProcessRunner : ProcessRunner {
        override fun run(command: List<String>, timeoutSeconds: Long): Pair<Int, String> {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return -1 to "timed out after ${timeoutSeconds}s"
            }
            val output = process.inputStream.bufferedReader().readText()
            return process.exitValue() to output
        }
    }

    private val binaryName: String = Platform.binaryFileName()

    /** Directory where we keep an auto-downloaded binary. */
    val managedBinaryDir: File get() = File(appDataDir, "ytdlp")

    val managedBinaryFile: File get() = File(managedBinaryDir, binaryName)

    /**
     * Resolves the binary to use, or null when none is available.
     * When [downloadIfMissing] is true (or settings allow it) and no binary is found,
     * downloads the official build first.
     */
    fun resolveBinary(downloadIfMissing: Boolean = settingsProvider().autoDownloadBinary): File? {
        settingsProvider().binaryPath
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it) }
            ?.takeIf { it.isFile && it.canExecute() }
            ?.let { return it }
        findOnPath()?.let { return it }
        managedBinaryFile.takeIf { it.isFile && it.canExecute() }?.let { return it }
        if (downloadIfMissing) {
            runCatching { downloadOfficialBinary() }.getOrNull()
                ?.takeIf { it.isFile && it.canExecute() }
                ?.let { return it }
        }
        return null
    }

    /** Returns the yt-dlp version string (e.g. "2025.01.01") or null when it cannot run. */
    fun getVersion(binary: File? = null): String? {
        val bin = binary ?: resolveBinary(downloadIfMissing = false) ?: return null
        val (exit, output) = runCatching {
            processRunner.run(listOf(bin.absolutePath, "--version"), timeoutSeconds = 30)
        }.getOrNull() ?: return null
        if (exit != 0) return null
        return output.lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun findOnPath(): File? {
        val probe = if (Platform.isWindows) listOf("where", "yt-dlp") else listOf("which", "yt-dlp")
        val (exit, output) = runCatching { processRunner.run(probe, 30) }.getOrNull() ?: return null
        if (exit != 0) return null
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map(::File)
            .firstOrNull { it.isFile && it.canExecute() }
    }

    /**
     * Downloads the official yt-dlp binary for the current OS/arch from GitHub releases.
     * Throws on unsupported platforms or network errors.
     */
    fun downloadOfficialBinary(
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): File {
        val asset = Platform.releaseAssetName()
            ?: throw UnsupportedOperationException(
                "yt-dlp auto-download is not supported on ${Platform.osName}/${Platform.arch}"
            )
        val url = URI("https://github.com/yt-dlp/yt-dlp/releases/latest/download/$asset").toURL()
        val dir = managedBinaryDir.apply { mkdirs() }
        val target = File(dir, binaryName)
        val tmp = File(dir, "$binaryName.download")
        url.openStream().use { input ->
            val total = runCatching { url.openConnection().contentLengthLong.takeIf { it > 0 } }.getOrNull()
            tmp.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                var downloaded = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    output.write(buf, 0, n)
                    downloaded += n
                    onProgress(downloaded, total)
                }
            }
        }
        Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (!Platform.isWindows) {
            target.setExecutable(true)
        }
        return target
    }

    /**
     * Platform detection and official release asset mapping.
     * Kept in an object so unit tests can verify the mapping logic via [assetNameFor].
     */
    object Platform {
        val osName: String = System.getProperty("os.name", "").lowercase()
        val arch: String = System.getProperty("os.arch", "").lowercase()
        val isWindows: Boolean = osName.contains("win")
        val isMac: Boolean = osName.contains("mac")
        val isLinux: Boolean = osName.contains("linux") || osName.contains("nix") || osName.contains("nux")

        fun binaryFileName(os: String = osName): String =
            if (os.lowercase().contains("win")) "yt-dlp.exe" else "yt-dlp"

        /**
         * Official GitHub release asset for the given OS/arch, or null when unsupported.
         * Asset names follow https://github.com/yt-dlp/yt-dlp/releases.
         */
        fun assetNameFor(os: String, arch: String): String? {
            val osLower = os.lowercase()
            val archLower = arch.lowercase()
            val normalizedArch = when {
                archLower.contains("aarch64") || archLower.contains("arm64") -> "arm64"
                archLower.contains("x86_64") || archLower.contains("amd64") || archLower == "x64" -> "x64"
                else -> return null
            }
            return when {
                osLower.contains("win") -> when (normalizedArch) {
                    "x64" -> "yt-dlp.exe"
                    "arm64" -> "yt-dlp_arm64.exe"
                    else -> null
                }
                osLower.contains("mac") -> when (normalizedArch) {
                    "x64" -> "yt-dlp_macos"
                    "arm64" -> "yt-dlp_macos_arm64"
                    else -> null
                }
                osLower.contains("linux") || osLower.contains("nix") || osLower.contains("nux") -> when (normalizedArch) {
                    "x64" -> "yt-dlp"
                    "arm64" -> "yt-dlp_linux_aarch64"
                    else -> null
                }
                else -> null
            }
        }

        fun releaseAssetName(): String? = assetNameFor(osName, arch)
    }
}
