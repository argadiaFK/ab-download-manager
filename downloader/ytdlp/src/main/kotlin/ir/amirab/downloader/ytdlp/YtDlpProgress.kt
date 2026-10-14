package ir.amirab.downloader.ytdlp

/**
 * Progress of a running yt-dlp download, parsed from its `--newline --progress` output.
 */
data class YtDlpProgress(
    /** 0..100, or null when yt-dlp did not report a percentage (e.g. unknown size). */
    val percent: Double?,
    val downloadedBytes: Long?,
    /** Total bytes; null when unknown. */
    val totalBytes: Long?,
    /** True when the total came from a "~" approximate figure. */
    val totalBytesIsApproximate: Boolean = false,
    /** Bytes per second, null when not reported yet. */
    val speedBytesPerSecond: Double?,
    /** ETA in seconds, null when not reported. */
    val etaSeconds: Long?,
    /** Raw destination file line reported by yt-dlp, if seen. */
    val destination: String? = null,
    val state: State = State.DOWNLOADING,
) {
    enum class State {
        DOWNLOADING,
        MERGING,
        POST_PROCESSING,
        DONE,
    }
}
