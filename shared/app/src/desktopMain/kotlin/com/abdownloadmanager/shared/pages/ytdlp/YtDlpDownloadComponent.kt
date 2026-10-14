package com.abdownloadmanager.shared.pages.ytdlp

import com.abdownloadmanager.shared.util.BaseComponent
import com.abdownloadmanager.shared.util.DefinedPaths
import com.arkivanov.decompose.ComponentContext
import ir.amirab.downloader.ytdlp.YtDlpBinaryManager
import ir.amirab.downloader.ytdlp.YtDlpClient
import ir.amirab.downloader.ytdlp.YtDlpFormatSelector
import ir.amirab.downloader.ytdlp.YtDlpProgress
import ir.amirab.downloader.ytdlp.YtDlpSettingsStore
import ir.amirab.downloader.ytdlp.YtDlpVideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

/**
 * Desktop-only (JVM) component behind the "Download Video (yt-dlp)" dialog.
 *
 * The actual downloading is done by the external yt-dlp binary, outside of ABDM's own
 * download engine/queue system, so progress is shown in the dialog itself.
 */
class YtDlpDownloadComponent(
    ctx: ComponentContext,
    private val onCloseRequest: () -> Unit,
) : BaseComponent(ctx), KoinComponent {

    /** Decompose slot config. */
    data class Config(val prefillUrl: String = "")

    sealed interface FetchState {
        data object Idle : FetchState
        data object Loading : FetchState
        data class Loaded(val info: YtDlpVideoInfo) : FetchState
        data class Error(val message: String, val suggestYtDlpUpdate: Boolean = false) : FetchState
    }

    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Running(val progress: YtDlpProgress) : DownloadState
        data object Done : DownloadState
        data class Error(val message: String) : DownloadState
    }

    private val settingsStore by inject<YtDlpSettingsStore>()
    private val definedPaths by inject<DefinedPaths>()

    private val binaryManager: YtDlpBinaryManager by lazy {
        YtDlpBinaryManager(
            appDataDir = File(definedPaths.dataDir.toString()),
            settingsProvider = { settingsStore.settings.value },
        )
    }

    private val _url = MutableStateFlow("")
    val url: StateFlow<String> = _url.asStateFlow()
    fun setUrl(value: String) {
        _url.value = value
    }

    private val _fetchState = MutableStateFlow<FetchState>(FetchState.Idle)
    val fetchState: StateFlow<FetchState> = _fetchState.asStateFlow()

    private val _selectedFormatId = MutableStateFlow("best")
    val selectedFormatId: StateFlow<String> = _selectedFormatId.asStateFlow()
    fun selectFormat(formatId: String) {
        _selectedFormatId.value = formatId
    }

    private val _recommendedFormatId = MutableStateFlow<String?>(null)
    val recommendedFormatId: StateFlow<String?> = _recommendedFormatId.asStateFlow()

    private val _outputFolder = MutableStateFlow(initialOutputFolder())
    val outputFolder: StateFlow<String> = _outputFolder.asStateFlow()
    fun setOutputFolder(value: String) {
        _outputFolder.value = value
    }

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _binaryStatus = MutableStateFlow<String?>(null)
    val binaryStatus: StateFlow<String?> = _binaryStatus.asStateFlow()

    private var fetchJob: Job? = null
    private var downloadJob: Job? = null

    fun fetchInfo() {
        val videoUrl = _url.value.trim()
        if (videoUrl.isBlank()) return
        fetchJob?.cancel()
        _downloadState.value = DownloadState.Idle
        _fetchState.value = FetchState.Loading
        fetchJob = scope.launch {
            try {
                val binary = requireBinary()
                val info = YtDlpClient(binaryPathProvider = { binary.absolutePath }).fetchInfo(videoUrl)
                val recommended = YtDlpFormatSelector.defaultFormat(info)
                _recommendedFormatId.value = recommended
                _selectedFormatId.value = recommended
                _fetchState.value = FetchState.Loaded(info)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val raw = e.message ?: "Unknown error"
                _fetchState.value = FetchState.Error(raw, suggestYtDlpUpdate(raw))
            }
        }
    }

    /**
     * Heuristic: these yt-dlp errors are almost always fixed by updating
     * yt-dlp to the latest version.
     */
    private fun suggestYtDlpUpdate(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("needs to be reloaded")
            || lower.contains("sign in to confirm")
            || lower.contains("unable to extract")
            || lower.contains("not available")
    }

    fun startDownload() {
        if (_fetchState.value !is FetchState.Loaded) return
        val videoUrl = _url.value.trim()
        val format = _selectedFormatId.value.ifBlank { "best" }
        val folder = _outputFolder.value.trim().ifBlank { defaultOutputFolder() }
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Running(
            YtDlpProgress(
                percent = 0.0,
                downloadedBytes = 0,
                totalBytes = null,
                speedBytesPerSecond = null,
                etaSeconds = null,
            )
        )
        downloadJob = scope.launch {
            try {
                val binary = requireBinary()
                val template = folder.trimEnd('/', '\\') +
                    File.separator + "%(title)s [%(id)s].%(ext)s"
                YtDlpClient(binaryPathProvider = { binary.absolutePath })
                    .download(videoUrl, format = format, outputTemplate = template)
                    .collect { progress ->
                        _downloadState.value = DownloadState.Running(progress)
                    }
                _downloadState.value = DownloadState.Done
            } catch (e: CancellationException) {
                _downloadState.value = DownloadState.Idle
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
    }

    /** Resolves the binary, updating [binaryStatus]; throws when unavailable. */
    private fun requireBinary(): File {
        val binary = binaryManager.resolveBinary()
        if (binary == null) {
            val hint = "yt-dlp binary not found. Install it or enable auto-download in Settings → yt-dlp."
            _binaryStatus.value = hint
            throw IllegalStateException(hint)
        }
        _binaryStatus.value = "yt-dlp ${binaryManager.getVersion(binary) ?: "?"}"
        return binary
    }

    /** Refreshes [binaryStatus] without starting any download. Used by Settings → Test. */
    fun refreshBinaryStatus() {
        scope.launch {
            try {
                requireBinary()
            } catch (_: Exception) {
                // status already updated by requireBinary
            }
        }
    }

    fun close() = onCloseRequest()

    companion object {
        private fun defaultOutputFolder(): String {
            return File(System.getProperty("user.home"), "Downloads").absolutePath
        }
    }

    private fun initialOutputFolder(): String {
        return settingsStore.settings.value.defaultDownloadFolder
            ?.takeIf { it.isNotBlank() }
            ?: defaultOutputFolder()
    }
}
