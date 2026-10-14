package ir.amirab.downloader.ytdlp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.io.File

/**
 * File-backed [YtDlpSettings] store. Persists as JSON in `<appDataDir>/ytdlp/settings.json`.
 * JVM-only (lives in the JVM-only `downloader:ytdlp` module on purpose).
 */
class YtDlpSettingsStore(
    appDataDir: File,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val file = File(File(appDataDir, "ytdlp"), "settings.json")

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<YtDlpSettings> = _settings.asStateFlow()

    init {
        scope.launch {
            _settings.drop(1).collect { save(it) }
        }
    }

    fun update(transform: (YtDlpSettings) -> YtDlpSettings) {
        _settings.value = transform(_settings.value)
    }

    private fun load(): YtDlpSettings {
        return runCatching {
            if (!file.isFile) return YtDlpSettings()
            ytdlpJson.decodeFromString<YtDlpSettings>(file.readText())
        }.getOrElse { YtDlpSettings() }
    }

    private fun save(settings: YtDlpSettings) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(ytdlpJson.encodeToString(settings))
        }
    }
}
