package com.abdownloadmanager.desktop.pages.settings

import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.configurable.item.BooleanConfigurable
import com.abdownloadmanager.shared.ui.configurable.item.StringConfigurable
import com.abdownloadmanager.shared.util.DefinedPaths
import ir.amirab.downloader.ytdlp.YtDlpBinaryManager
import ir.amirab.downloader.ytdlp.YtDlpSettingsStore
import ir.amirab.util.compose.asStringSource
import ir.amirab.util.flow.createMutableStateFlowFromStateFlow
import ir.amirab.util.flow.mapStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings UI (configurables) for the yt-dlp integration.
 */
object YtDlpSettingsConfigurables {
    fun binaryPathConfig(
        store: YtDlpSettingsStore,
        scope: CoroutineScope,
    ): StringConfigurable {
        return StringConfigurable(
            title = Res.string.ytdlp_binary_path.asStringSource(),
            description = Res.string.ytdlp_binary_path_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = store.settings.mapStateFlow { it.binaryPath.orEmpty() },
                updater = { store.update { settings -> settings.copy(binaryPath = it.ifBlank { null }) } },
                scope = scope,
            ),
            describe = { it.asStringSource() },
        )
    }

    fun autoDownloadBinaryConfig(
        store: YtDlpSettingsStore,
        scope: CoroutineScope,
    ): BooleanConfigurable {
        return BooleanConfigurable(
            title = Res.string.ytdlp_auto_download_binary.asStringSource(),
            description = Res.string.ytdlp_auto_download_binary_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = store.settings.mapStateFlow { it.autoDownloadBinary },
                updater = { store.update { settings -> settings.copy(autoDownloadBinary = it) } },
                scope = scope,
            ),
            describe = {
                if (it) {
                    Res.string.enabled.asStringSource()
                } else {
                    Res.string.disabled.asStringSource()
                }
            },
        )
    }

    /**
     * Default folder used for yt-dlp downloads (initial value of the
     * download dialog's save folder). Blank = the OS Downloads folder.
     */
    fun defaultDownloadFolderConfig(
        store: YtDlpSettingsStore,
        scope: CoroutineScope,
    ): StringConfigurable {
        return StringConfigurable(
            title = Res.string.ytdlp_default_download_folder.asStringSource(),
            description = Res.string.ytdlp_default_download_folder_description.asStringSource(),
            backedBy = createMutableStateFlowFromStateFlow(
                flow = store.settings.mapStateFlow { it.defaultDownloadFolder.orEmpty() },
                updater = { store.update { settings -> settings.copy(defaultDownloadFolder = it.ifBlank { null }) } },
                scope = scope,
            ),
            describe = { it.asStringSource() },
        )
    }

    /**
     * Read-only row showing the detected yt-dlp version (refreshes each time
     * the settings section is opened).
     */    fun detectedVersionConfig(
        store: YtDlpSettingsStore,
        definedPaths: DefinedPaths,
        scope: CoroutineScope,
    ): StringConfigurable {
        val versionText = MutableStateFlow("…")
        scope.launch(Dispatchers.IO) {
            val manager = YtDlpBinaryManager(
                appDataDir = File(definedPaths.dataDir.toString()),
                settingsProvider = { store.settings.value },
            )
            val binary = manager.resolveBinary(downloadIfMissing = false)
            versionText.value = when {
                binary == null -> "not found"
                else -> manager.getVersion(binary) ?: "unknown"
            }
        }
        return StringConfigurable(
            title = Res.string.ytdlp_test_binary.asStringSource(),
            description = "".asStringSource(),
            backedBy = versionText,
            describe = { it.asStringSource() },
            enabled = MutableStateFlow(false),
        )
    }
}
