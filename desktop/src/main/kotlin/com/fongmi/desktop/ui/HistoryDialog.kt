package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fongmi.android.tv.bean.History
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 最近观看弹窗（原版 VodFragment 顶栏历史入口语义）：
 * 当前配置下 HISTORY_TIME 窗口内的观看记录，点击按记录的键续播
 */
@Composable
fun HistoryDialog(onDismiss: () -> Unit, onPlay: (History) -> Unit) {
    var items by remember { mutableStateOf(runCatching { History.get() }.getOrDefault(emptyList())) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .background(UiTheme.Panel, RoundedCornerShape(10.dp))
                .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "最近观看", color = UiTheme.TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "共 ${items.size} 条", color = UiTheme.TextSub, fontSize = 12.sp)
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (items.isEmpty()) {
                Text(text = "暂无观看记录\n播放影片后会自动记录断点", color = UiTheme.TextSub, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(vertical = 16.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(items, key = { it.getKey() }) { history ->
                        HistoryRow(history) {
                            onPlay(history)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(history: History, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(UiTheme.Bg, RoundedCornerShape(6.dp))
            .border(1.dp, UiTheme.ChipBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PosterImage(
            url = history.getVodPic().orEmpty(),
            name = history.getVodName().orEmpty(),
            modifier = Modifier.width(42.dp).height(58.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = history.getVodName().orEmpty(), color = UiTheme.TextMain, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = historyLine(history), color = UiTheme.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = relativeTime(history.getCreateTime()), color = UiTheme.TextSub, fontSize = 11.sp)
    }
}

/** 副标题：集名 + 观看进度 */
private fun historyLine(history: History): String {
    val parts = mutableListOf<String>()
    if (history.getVodRemarks().orEmpty().isNotEmpty()) parts.add(history.getVodRemarks())
    val duration = history.getDuration()
    val position = history.getPosition()
    if (duration > 0 && position > 0) parts.add("看到 ${(position * 100 / duration).coerceIn(0, 100)}%")
    return parts.joinToString(" · ").ifEmpty { "继续观看" }
}

/** 相对时间（x分钟前 / x小时前 / x天前 / 日期） */
private fun relativeTime(createTime: Long): String {
    if (createTime <= 0) return ""
    val diff = System.currentTimeMillis() - createTime
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes}分钟前"
        minutes < 1440 -> "${minutes / 60}小时前"
        minutes < 43200 -> "${minutes / 1440}天前"
        else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(createTime))
    }
}
