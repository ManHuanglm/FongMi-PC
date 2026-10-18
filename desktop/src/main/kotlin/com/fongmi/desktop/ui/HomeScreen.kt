package com.fongmi.desktop.ui

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fongmi.android.tv.bean.Filter
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
    var bitmap by remember(model.wallUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(model.wallUrl) {
        bitmap = PosterLoader.load(model.wallUrl)
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            alpha = 0.25f
        )
    }
}

/**
 * 首页：布局对齐原版 VodFragment —— 顶栏（logo头像 + 站名点击换源 + 搜索/收藏/最近观看图标）
 * 分类 Tab 横滚 + 海报网格 + 右下角浮动筛选按钮（弹窗筛选，替代内联筛选条）
 */
@Composable
fun HomeScreen(
    model: AppModel,
    onOpenVod: (Vod) -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenKeep: () -> Unit = {},
    onPlayHistory: (com.fongmi.android.tv.bean.History) -> Unit = {},
) {
    var siteDialog by remember { mutableStateOf(false) }
    var filterDialog by remember { mutableStateOf(false) }
    var historyDialog by remember { mutableStateOf(false) }
    val filters = model.currentFilters()

    Box(modifier = Modifier.fillMaxSize().background(wallpaperBrush(model))) {
        WallpaperImage(model)
        Column(modifier = Modifier.fillMaxSize()) {
            HomeTopBar(model, onOpenSearch, onOpenKeep) { historyDialog = true }
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
                Box(modifier = Modifier.weight(1f)) {
                    PosterGrid(model, onOpenVod)
                    // 原版语义：当前分类含筛选时显示浮动筛选按钮
                    if (filters.isNotEmpty()) {
                        FilterFab(modifier = Modifier.align(Alignment.BottomEnd)) { filterDialog = true }
                    }
                }
            }
        }
    }

    if (siteDialog) SitePickerDialog(appModel = model, onDismiss = { siteDialog = false })
    if (filterDialog) FilterDialog(model, onDismiss = { filterDialog = false })
    if (historyDialog) HistoryDialog(onDismiss = { historyDialog = false }, onPlay = onPlayHistory)
}

/** 顶栏：logo 头像（配置 logo 或站名首字）+ 站名（点击换源）+ 搜索/收藏/最近观看图标（对齐原版 VodFragment 顶栏） */
@Composable
private fun HomeTopBar(model: AppModel, onOpenSearch: () -> Unit, onOpenKeep: () -> Unit, onOpenHistory: () -> Unit) {
    var siteDialog by remember { mutableStateOf(false) }
    val logoUrl = remember(model.siteName) { runCatching { com.fongmi.android.tv.api.config.VodConfig.get().getConfig().getLogo().orEmpty() }.getOrDefault("") }
    Row(
        modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoAvatar(logoUrl, model.siteName) { siteDialog = true }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = model.siteName.ifEmpty { "FongMi TV PC" },
            color = TextMain,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { siteDialog = true }
        )
        Spacer(modifier = Modifier.weight(1f))
        if (model.loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
        }
        ChipButton(text = "最近观看") { onOpenHistory() }
        Spacer(modifier = Modifier.width(8.dp))
        ChipButton(text = "收藏") { onOpenKeep() }
        Spacer(modifier = Modifier.width(8.dp))
        ChipButton(text = "搜索", selected = true) { onOpenSearch() }
    }
    if (siteDialog) SitePickerDialog(appModel = model, onDismiss = { siteDialog = false })
}

/** 圆形 logo 头像（原版顶部 logo 位；无图显示站名首字） */
@Composable
private fun LogoAvatar(logoUrl: String, siteName: String, onClick: () -> Unit) {
    var bitmap by remember(logoUrl) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(logoUrl) {
        bitmap = if (logoUrl.startsWith("http")) PosterLoader.load(logoUrl) else null
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (bitmap == null) Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        } else {
            Text(text = siteName.take(1).ifEmpty { "F" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CategoryTabs(model: AppModel) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(model.types.size) { index ->
            val selected = index == model.activeIndex
            Box(
                modifier = Modifier
                    .background(if (selected) Accent else Chip, RoundedCornerShape(8.dp))
                    .clickable { model.selectType(index) }
                    .padding(horizontal = 16.dp, vertical = 7.dp)
            ) {
                Text(text = model.types[index].getTypeName(), color = if (selected) Color.White else TextSub, fontSize = 14.sp, maxLines = 1)
            }
        }
    }
}

/** 右下角浮动筛选按钮（原版 FAB 语义） */
@Composable
private fun FilterFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .padding(20.dp)
            .background(Accent, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(text = "筛选", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/** 筛选弹窗（原版 FilterDialog 语义：分组值胶囊 + 清除筛选） */
@Composable
private fun FilterDialog(model: AppModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(640.dp)
                .background(Panel, RoundedCornerShape(10.dp))
                .border(1.dp, Chip, RoundedCornerShape(10.dp))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "筛选", color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = "清除筛选", color = Accent, fontSize = 13.sp, modifier = Modifier.clickable { model.clearFilters() }.padding(4.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).heightIn(max = 420.dp)) {
                model.currentFilters().forEach { filter ->
                    FilterGroup(model, filter) { onDismiss() }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                ChipButton(text = "完成", selected = true) { onDismiss() }
            }
        }
    }
}

@Composable
private fun FilterGroup(model: AppModel, filter: Filter, onSelected: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(text = filter.getName(), color = TextSub, fontSize = 13.sp, modifier = Modifier.width(64.dp).padding(top = 7.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filter.getValue().forEach { value ->
                val selected = value.getV() == model.selectedValue(filter.getKey())
                Box(
                    modifier = Modifier
                        .background(if (selected) Accent else Chip, RoundedCornerShape(6.dp))
                        .clickable {
                            model.applyFilter(filter.getKey(), value.getV())
                            onSelected()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(text = value.getN(), color = if (selected) Color.White else TextSub, fontSize = 13.sp, maxLines = 1)
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 72.dp)
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
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).border(1.dp, Chip, RoundedCornerShape(8.dp))
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = vod.getName(), color = TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = vod.getRemarks(), color = TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
