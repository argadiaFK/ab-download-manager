package com.abdownloadmanager.desktop.utils.singleInstance.service

import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import com.abdownloadmanager.integration.model.ApiQueueModel
import com.abdownloadmanager.integration.model.NewDownloadTask
import ir.amirab.downloader.downloaditem.DownloadJobStatus
import ir.amirab.downloader.utils.ExceptionUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.rpc.annotations.Rpc
import kotlinx.serialization.Serializable
import ir.amirab.downloader.downloaditem.DownloadStatus as DownloadItemStatus

@Rpc
interface IDefaultAppIPCService {
    // download
    suspend fun addDownloadByGui(request: AddDownloadsFromIntegration)
    suspend fun addDownload(request: NewDownloadTask): Long

    // yt-dlp video download (triggered e.g. by the browser extension's video button)
    suspend fun openYtDlpDialog(url: String)

    /**
     * Headless yt-dlp info fetch for the browser extension's IDM-style panel:
     * returns the video title/duration plus a trimmed, display-sorted format
     * list (no app dialog is opened).
     */
    suspend fun fetchYtDlpInfo(url: String): YtDlpInfoIPC

    /**
     * Starts a yt-dlp download in the background (no dialog). The file lands in
     * the configured yt-dlp default folder; an in-app notification is shown on
     * completion/failure. Returns true when the download was accepted.
     */
    suspend fun downloadYtDlpVideo(url: String, formatId: String, title: String?): Boolean

    suspend fun pauseDownload(ids: List<Long>)
    suspend fun resumeDownload(ids: List<Long>)

    suspend fun removeDownload(ids: List<Long>, alsoRemoveFile: Boolean)

    suspend fun showDownload(ids: List<Long>): List<ShowDownloadIPC>
    fun watchDownload(ids: List<Long>): Flow<List<ShowDownloadIPC>>

    suspend fun listQueues(): List<ApiQueueModel>
}

/**
 * Trimmed yt-dlp video info sent to the browser extension's download panel.
 * Kept small on purpose: native-messaging messages should stay far below the
 * 1 MB browser limit.
 */
@Serializable
data class YtDlpInfoIPC(
    val title: String,
    val durationSeconds: Long?,
    /** Display-sorted, best first. */
    val formats: List<YtDlpFormatIPC>,
)

@Serializable
data class YtDlpFormatIPC(
    val formatId: String,
    /** e.g. "1080p", "audio only" */
    val resolution: String,
    /** e.g. "mp4" */
    val ext: String?,
    val sizeBytes: Long?,
)

@Serializable
data class ShowDownloadIPC(    val name: String,
    val folder: String,
    val id: Long,
    val percent: Int?,
    val status: DownloadStatus
) {
    @Serializable
    enum class DownloadStatus {
        Paused,
        Error,
        Downloading,
        Finished,
        PreparingFile,
        Resuming,
        Retrying;

        companion object {
            fun fromDownloadStatus(status: DownloadJobStatus): DownloadStatus {
                return when (status) {
                    is DownloadJobStatus.Canceled -> {
                        if (ExceptionUtils.isNormalCancellation(status.e)) {
                            Paused
                        } else {
                            Error
                        }
                    }

                    DownloadJobStatus.Downloading -> Downloading
                    DownloadJobStatus.Finished -> Finished
                    DownloadJobStatus.IDLE -> Paused
                    is DownloadJobStatus.PreparingFile -> PreparingFile
                    DownloadJobStatus.Resuming -> Resuming
                    is DownloadJobStatus.Retrying -> Retrying
                }
            }

            fun fromDownloadStatus(status: DownloadItemStatus): ShowDownloadIPC.DownloadStatus {
                return when (status) {
                    DownloadItemStatus.Paused -> Paused
                    DownloadItemStatus.Error -> Error
                    DownloadItemStatus.Downloading -> Downloading
                    DownloadItemStatus.Completed -> Finished
                    DownloadItemStatus.Added -> Paused
                }
            }
        }
    }
}
