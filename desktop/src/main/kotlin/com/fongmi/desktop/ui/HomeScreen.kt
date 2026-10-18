package com.fongmi.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.bean.Vod

private val Bg = Color(0xFF10141A)
private val Panel = Color(0xFF161B24)
private val Chip = Color(0xFF232B38)
private val Accent get() = UiTheme.Accent
private val TextMain = Color(0xFFE8ECF2)
private val TextSub = Color(0xFF98A2B0)

/** 壁纸背景：wall 0=自定义图片配置（含降级纯色），1-4=内置深色渐变预设，与原版设置页壁纸轮换对应 */
@Composable
private fun wallpaperBrush(model: AppModel): androidx.compose.ui.graphics.Brush = when (model.wall) {
    1 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1A2332), Color(0xFF0C111B)))
    2 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF241A32), Color(0xFF0F0B18)))
    3 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF14262B), Color(0xFF0A1214)))
    4 -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1F2B1E), Color(0xFF0D130C)))
    else -> androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Bg, Bg))
}

/** 自定义壁纸图片（wall=0 且配置含 wallpaper 地址时铺底，低透明度避免干扰内容） */
@Composable
private fun WallpaperImage(model: AppModel) {
    if (model.wall != 0 || model.wallUrl.isEmpty()) return
    var bitmap by remember(model.wallUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    androidx.compose.runtime.LaunchedEffect(model.wallUrl) {
        bitmap = PosterLoader.load(model.wallUrl)
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.25f
        )
    }
}

/** 首页：源切换 + 分类 Tab + 筛选条 + 影片网格 + 无限分页；背景支持壁纸预设/自定义图片 */
@Composable
fun HomeScreen(model: AppModel, onOpenVod: (Vod) -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(wallpaperBrush(model))) {
        WallpaperImage(model)
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(model)
        if (model.notice.isNotEmpty()) {
            Text(
                text = model.notice,
                color = Color(0xFFD9A44A),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().background(Color(0xFF2A2418)).padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }
        when {
            model.loading && model.types.isEmpty() -> CenterMessage(model.message, showSpinner = true)
            model.error.isNotEmpty() && model.types.isEmpty() -> CenterMessage("加载失败：${model.error}", showSpinner = false)
            model.types.isNotEmpty() -> CategoryTabs(model)
        }
        if (model.types.isNotEmpty()) {
            FilterBar(model)
            PosterGrid(model, onOpenVod)
        }
        }
    }
}

@Composable
private fun TopBar(model: AppModel) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "FongMi TV PC", color = Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.width(24.dp))
        Box {
            Row(
                modifier = Modifier.clickable { expanded = true }.background(Chip, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = model.siteName.ifEmpty { "选择源" }, color = TextMain, fontSize = 14.sp, maxLines = 1)
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "▾", color = TextSub, fontSize = 12.sp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                model.sites.forEach { site ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = site.getName(),
                                color = if (site.getKey() == model.homeSite().getKey()) Accent else TextMain,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            expanded = false
                            model.changeSite(site)
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (model.loading) CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), color = Accent, strokeWidth = 2.dp)
    }
}

@Composable
private fun CategoryTabs(model: AppModel) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().background(Bg).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(model.types.size) { index ->
            val selected = index == model.activeIndex
            Box(
                modifier = Modifier
                    .background(if (selected) Accent else Chip, RoundedCornerShape(6.dp))
                    .clickable { model.selectType(index) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(text = model.types[index].getTypeName(), color = if (selected) Color.White else TextSub, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FilterBar(model: AppModel) {
    val filters = model.currentFilters()
    if (filters.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 12.dp, vertical = 6.dp).heightIn(max = 150.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(text = "筛选", color = TextSub, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(text = "清除筛选", color = Accent, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp).clickable { model.clearFilters() })
        }
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            filters.forEach { filter ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = filter.getName(), color = TextSub, fontSize = 12.sp, modifier = Modifier.width(64.dp), maxLines = 1)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        filter.getValue().forEach { value ->
                            val selected = value.getV() == model.selectedValue(filter.getKey())
                            Box(
                                modifier = Modifier
                                    .background(if (selected) Accent else Chip, RoundedCornerShape(4.dp))
                                    .clickable { model.applyFilter(filter.getKey(), value.getV()) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(text = value.getN(), color = if (selected) Color.White else TextSub, fontSize = 12.sp, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterGrid(model: AppModel, onOpenVod: (Vod) -> Unit) {
    val gridState = rememberLazyGridState()
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 12
        }
    }
    LaunchedEffect(nearEnd) {
        if (nearEnd && model.vods.isNotEmpty()) model.loadMore()
    }

    if (model.vods.isEmpty() && !model.loading) {
        CenterMessage(model.error.ifEmpty { "暂无内容" }, showSpinner = false)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(model.posterWidthDp().dp),
        state = gridState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(model.vods, key = { it.getId() + it.getName() }) { vod ->
            PosterCard(vod) { onOpenVod(vod) }
        }
        if (model.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), color = Accent, strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun PosterCard(vod: Vod, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        PosterImage(
            url = vod.getPic(),
            name = vod.getName(),
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).border(1.dp, Color(0xFF232B38), RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = vod.getName(), color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = vod.getRemarks(), color = TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CenterMessage(message: String, showSpinner: Boolean) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showSpinner) {
                CircularProgressIndicator(color = Accent)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(text = message, color = TextSub, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}
