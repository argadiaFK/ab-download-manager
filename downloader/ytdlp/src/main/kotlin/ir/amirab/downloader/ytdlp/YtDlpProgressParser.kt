package ir.amirab.downloader.ytdlp

/**
 * Parses yt-dlp `--newline` progress lines, e.g.:
 * ```
 * [download]  12.3% of ~50.00MiB at  1.20MiB/s ETA 00:36
 * [download] 100% of 50.00MiB in 00:41
 * [download] Destination: video.mp4
 * [Merger] Merging formats into "video.mp4"
 * ```
 */
object YtDlpProgressParser {
    private val progressRegex =
        Regex("""\[download\]\s+(\d+(?:\.\d+)?)%\s+of\s+(~?)([\d.]+)([KMGT]?i?B)\s+at\s+([\d.]+)([KMGT]?i?B)/s\s+ETA\s+(\d+:)?(\d+):(\d+)""")
    private val doneRegex =
        Regex("""\[download\]\s+100%\s+of\s+(~?)([\d.]+)([KMGT]?i?B)\s+in\s+.*""")
    private val destinationRegex =
        Regex("""\[download\]\s+Destination:\s+(.+)""")
    private val alreadyDownloadedRegex =
        Regex("""\[download\]\s+(.+)\s+has already been downloaded""")

    /**
     * Parses a single line. Returns null when the line carries no progress information.
     * [last] may be passed to carry the destination across lines.
     */
    fun parse(line: String, last: YtDlpProgress? = null): YtDlpProgress? {
        val destination = last?.destination
        destinationRegex.find(line)?.let {
            return YtDlpProgress(
                percent = last?.percent,
                downloadedBytes = last?.downloadedBytes,
                totalBytes = last?.totalBytes,
                speedBytesPerSecond = null,
                etaSeconds = null,
                destination = it.groupValues[1].trim(),
                state = YtDlpProgress.State.DOWNLOADING,
            )
        }
        alreadyDownloadedRegex.find(line)?.let {
            return YtDlpProgress(
                percent = 100.0,
                downloadedBytes = last?.downloadedBytes,
                totalBytes = last?.totalBytes,
                speedBytesPerSecond = null,
                etaSeconds = 0,
                destination = destination ?: it.groupValues[1].trim(),
                state = YtDlpProgress.State.DONE,
            )
        }
        progressRegex.find(line)?.let { m ->
            val percent = m.groupValues[1].toDoubleOrNull()
            val approx = m.groupValues[2] == "~"
            val total = parseSize(m.groupValues[3], m.groupValues[4])
            val speed = parseSize(m.groupValues[5], m.groupValues[6])
            val eta = parseEta(m.groupValues[7], m.groupValues[8], m.groupValues[9])
            val downloaded = if (percent != null && total != null) (total * percent / 100).toLong() else null
            return YtDlpProgress(
                percent = percent,
                downloadedBytes = downloaded,
                totalBytes = total?.toLong(),
                totalBytesIsApproximate = approx,
                speedBytesPerSecond = speed,
                etaSeconds = eta,
                destination = destination,
                state = YtDlpProgress.State.DOWNLOADING,
            )
        }
        doneRegex.find(line)?.let { m ->
            val approx = m.groupValues[1] == "~"
            val total = parseSize(m.groupValues[2], m.groupValues[3])
            return YtDlpProgress(
                percent = 100.0,
                downloadedBytes = total?.toLong(),
                totalBytes = total?.toLong(),
                totalBytesIsApproximate = approx,
                speedBytesPerSecond = null,
                etaSeconds = 0,
                destination = destination,
                state = YtDlpProgress.State.DONE,
            )
        }
        if (line.startsWith("[Merger]")) {
            return (last ?: emptyProgress()).copy(state = YtDlpProgress.State.MERGING)
        }
        if (line.startsWith("[ExtractAudio]") || line.startsWith("[EmbedSubtitle]") ||
            line.startsWith("[Postprocess]")
        ) {
            return (last ?: emptyProgress()).copy(state = YtDlpProgress.State.POST_PROCESSING)
        }
        return null
    }

    private fun emptyProgress() = YtDlpProgress(
        percent = null,
        downloadedBytes = null,
        totalBytes = null,
        speedBytesPerSecond = null,
        etaSeconds = null,
    )

    /** Parses values like "50.00MiB", "1.20MiB", "800.00KiB", "123B". Returns bytes. */
    fun parseSize(value: String, unit: String): Double? {
        val number = value.toDoubleOrNull() ?: return null
        val multiplier = when (unit.uppercase()) {
            "B" -> 1.0
            "KB" -> 1000.0
            "KIB" -> 1024.0
            "MB" -> 1000.0 * 1000
            "MIB" -> 1024.0 * 1024
            "GB" -> 1000.0 * 1000 * 1000
            "GIB" -> 1024.0 * 1024 * 1024
            "TB" -> 1e12
            "TIB" -> 1024.0 * 1024 * 1024 * 1024
            else -> return null
        }
        return number * multiplier
    }

    private fun parseEta(hours: String, minutes: String, seconds: String): Long? {
        val h = hours.trimEnd(':').toLongOrNull() ?: 0L
        val m = minutes.toLongOrNull() ?: return null
        val s = seconds.toLongOrNull() ?: return null
        return h * 3600 + m * 60 + s
    }
}
