package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.api.SiteApi
import com.fongmi.android.tv.bean.Episode
import com.fongmi.android.tv.bean.Flag
import com.fongmi.android.tv.bean.Vod
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val Bg = Color(0xFF10141A)
private val Panel = Color(0xFF161B24)
private val Chip = Color(0xFF232B38)
private val Accent = Color(0xFF3D8BFF)
private val TextMain = Color(0xFFE8ECF2)
private val TextSub = Color(0xFF98A2B0)

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * 详情页：海报/信息/简介/选集列表；选集点击进入播放（M2 libmpv 链路）
 */
@Composable
fun DetailScreen(siteKey: String, vodId: String, onBack: () -> Unit, onPlay: (String, String, String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var vod by remember { mutableStateOf<Vod?>(null) }

    LaunchedEffect(siteKey, vodId) {
        scope.launch {
            try {
                val result = SiteApi.detailContent(siteKey, vodId)
                vod = result.getVod()
                loading = false
            } catch (e: Exception) {
                error = e.message ?: e.toString()
                loading = false
                e.printStackTrace()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Bg)) {
        DetailTopBar(onBack)
        when {
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
            error.isNotEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "加载失败：$error", color = Color(0xFFE06A6A), fontSize = 14.sp)
            }
            else -> vod?.let { DetailContent(it, onPlay) }
        }
    }
}

@Composable
private fun DetailTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.background(Chip, RoundedCornerShape(6.dp)).clickable(onClick = onBack).padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(text = "← 返回", color = TextMain, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "详情", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DetailContent(vod: Vod, onPlay: (String, String, String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row {
            PosterImage(
                url = vod.getPic(),
                name = vod.getName(),
                modifier = Modifier.width(170.dp).height(236.dp).border(1.dp, Chip, RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = vod.getName(), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                if (vod.getRemarks().isNotEmpty()) InfoLine(label = "备注", value = vod.getRemarks())
                if (vod.getYear().isNotEmpty()) InfoLine(label = "年份", value = vod.getYear())
                if (vod.getArea().isNotEmpty()) InfoLine(label = "地区", value = vod.getArea())
                if (vod.getTypeName().isNotEmpty()) InfoLine(label = "类型", value = vod.getTypeName())
                if (vod.getActor().isNotEmpty()) InfoLine(label = "演员", value = vod.getActor(), maxLines = 3)
                if (vod.getDirector().isNotEmpty()) InfoLine(label = "导演", value = vod.getDirector(), maxLines = 2)
            }
        }
        if (vod.getContent().isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(text = "简介", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = vod.getContent(), color = TextSub, fontSize = 13.sp, lineHeight = 20.sp)
        }
        val flags = vod.getFlags()
        flags.forEach { flag -> EpisodeGroup(flag, onPlay) }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun InfoLine(label: String, value: String, maxLines: Int = 1) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(text = label, color = TextSub, fontSize = 13.sp, modifier = Modifier.width(44.dp))
        Text(text = value, color = TextMain, fontSize = 13.sp, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EpisodeGroup(flag: Flag, onPlay: (String, String, String) -> Unit) {
    Spacer(modifier = Modifier.height(20.dp))
    Text(text = flag.getShow(), color = Accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(8.dp))
    val episodes = flag.getEpisodes()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        episodes.chunked(6).forEach { chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                chunk.forEach { episode -> EpisodeChip(episode) { onPlay(flag.getFlag(), episode.getName(), episode.getUrl()) } }
            }
        }
    }
}

@Composable
private fun EpisodeChip(episode: Episode, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(Chip, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2E3745), RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(text = episode.getName(), color = TextMain, fontSize = 12.sp, maxLines = 1)
    }
}
