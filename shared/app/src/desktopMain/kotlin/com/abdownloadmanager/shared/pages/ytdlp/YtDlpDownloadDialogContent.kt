package com.abdownloadmanager.shared.pages.ytdlp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import com.abdownloadmanager.resources.Res
import com.abdownloadmanager.shared.ui.widget.ActionButton
import com.abdownloadmanager.shared.ui.widget.ExpandableItem
import com.abdownloadmanager.shared.ui.widget.LoadingIndicator
import com.abdownloadmanager.shared.ui.widget.MyTextFieldWithIcons
import com.abdownloadmanager.shared.ui.widget.Text
import com.abdownloadmanager.shared.util.div
import com.abdownloadmanager.shared.util.ui.myColors
import com.abdownloadmanager.shared.util.ui.theme.myShapes
import ir.amirab.downloader.ytdlp.YtDlpFormat
import ir.amirab.downloader.ytdlp.YtDlpFormatSelector
import ir.amirab.downloader.ytdlp.YtDlpProgress
import ir.amirab.util.compose.resources.myStringResource

@Composable
fun YtDlpDownloadDialogContent(component: YtDlpDownloadComponent) {
    val url by component.url.collectAsState()
    val fetchState by component.fetchState.collectAsState()
    val downloadState by component.downloadState.collectAsState()
    val selectedFormatId by component.selectedFormatId.collectAsState()
    val recommendedFormatId by component.recommendedFormatId.collectAsState()
    val outputFolder by component.outputFolder.collectAsState()
    val binaryStatus by component.binaryStatus.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Step 1: video link
        SectionHeader(myStringResource(Res.string.ytdlp_step1_link))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MyTextFieldWithIcons(
                text = url,
                onTextChange = component::setUrl,
                placeholder = myStringResource(Res.string.ytdlp_video_url),
                modifier = Modifier.weight(1f),
            )
            val fetching = fetchState is YtDlpDownloadComponent.FetchState.Loading
            ActionButton(
                text = myStringResource(Res.string.ytdlp_fetch_info),
                enabled = url.isNotBlank() && !fetching &&
                    downloadState !is YtDlpDownloadComponent.DownloadState.Running,
                onClick = component::fetchInfo,
            )
        }
        binaryStatus?.let {
            Text(it)
        }

        when (val state = fetchState) {
            is YtDlpDownloadComponent.FetchState.Idle -> Unit
            is YtDlpDownloadComponent.FetchState.Loading -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LoadingIndicator(Modifier.size(20.dp))
                    Text(myStringResource(Res.string.ytdlp_fetching))
                }
            }
            is YtDlpDownloadComponent.FetchState.Error -> {
                Text(state.message)
                if (state.suggestYtDlpUpdate) {
                    Text(
                        myStringResource(Res.string.ytdlp_update_yt_dlp_hint),
                        color = myColors.primary,
                    )
                }
            }
            is YtDlpDownloadComponent.FetchState.Loaded -> {
                val info = state.info
                SectionHeader(myStringResource(Res.string.ytdlp_step2_quality))
                Text(info.displayTitle)
                info.durationSeconds?.let { seconds ->
                    Text(formatDuration(seconds))
                }
                FormatDropdown(
                    formats = YtDlpFormatSelector.sortedForDisplay(info).take(30),
                    selectedFormatId = selectedFormatId,
                    recommendedFormatId = recommendedFormatId,
                    onSelect = component::selectFormat,
                )
                Text(
                    myStringResource(Res.string.ytdlp_saved_to) + " " + outputFolder,
                )
            }
        }

        // Download state row
        when (val state = downloadState) {
            is YtDlpDownloadComponent.DownloadState.Idle -> {
                val canDownload = fetchState is YtDlpDownloadComponent.FetchState.Loaded
                ActionButton(
                    text = myStringResource(Res.string.ytdlp_download_action),
                    enabled = canDownload,
                    onClick = component::startDownload,
                )
            }
            is YtDlpDownloadComponent.DownloadState.Running -> {
                DownloadProgress(state.progress)
                ActionButton(
                    text = myStringResource(Res.string.ytdlp_cancel),
                    onClick = component::cancelDownload,
                )
            }
            is YtDlpDownloadComponent.DownloadState.Done -> {
                Text(myStringResource(Res.string.ytdlp_download_finished))
                ActionButton(
                    text = myStringResource(Res.string.ytdlp_close),
                    onClick = component::close,
                )
            }
            is YtDlpDownloadComponent.DownloadState.Error -> {
                Text(state.message)
                ActionButton(
                    text = myStringResource(Res.string.ytdlp_download_action),
                    onClick = component::startDownload,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
    )
}

@Composable
private fun FormatDropdown(
    formats: List<YtDlpFormat>,
    selectedFormatId: String,
    recommendedFormatId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = formats.firstOrNull { it.formatId == selectedFormatId }
    ExpandableItem(
        isExpanded = expanded,
        modifier = modifier,
        header = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(myShapes.defaultRounded)
                    .background(myColors.surface / 50)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    selected?.let { formatLabel(it) } ?: selectedFormatId,
                    modifier = Modifier.weight(1f),
                )
                if (selected != null && selected.formatId == recommendedFormatId) {
                    RecommendedBadge()
                }
                Text(if (expanded) "▴" else "▾")
            }
        },
        body = {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(myShapes.defaultRounded)
                    .background(myColors.surface / 30)
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(formats, key = { it.formatId }) { format ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(format.formatId)
                                expanded = false
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            formatLabel(format),
                            modifier = Modifier.weight(1f),
                        )
                        if (format.formatId == recommendedFormatId) {
                            RecommendedBadge()
                        }
                    }
                }
            }
        },
    )
}

/** Compact one-line description: resolution • type • size • note. */
private fun formatLabel(format: YtDlpFormat): String = buildString {
    append(format.displayResolution)
    format.ext?.let { append(" • $it") }
    format.effectiveSize?.let { append(" • ${formatBytes(it)}") }
    format.formatNote?.let { append(" • $it") }
}

@Composable
private fun RecommendedBadge() {
    Box(
        Modifier
            .clip(myShapes.defaultRounded)
            .background(myColors.primary / 0.18f)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            myStringResource(Res.string.ytdlp_recommended),
            color = myColors.primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DownloadProgress(progress: YtDlpProgress) {
    val fraction = (progress.percent ?: 0.0) / 100.0
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(myShapes.defaultRounded)
                .background(myColors.onBackground / 0.15f)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.toFloat().coerceIn(0f, 1f))
                    .height(8.dp)
                    .clip(myShapes.defaultRounded)
                    .background(myColors.primary)
            )
        }
        Text(
            buildString {
                progress.percent?.let { append("%.1f%%".format(it)) }
                progress.speedBytesPerSecond?.let { append(" • ${formatBytes(it.toLong())}/s") }
                progress.etaSeconds?.let { append(" • ETA ${formatDuration(it)}") }
                when (progress.state) {
                    YtDlpProgress.State.MERGING -> append(" • merging…")
                    YtDlpProgress.State.POST_PROCESSING -> append(" • post-processing…")
                    else -> Unit
                }
            }
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024
    var unit = units[0]
    for (u in units) {
        unit = u
        if (value < 1024) break
        value /= 1024
    }
    return "%.1f %s".format(value, unit)
}

private fun formatDuration(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
