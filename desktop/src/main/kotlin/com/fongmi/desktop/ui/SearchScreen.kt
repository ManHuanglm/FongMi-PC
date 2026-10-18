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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fongmi.android.tv.bean.Vod

/** 搜索页：关键词/热词/联想/历史 + 并行聚合结果网格 */
@Composable
fun SearchScreen(model: SearchModel, onOpenVod: (Vod) -> Unit) {
    DisposableEffect(Unit) {
        onDispose { model.stopSearch() }
    }

    Column(modifier = Modifier.fillMaxSize().background(UiTheme.Bg)) {
        SearchBar(model)
        WordChips(model)
        SearchStatus(model)
        SearchResultGrid(model, onOpenVod)
    }
}

@Composable
private fun SearchBar(model: SearchModel) {
    Row(
        modifier = Modifier.fillMaxWidth().background(UiTheme.Panel).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "搜索", color = UiTheme.Accent, fontSize = 18.sp)
        Spacer(modifier = Modifier.width(16.dp))
        TextField(
            value = model.keyword,
            onValueChange = {
                model.keyword = it
                model.suggest(it)
            },
            placeholder = { Text(text = "输入影片名称，回车聚合搜索", color = UiTheme.TextSub, fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { model.search(model.keyword) }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = UiTheme.TextMain,
                unfocusedTextColor = UiTheme.TextMain,
                focusedContainerColor = UiTheme.Chip,
                unfocusedContainerColor = UiTheme.Chip,
                cursorColor = UiTheme.Accent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier.weight(1f).height(48.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        ChipButton(text = "搜索", selected = true) { model.search(model.keyword) }
        if (model.history.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            ChipButton(text = "清历史") { model.clearHistory() }
        }
    }
}

@Composable
private fun WordChips(model: SearchModel) {
    val suggestions = model.suggestWords
    val words = when {
        suggestions.isNotEmpty() -> suggestions
        model.hotWords.isNotEmpty() -> model.hotWords
        else -> emptyList()
    }
    if (words.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = if (suggestions.isNotEmpty()) "联想" else "热门搜索",
            color = UiTheme.TextSub,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            words.take(12).forEach { word ->
                ChipButton(text = word) {
                    model.keyword = word
                    model.search(word)
                }
            }
        }
    }
}

@Composable
private fun SearchStatus(model: SearchModel) {
    if (model.totalSites == 0 && !model.searching) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (model.searching) {
            CircularProgressIndicator(modifier = Modifier.width(14.dp).height(14.dp), color = UiTheme.Accent, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = if (model.searching) "正在搜索 ${model.finished}/${model.totalSites} 个站点..." else "共 ${model.results.size} 条结果",
            color = UiTheme.TextSub,
            fontSize = 12.sp
        )
        if (model.error.isNotEmpty()) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = model.error, color = UiTheme.Error, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SearchResultGrid(model: SearchModel, onOpenVod: (Vod) -> Unit) {
    if (model.results.isEmpty()) {
        val message = when {
            model.keyword.isEmpty() -> "输入关键词开始搜索"
            model.searching -> "正在聚合各站点结果..."
            model.error.isNotEmpty() -> "搜索失败：${model.error}"
            else -> "未找到相关影片"
        }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = message, color = UiTheme.TextSub, fontSize = 14.sp)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = rememberLazyGridState(),
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(model.results, key = { it.getSiteKey() + it.getId() + it.getName() }) { vod ->
            Column(modifier = Modifier.clickable { onOpenVod(vod) }) {
                PosterImage(
                    url = vod.getPic(),
                    name = vod.getName(),
                    modifier = Modifier.fillMaxWidth().aspectRatio(0.72f).border(1.dp, UiTheme.Chip, RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = vod.getName(), color = UiTheme.TextMain, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = vod.getSiteName(), color = UiTheme.TextSub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
