package ir.amirab.downloader.ytdlp

/**
 * Picks a sensible default `-f` selector for a video.
 *
 * Strategy: prefer the best *progressive* format (video+audio in one file) so that
 * no ffmpeg merge step is required. Falls back to "best" when nothing progressive exists.
 */
object YtDlpFormatSelector {
    /**
     * Returns the format_id of the best progressive format, or "best" as a fallback.
     * [maxHeight] optionally caps the resolution (e.g. 1080).
     */
    fun defaultFormat(info: YtDlpVideoInfo, maxHeight: Int? = null): String {
        val candidates = info.formats
            .filter { it.formatId.isNotBlank() && it.hasVideo && it.hasAudio }
            .filter { maxHeight == null || (it.height ?: Int.MAX_VALUE) <= maxHeight }
        val best = candidates.maxWithOrNull(
            compareBy(
                { it.height ?: 0 },
                { it.fps ?: 0.0 },
                { it.effectiveSize ?: 0L },
            )
        )
        return best?.formatId ?: "best"
    }

    /** All formats sorted for display: progressive first, then by height desc. */
    fun sortedForDisplay(info: YtDlpVideoInfo): List<YtDlpFormat> =
        info.formats
            .filter { it.formatId.isNotBlank() }
            .sortedWith(
                compareByDescending<YtDlpFormat> { it.hasVideo && it.hasAudio }
                    .thenByDescending { it.height ?: 0 }
                    .thenByDescending { it.effectiveSize ?: 0L }
            )
}
