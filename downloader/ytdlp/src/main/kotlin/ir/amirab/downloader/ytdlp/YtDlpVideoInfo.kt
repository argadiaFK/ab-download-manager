package ir.amirab.downloader.ytdlp

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Subset of `yt-dlp --dump-json` output that we care about.
 * Unknown fields are ignored and every field is nullable so that
 * extractor differences never break parsing.
 */
@Serializable
data class YtDlpVideoInfo(
    val id: String? = null,
    val title: String? = null,
    @SerialName("fulltitle") val fullTitle: String? = null,
    val duration: Double? = null,
    val uploader: String? = null,
    @SerialName("uploader_id") val uploaderId: String? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val extractor: String? = null,
    @SerialName("_type") val type: String? = null,
    val formats: List<YtDlpFormat> = emptyList(),
    @SerialName("requested_formats") val requestedFormats: List<YtDlpFormat> = emptyList(),
) {
    val displayTitle: String get() = fullTitle ?: title ?: webpageUrl ?: id ?: "Unknown"

    /** Duration in whole seconds, or null when unknown. */
    val durationSeconds: Long? get() = duration?.toLong()
}

@Serializable
data class YtDlpFormat(
    @SerialName("format_id") val formatId: String = "",
    val ext: String? = null,
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val filesize: Long? = null,
    @SerialName("filesize_approx") val filesizeApprox: Long? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    @SerialName("format_note") val formatNote: String? = null,
    val url: String? = null,
    @SerialName("http_headers") val httpHeaders: Map<String, String> = emptyMap(),
    val protocol: String? = null,
) {
    val effectiveSize: Long? get() = filesize ?: filesizeApprox
    val hasVideo: Boolean get() = vcodec != null && vcodec != "none"
    val hasAudio: Boolean get() = acodec != null && acodec != "none"

    /** e.g. "1080p", "720p60", "audio only" */
    val displayResolution: String
        get() {
            if (!hasVideo) return "audio only"
            val h = height
            if (h != null) {
                val fpsSuffix = if (fps != null && fps > 30) fps.toInt().toString() else ""
                return "${h}p$fpsSuffix"
            }
            return resolution ?: formatNote ?: ext ?: "unknown"
        }
}

/**
 * One entry of `yt-dlp --flat-playlist --dump-json` output.
 */
@Serializable
data class YtDlpPlaylistEntry(
    val id: String? = null,
    val title: String? = null,
    val url: String? = null,
    @SerialName("webpage_url") val webpageUrl: String? = null,
    val duration: Double? = null,
    @SerialName("_type") val type: String? = null,
    // flat-playlist entries may nest under "entries" when --dump-json prints a playlist object
    val entries: List<YtDlpPlaylistEntry> = emptyList(),
)

internal val ytdlpJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}
