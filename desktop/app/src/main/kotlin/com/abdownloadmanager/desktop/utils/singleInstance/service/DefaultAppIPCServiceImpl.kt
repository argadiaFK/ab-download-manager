package com.abdownloadmanager.desktop.utils.singleInstance.service

import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.utils.singleInstance.SingleInstanceInitialized
import com.abdownloadmanager.integration.IntegrationHandler
import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import com.abdownloadmanager.shared.ui.widget.NotificationType
import com.abdownloadmanager.shared.util.DefinedPaths
import ir.amirab.downloader.downloaditem.contexts.RemovedBy
import ir.amirab.downloader.downloaditem.contexts.User
import ir.amirab.downloader.monitor.CompletedDownloadItemState
import ir.amirab.downloader.monitor.ProcessingDownloadItemState
import ir.amirab.downloader.monitor.statusOrFinished
import ir.amirab.downloader.ytdlp.YtDlpBinaryManager
import ir.amirab.downloader.ytdlp.YtDlpClient
import ir.amirab.downloader.ytdlp.YtDlpFormatSelector
import ir.amirab.downloader.ytdlp.YtDlpSettingsStore
import ir.amirab.util.compose.asStringSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.io.File

class DefaultAppIPCServiceImpl : IDefaultAppIPCService, KoinComponent {
    private suspend fun awaitAppBoot() {
        SingleInstanceInitialized.awaitDone()
    }

    private val appComponent: AppComponent by inject()
    private val downloadSystem get() = appComponent.downloadSystem
    private val integrationHandler: IntegrationHandler by inject()

    override suspend fun addDownloadByGui(request: AddDownloadsFromIntegration) {
        integrationHandler.addDownloadByGui(request)
    }

    override suspend fun openYtDlpDialog(url: String) {
        awaitAppBoot()
        appComponent.openYtDlpDialog(url)
    }

    private val ytDlpSettingsStore: YtDlpSettingsStore by inject()
    private val definedPaths: DefinedPaths by inject()
    private val ytDlpScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun ytDlpBinaryManager(): YtDlpBinaryManager {
        return YtDlpBinaryManager(
            appDataDir = File(definedPaths.dataDir.toString()),
            settingsProvider = { ytDlpSettingsStore.settings.value },
        )
    }

    private fun ytDlpDefaultFolder(): String {
        return ytDlpSettingsStore.settings.value.defaultDownloadFolder
            ?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("user.home"), "Downloads").absolutePath
    }

    override suspend fun fetchYtDlpInfo(url: String): YtDlpInfoIPC {
        awaitAppBoot()
        val manager = ytDlpBinaryManager()
        val binary = manager.resolveBinary()
            ?: throw IllegalStateException("yt-dlp binary not found. Install it or enable auto-download in Settings → yt-dlp.")
        val info = YtDlpClient(binaryPathProvider = { binary.absolutePath }).fetchInfo(url)
        val formats = YtDlpFormatSelector.sortedForDisplay(info)
            .take(25)
            .map {
                YtDlpFormatIPC(
                    formatId = it.formatId,
                    resolution = it.displayResolution,
                    ext = it.ext,
                    sizeBytes = it.effectiveSize,
                )
            }
        return YtDlpInfoIPC(
            title = info.displayTitle,
            durationSeconds = info.durationSeconds,
            formats = formats,
        )
    }

    override suspend fun downloadYtDlpVideo(url: String, formatId: String, title: String?): Boolean {
        awaitAppBoot()
        val folder = ytDlpDefaultFolder()
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: url
        ytDlpScope.launch {
            try {
                val manager = ytDlpBinaryManager()
                val binary = manager.resolveBinary()
                    ?: throw IllegalStateException("yt-dlp binary not found.")
                val template = folder.trimEnd('/', '\\') +
                    File.separator + "%(title)s [%(id)s].%(ext)s"
                YtDlpClient(binaryPathProvider = { binary.absolutePath })
                    .download(url, format = formatId.ifBlank { "best" }, outputTemplate = template)
                    .collect { /* progress is not reported to the extension panel */ }
                appComponent.sendNotification(
                    tag = "ytdlp:$url",
                    title = "Download selesai".asStringSource(),
                    description = displayTitle.asStringSource(),
                    type = NotificationType.Success,
                )
            } catch (e: Exception) {
                appComponent.sendNotification(
                    tag = "ytdlp:$url",
                    title = "Download gagal".asStringSource(),
                    description = (e.message ?: "Unknown error").asStringSource(),
                    type = NotificationType.Error,
                )
            }
        }
        return true
    }

    override suspend fun addDownload(request: NewDownloadTask): Long {
        awaitAppBoot()
        return integrationHandler.addDownload(request)
    }

    override suspend fun pauseDownload(ids: List<Long>) {
        awaitAppBoot()
        for (id in ids) {
            downloadSystem.manualPause(id)
        }
    }

    override suspend fun resumeDownload(ids: List<Long>) {
        awaitAppBoot()
        for (id in ids) {
            downloadSystem.userManualResume(id)
        }
    }

    override suspend fun removeDownload(ids: List<Long>, alsoRemoveFile: Boolean) {
        awaitAppBoot()
        for (id in ids) {
            downloadSystem.removeDownload(id, alsoRemoveFile, RemovedBy(User))
        }
    }

    override suspend fun showDownload(ids: List<Long>): List<ShowDownloadIPC> {
        awaitAppBoot()

        return ids.mapNotNull {
            runCatching {
                downloadSystem.getDownloadItemById(it)
            }.getOrNull()
        }.map {
            ShowDownloadIPC(
                name = it.name,
                folder = it.folder,
                id = it.id,
                status = ShowDownloadIPC.DownloadStatus.fromDownloadStatus(
                    it.status
                ),
                percent = null
            )
        }
    }

    override fun watchDownload(ids: List<Long>): Flow<List<ShowDownloadIPC>> {
        return flow {
            downloadSystem.downloadMonitor.downloadListFlow.collect { downloads ->
                val byId = downloads.associateBy { it.id }
                val toBeSend = ids
                    .mapNotNull(byId::get)
                    .map {
                        ShowDownloadIPC(
                            id = it.id,
                            name = it.name,
                            folder = it.folder,
                            status = ShowDownloadIPC.DownloadStatus.fromDownloadStatus(
                                it.statusOrFinished()
                            ),
                            percent = when (it) {
                                is ProcessingDownloadItemState -> it.percent
                                is CompletedDownloadItemState -> 100
                            }
                        )
                    }
                emit(toBeSend)
            }
        }
    }

    override suspend fun listQueues(): List<ApiQueueModel> {
        awaitAppBoot()
        return downloadSystem.queueManager.queues.value
            .asSequence()
            .map { it.queueModel.value }
            .map { ApiQueueModel(it.id, it.name) }
            .toList()
    }

}
