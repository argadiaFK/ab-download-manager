package com.abdownloadmanager.desktop.nativemessaging.host

import com.abdownloadmanager.desktop.utils.singleInstance.IPCServiceProviderAwakerSupport
import com.abdownloadmanager.desktop.utils.singleInstance.service.IDefaultAppIPCService
import com.abdownloadmanager.integration.model.AddDownloadsFromIntegration
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class AddDownloadNativeRequestHandler(
    private val json: Json,
    private val appIPC: IPCServiceProviderAwakerSupport<IDefaultAppIPCService>,
) : NativeMessagingMessageHandler {
    override fun accept(request: NativeMessagingMessage.Content): Boolean {
        return request.action == "add"
    }

    override suspend fun handle(request: NativeMessagingMessage.Content): NativeMessagingMessage.Content {
        val integrationRequest = json.decodeFromString<AddDownloadsFromIntegration>(request.payload)
        appIPC.getService().useService {
            it.addDownloadByGui(integrationRequest)
        }
        return context(json) {
            NativeMessagingMessage.Content.boolean(true)
        }
    }
}

class PingNativeRequestHandler(
    private val json: Json
) : NativeMessagingMessageHandler {
    override fun accept(request: NativeMessagingMessage.Content): Boolean {
        return request.action == "ping"
    }

    override suspend fun handle(request: NativeMessagingMessage.Content): NativeMessagingMessage.Content {
        return context(json) {
            NativeMessagingMessage.Content.boolean(true)
        }
    }
}

@Serializable
data class YtDlpVideoRequest(
    val url: String,
)

@Serializable
data class YtDlpPanelFetchRequest(
    val url: String,
)

@Serializable
data class YtDlpPanelDownloadRequest(
    val url: String,
    val formatId: String = "best",
    val title: String? = null,
)

/**
 * Handles the browser extension's IDM-style panel "fetch info" request:
 * returns the video title + trimmed format list as JSON (no dialog opened).
 */
class YtDlpFetchInfoNativeRequestHandler(
    private val json: Json,
    private val appIPC: IPCServiceProviderAwakerSupport<IDefaultAppIPCService>,
) : NativeMessagingMessageHandler {
    override fun accept(request: NativeMessagingMessage.Content): Boolean {
        return request.action == "ytdlp_fetch_info"
    }

    override suspend fun handle(request: NativeMessagingMessage.Content): NativeMessagingMessage.Content {
        val fetchRequest = json.decodeFromString<YtDlpPanelFetchRequest>(request.payload)
        val info = appIPC.getService().useService {
            it.fetchYtDlpInfo(fetchRequest.url)
        }
        return context(json) {
            NativeMessagingMessage.Content.create(info)
        }
    }
}

/**
 * Handles the browser extension's IDM-style panel "download" request:
 * starts the download in the background (no dialog opened).
 */
class YtDlpDownloadNativeRequestHandler(
    private val json: Json,
    private val appIPC: IPCServiceProviderAwakerSupport<IDefaultAppIPCService>,
) : NativeMessagingMessageHandler {
    override fun accept(request: NativeMessagingMessage.Content): Boolean {
        return request.action == "ytdlp_download"
    }

    override suspend fun handle(request: NativeMessagingMessage.Content): NativeMessagingMessage.Content {
        val downloadRequest = json.decodeFromString<YtDlpPanelDownloadRequest>(request.payload)
        val accepted = appIPC.getService().useService {
            it.downloadYtDlpVideo(downloadRequest.url, downloadRequest.formatId, downloadRequest.title)
        }
        return context(json) {
            NativeMessagingMessage.Content.boolean(accepted)
        }
    }
}

/**
 * Handles the browser extension's IDM-style "Download video" button:
 * opens the yt-dlp dialog in the app with the page URL prefilled.
 */
class YtDlpVideoNativeRequestHandler(
    private val json: Json,
    private val appIPC: IPCServiceProviderAwakerSupport<IDefaultAppIPCService>,
) : NativeMessagingMessageHandler {
    override fun accept(request: NativeMessagingMessage.Content): Boolean {
        return request.action == "ytdlp_video"
    }

    override suspend fun handle(request: NativeMessagingMessage.Content): NativeMessagingMessage.Content {
        val videoRequest = json.decodeFromString<YtDlpVideoRequest>(request.payload)
        appIPC.getService().useService {
            it.openYtDlpDialog(videoRequest.url)
        }
        return context(json) {
            NativeMessagingMessage.Content.boolean(true)
        }
    }
}

object DefinedNativeMessagingHandlers {
    fun getAll(
        json: Json, appIPC: IPCServiceProviderAwakerSupport<IDefaultAppIPCService>,
    ): List<NativeMessagingMessageHandler> {
        return listOf(
            AddDownloadNativeRequestHandler(json, appIPC),
            PingNativeRequestHandler(json),
            YtDlpVideoNativeRequestHandler(json, appIPC),
            YtDlpFetchInfoNativeRequestHandler(json, appIPC),
            YtDlpDownloadNativeRequestHandler(json, appIPC),
        )
    }
}
