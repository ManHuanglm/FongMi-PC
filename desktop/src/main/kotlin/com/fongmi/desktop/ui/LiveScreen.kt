package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.bean.Channel
import com.fongmi.android.tv.bean.EpgData

private val Overlay = Color(0xEE161B24)
private val OverlayRow = Color(0x33232B38)
private val RowSelected = Color(0xFF3D8BFF)

/**
 * 直播页：布局对齐原版 LiveActivity —— 左侧分组文字列 + 频道列表（编号+台标+名称）浮层样式，
 * EPG 节目单/回看面板靠右；播放视频由独立 PlayerFrame 窗口承载（wid 直绘）
 */
@Composable
fun LiveScreen(model: LiveModel) {
    LaunchedEffect(Unit) { model.ensureReady() }

    Column(modifier = Modifier.fillMaxSize().background(UiTheme.Bg)) {
        LiveHeader(model)
        when {
            model.loading && model.groups.isEmpty() -> CenterMessage(model.message, showSpinner = true)
            model.error.isNotEmpty() && model.groups.isEmpty() -> CenterMessage("加载失败：${model.error}", showSpinner = false)
            model.groups.isEmpty() -> CenterMessage(model.message, showSpinner = false)
            else -> Row(modifier = Modifier.fillMaxSize()) {
                GroupPanel(model)
                ChannelPanel(model, Modifier.weight(1f))
                EpgPanel(model)
            }
        }
    }
}

@Composable
private fun LiveHeader(model: LiveModel) {
    Row(
        modifier = Modifier.fillMaxWidth().background(UiTheme.Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "直播", color = UiTheme.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = model.liveName.ifEmpty { "未加载直播源" }, color = UiTheme.TextSub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.weight(1f))
        if (model.playError.isNotEmpty()) {
            Text(text = model.playError, color = UiTheme.Error, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(2f))
        }
    }
}

/** 左列分组：纯文字行（原版浮层样式，选中项高亮） */
@Composable
private fun GroupPanel(model: LiveModel) {
    LazyColumn(
        modifier = Modifier.width(120.dp).fillMaxHeight().background(Overlay).padding(vertical = 6.dp),
        state = rememberLazyListState()
    ) {
        items(model.groups.size) { index ->
            val group = model.groups[index]
            val selected = index == model.groupIndex
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .background(if (selected) RowSelected.copy(alpha = 0.35f) else Color.Transparent, RoundedCornerShape(6.dp))
                    .clickable { model.selectGroup(index) }
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(
                    text = group.getName(),
                    color = if (selected) UiTheme.Accent else UiTheme.TextMain,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 中列频道列表：编号 + 台标 + 名称（原版频道行样式） */
@Composable
private fun ChannelPanel(model: LiveModel, modifier: Modifier = Modifier) {
    val channels = model.channels(model.groupIndex)
    if (channels.isEmpty()) {
        CenterMessage("该分组暂无频道", showSpinner = false, modifier = modifier)
        return
    }
    LazyColumn(
        state = rememberLazyListState(),
        modifier = modifier.fillMaxHeight().background(Overlay).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(channels, key = { it.getNumber() + it.getName() }) { channel ->
            ChannelRow(model, channel)
        }
    }
}

@Composable
private fun ChannelRow(model: LiveModel, channel: Channel) {
    val isCurrent = model.currentChannel == channel
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isCurrent) RowSelected.copy(alpha = 0.35f) else OverlayRow, RoundedCornerShape(6.dp))
            .border(1.dp, if (isCurrent) RowSelected else Color.Transparent, RoundedCornerShape(6.dp))
            .clickable { model.play(channel) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (channel.getNumber().isNotEmpty()) {
            Text(text = channel.getNumber(), color = UiTheme.TextSub, fontSize = 13.sp, modifier = Modifier.width(38.dp))
        }
        if (channel.getLogo().isNotEmpty()) {
            PosterImage(
                url = channel.getLogo(),
                name = channel.getShow(),
                modifier = Modifier.size(width = 34.dp, height = 24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = channel.getShow(),
            color = if (isCurrent) UiTheme.Accent else UiTheme.TextMain,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        val kept = model.isKeep(channel)
        Text(
            text = if (kept) "★" else "☆",
            color = if (kept) UiTheme.Accent else UiTheme.TextSub,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable { model.toggleKeep(channel) }
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

/** 右列 EPG 节目单：当前节目高亮，已结束节目可回看 */
@Composable
private fun EpgPanel(model: LiveModel) {
    val channel = model.currentChannel
    val epg = model.epg
    Column(modifier = Modifier.width(340.dp).fillMaxHeight().background(Overlay).padding(12.dp)) {
        if (channel == null) {
            CenterMessage("点击频道查看节目单", showSpinner = false, modifier = Modifier.fillMaxSize())
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = channel.getShow(),
                color = UiTheme.TextMain,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (!channel.isOnly()) {
                ChipButton(text = "换线路 ${channel.getIndex() + 1}/${channel.getUrls().size}") { model.switchLine(true) }
            }
        }
        Text(text = "节目单", color = UiTheme.TextSub, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))
        val entries = epg?.getList().orEmpty()
        if (entries.isEmpty()) {
            Text(text = "暂无节目单数据", color = UiTheme.TextSub, fontSize = 13.sp)
            return
        }
        LazyColumn(state = rememberLazyListState(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(entries, key = { it.getStart() + it.getTitle() }) { data ->
                EpgRow(model, data)
            }
        }
    }
}

@Composable
private fun EpgRow(model: LiveModel, data: EpgData) {
    val current = data.isSelected || data.isInRange
    val future = data.isFuture
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (current) RowSelected.copy(alpha = 0.30f) else OverlayRow, RoundedCornerShape(4.dp))
            .clickable(enabled = !future) { model.catchup(data) }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(0.82f)) {
            Text(
                text = data.getTime(),
                color = when {
                    current -> UiTheme.Accent
                    future -> UiTheme.TextSub.copy(alpha = 0.5f)
                    else -> UiTheme.TextSub
                },
                fontSize = 12.sp
            )
            Text(
                text = data.getTitle(),
                color = when {
                    current -> UiTheme.Accent
                    future -> UiTheme.TextSub.copy(alpha = 0.5f)
                    else -> UiTheme.TextMain
                },
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!future) {
            Text(
                text = if (current) "直播中" else "回看",
                color = if (current) UiTheme.Accent else UiTheme.TextSub,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}
