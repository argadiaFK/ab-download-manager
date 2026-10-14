package ir.amirab.downloader.ytdlp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * JVM-only client that shells out to the yt-dlp binary.
 *
 * @param binaryPathProvider resolves the binary to execute for each call.
 * @param ioDispatcher dispatcher used for blocking process IO.
 */
class YtDlpClient(
    private val binaryPathProvider: () -> String,
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO,
) {
    class YtDlpException(message: String, val exitCode: Int = -1, val output: String = "") :
        Exception(message)

    /**
     * Runs `yt-dlp --dump-json --no-playlist` and parses the single JSON document.
     */
    suspend fun fetchInfo(url: String, timeoutSeconds: Long = 60): YtDlpVideoInfo =
        withContext(ioDispatcher) {
            val binary = binaryPathProvider()
            val process = startProcess(
                binary, "--dump-json", "--no-playlist", "--no-warnings",
                "--no-check-certificate", url
            )
            try {
                val output = withTimeout(timeoutSeconds * 1000) {
                    process.inputStream.bufferedReader().readText()
                }
                val exit = process.waitFor()
                if (exit != 0) {
                    // stderr is merged into stdout (see startProcess)
                    val err = output.trim().takeLast(2000)
                    throw YtDlpException("yt-dlp exited with code $exit: $err", exit, err)
                }
                val json = output.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.startsWith("{") }
                    ?: throw YtDlpException("yt-dlp returned no JSON for $url", exit, output.take(500))
                ytdlpJson.decodeFromString<YtDlpVideoInfo>(json)
            } catch (e: TimeoutCancellationException) {
                process.destroyForcibly()
                throw YtDlpException("yt-dlp timed out after ${timeoutSeconds}s for $url", -1)
            } finally {
                process.destroyForcibly()
            }
        }

    /**
     * Runs `yt-dlp --flat-playlist --dump-json` and returns up to [maxItems] entries.
     * Each output line is one JSON object.
     */
    suspend fun fetchPlaylist(url: String, maxItems: Int = 50, timeoutSeconds: Long = 120): List<YtDlpPlaylistEntry> =
        withContext(ioDispatcher) {
            val binary = binaryPathProvider()
            val process = startProcess(
                binary, "--flat-playlist", "--dump-json", "--no-warnings",
                "--playlist-end", maxItems.toString(), url
            )
            try {
                val lines: List<String> = withTimeout(timeoutSeconds * 1000) {
                    process.inputStream.bufferedReader().readLines()
                }
                val exit = process.waitFor()
                if (exit != 0) {
                    // stderr is merged into stdout (see startProcess)
                    val err = lines.takeLast(10).joinToString("\n").trim().take(2000)
                    throw YtDlpException("yt-dlp exited with code $exit: $err", exit, err)
                }
                lines.asSequence()
                    .map { it.trim() }
                    .filter { it.startsWith("{") }
                    .mapNotNull { runCatching { ytdlpJson.decodeFromString<YtDlpPlaylistEntry>(it) }.getOrNull() }
                    .flatMap { entry ->
                        // --dump-json on a playlist URL prints a single playlist object with nested entries
                        if (entry.entries.isNotEmpty()) entry.entries.asSequence() else sequenceOf(entry)
                    }
                    .take(maxItems)
                    .toList()
            } catch (e: TimeoutCancellationException) {
                process.destroyForcibly()
                throw YtDlpException("yt-dlp timed out after ${timeoutSeconds}s for $url", -1)
            } finally {
                process.destroyForcibly()
            }
        }

    /**
     * Downloads [url] with `-f [format]` into [outputTemplate] (yt-dlp output template,
     * e.g. `/downloads/%(title)s [%(id)s].%(ext)s`).
     *
     * Emits [YtDlpProgress] parsed from `--newline` output. Cancelling the collecting
     * coroutine destroys the yt-dlp process.
     */
    fun download(
        url: String,
        format: String = "best",
        outputTemplate: String,
        extraArgs: List<String> = emptyList(),
    ): Flow<YtDlpProgress> = flow {
        val binary = binaryPathProvider()
        val args = buildList {
            add(binary)
            add("--newline")
            add("--no-warnings")
            add("-f"); add(format)
            add("-o"); add(outputTemplate)
            addAll(extraArgs)
            add(url)
        }
        val process = withContext(ioDispatcher) { startProcess(args) }
        try {
            val reader = process.inputStream.bufferedReader()
            var last: YtDlpProgress? = null
            val recentLines = ArrayDeque<String>(20)
            while (true) {
                val line = withContext(ioDispatcher) { reader.readLine() } ?: break
                // yt-dlp writes progress to stdout with --newline; errors go to stderr.
                if (recentLines.size >= 20) recentLines.removeFirst()
                recentLines.addLast(line.trim())
                val parsed = YtDlpProgressParser.parse(line.trim(), last)
                if (parsed != null) {
                    last = parsed
                    emit(parsed)
                }
            }
            val exit = withContext(ioDispatcher) { process.waitFor() }
            if (exit != 0) {
                // stderr is merged into stdout (see startProcess); report the tail.
                val err = recentLines.takeLast(5).joinToString("\n").take(2000)
                throw YtDlpException("yt-dlp exited with code $exit: $err", exit, err)
            }
            // Ensure a terminal DONE is emitted even if yt-dlp printed no 100% line.
            if (last?.state != YtDlpProgress.State.DONE) {
                emit(
                    (last ?: YtDlpProgress(
                        percent = null, downloadedBytes = null, totalBytes = null,
                        speedBytesPerSecond = null, etaSeconds = null,
                    )).copy(percent = 100.0, state = YtDlpProgress.State.DONE)
                )
            }
        } finally {
            process.destroyForcibly()
        }
    }.flowOn(ioDispatcher)

    private fun startProcess(vararg command: String): Process =
        startProcess(command.toList())

    private fun startProcess(command: List<String>): Process =
        ProcessBuilder(command)
            // Merge stderr into stdout: JSON parsing only looks at lines starting
            // with '{', and this avoids blocking when an extractor is chatty.
            .redirectErrorStream(true)
            .start()
}
