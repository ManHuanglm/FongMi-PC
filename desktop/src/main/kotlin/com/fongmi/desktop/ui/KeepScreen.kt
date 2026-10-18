package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import com.fongmi.android.tv.bean.Keep

/** 点播收藏夹：Keep 表 type=0 记录（与安卓收藏互通），点击进详情，支持取消收藏 */
@Composable
fun KeepScreen(onOpenVod: (siteKey: String, vodId: String) -> Unit) {
    var version by remember { mutableStateOf(0) }
    val keeps = remember(version) { Keep.getVod() }

    Column(modifier = Modifier.fillMaxSize().background(UiTheme.Bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(UiTheme.Panel).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "收藏", color = UiTheme.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = "共 ${keeps.size} 部", color = UiTheme.TextSub, fontSize = 13.sp)
        }
        if (keeps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "暂无收藏\n在详情页点击收藏按钮即可添加", color = UiTheme.TextSub, fontSize = 14.sp, lineHeight = 22.sp)
            }
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = rememberLazyGridState(),
            modifier = Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(keeps, key = { it.getKey() }) { keep ->
                KeepCard(keep, onOpenVod) {
                    keep.delete()
                    version += 1
                }
            }
        }
    }
}

@Composable
private fun KeepCard(keep: Keep, onOpenVod: (String, String) -> Unit, onRemove: () -> Unit) {
    Column(modifier = Modifier.clickable { onOpenVod(keep.getSiteKey(), keep.getVodId()) }) {
        PosterImage(
            url = keep.getVodPic(),
            name = keep.getVodName(),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).border(1.dp, UiTheme.Chip, RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = keep.getVodName(), color = UiTheme.TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = keep.getSiteName(), color = UiTheme.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(UiTheme.Chip, RoundedCornerShape(4.dp))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(text = "取消收藏", color = UiTheme.Error, fontSize = 11.sp)
            }
        }
    }
}
