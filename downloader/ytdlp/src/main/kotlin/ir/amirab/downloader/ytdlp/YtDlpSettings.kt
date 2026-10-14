package ir.amirab.downloader.ytdlp

import kotlinx.serialization.Serializable

/**
 * User configuration for the yt-dlp integration.
 * Persisted by the app (see shared app settings); this module only defines the shape.
 */
@Serializable
data class YtDlpSettings(
    /** Explicit path to the yt-dlp binary. Null = auto-detect (PATH, then app data dir). */
    val binaryPath: String? = null,
    /** When true and no binary is found, offer/download the official binary from GitHub releases. */
    val autoDownloadBinary: Boolean = true,
    /**
     * Format selector passed to yt-dlp `-f`. "best" by default.
     * The UI pre-selects the best progressive format and passes its format_id instead.
     */
    val defaultFormat: String = "best",
    /** Max playlist entries fetched by --flat-playlist. */
    val maxPlaylistItems: Int = 50,
)
