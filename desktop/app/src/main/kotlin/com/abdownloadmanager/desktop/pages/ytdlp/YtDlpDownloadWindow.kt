package com.abdownloadmanager.desktop.pages.ytdlp

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.v2.WindowBoundsProvider
import androidx.compose.ui.window.v2.WindowPositionProvider
import androidx.compose.ui.window.v2.WindowSizeProvider
import androidx.compose.ui.window.v2.rememberWindowState
import com.abdownloadmanager.desktop.AppComponent
import com.abdownloadmanager.desktop.window.custom.CustomWindow
import com.abdownloadmanager.desktop.window.custom.rememberWindowController
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.pages.ytdlp.YtDlpDownloadComponent
import com.abdownloadmanager.shared.pages.ytdlp.YtDlpDownloadDialogContent
import com.abdownloadmanager.shared.util.rememberChild
import com.abdownloadmanager.shared.util.ui.theme.LocalUiScale
import ir.amirab.util.compose.resources.myStringResource
import ir.amirab.util.desktop.screen.applyUiScale

@Composable
fun YtDlpDownloadWindow(
    appComponent: AppComponent
) {
    val child = appComponent.ytDlpWindowSlot.rememberChild()
    child?.let {
        YtDlpDownloadWindow(it)
    }
}

@Composable
private fun YtDlpDownloadWindow(
    component: YtDlpDownloadComponent,
) {
    val windowState = rememberWindowState(
        initialBoundsProvider = WindowBoundsProvider(
            sizeProvider = WindowSizeProvider.Fixed(
                size = DpSize(560.dp, 620.dp)
                    .applyUiScale(LocalUiScale.current),
            ),
            positionProvider = WindowPositionProvider.CenteredOnScreen
        )
    )
    CustomWindow(
        state = windowState,
        onCloseRequest = component::close,
        windowController = rememberWindowController(
            title = myStringResource(Res.string.ytdlp_download),
        ),
    ) {
        YtDlpDownloadDialogContent(
            component,
        )
    }
}
