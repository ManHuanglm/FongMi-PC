package com.fongmi.desktop.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.catvod.net.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image

/**
 * 桌面端图片加载（OkHttp 拉流 + Skia 解码，支持 jpeg/png/webp/gif），海报缺省用文字占位
 */
object PosterLoader {

    suspend fun load(url: String): ImageBitmap? = withContext(Dispatchers.IO) {
        if (url.isEmpty()) return@withContext null
        runCatching {
            OkHttp.newCall(url).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val bytes = response.body?.bytes() ?: return@runCatching null
                Image.makeFromEncoded(bytes).toComposeImageBitmap()
            }
        }.getOrNull()
    }
}

/** 海报组件：加载前显示占位底色，失败显示首字 */
@Composable
fun PosterImage(url: String, name: String, modifier: Modifier = Modifier) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        if (url.isEmpty()) {
            failed = true
            return@LaunchedEffect
        }
        bitmap = PosterLoader.load(url)
        if (bitmap == null) failed = true
    }

    Box(modifier = modifier.background(Color(0xFF1B2029)), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(bitmap = image, contentDescription = name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (failed) {
            Text(
                text = if (name.isEmpty()) "无图" else name.take(2),
                color = Color(0xFF5A6472),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(8.dp)
            )
        }
    }
}
