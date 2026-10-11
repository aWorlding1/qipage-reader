package local.readapp.feature

import local.readapp.core.T

import android.media.MediaPlayer
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.readapp.core.*
import java.io.File

/** 按需解码一张素材图片（用于章首插图），限制最大边以控制内存。 */
internal fun decodeIllustration(file:File,maxDim:Int=1200):android.graphics.Bitmap? {
    val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
    android.graphics.BitmapFactory.decodeFile(file.path,bounds)
    if(bounds.outWidth<=0||bounds.outHeight<=0)return null
    var sample=1
    while(bounds.outWidth/sample>maxDim||bounds.outHeight/sample>maxDim)sample*=2
    return android.graphics.BitmapFactory.decodeFile(file.path,android.graphics.BitmapFactory.Options().apply{inSampleSize=sample})
}

private fun kindLabel(kind: AssetKind) = when (kind) {
    AssetKind.Text -> T("正文 · 文本")
    AssetKind.Image -> T("图片 · 封面与插画")
    AssetKind.Video -> T("视频")
    AssetKind.Audio -> T("音频 · 配音与配乐")
}

private fun sizeText(bytes: Long) = when {
    bytes <= 0L -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "%.1f MB".format(bytes / 1048576.0)
}

private fun iconOf(kind: AssetKind) = when (kind) {
    AssetKind.Text -> "type"
    AssetKind.Image -> "image"
    AssetKind.Video -> "video"
    AssetKind.Audio -> "sound"
}

/** 一本书的「书斋图鉴」：正文 + 封面 + 图片 + 视频 + 音频，可预览、可下载。 */
@Composable
internal fun BookAssetsPanel(book: Book, repository: BookRepository, close: () -> Unit) {
    val scope = rememberCoroutineScope()
    val assets by produceState<List<BookAsset>?>(null, book.id) {
        value = withContext(Dispatchers.IO) { runCatching { repository.assets(book.id) }.getOrDefault(emptyList()) }
    }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<BookAsset?>(null) }
    var busy by remember { mutableStateOf(false) }
    val download: (BookAsset) -> Unit = { asset ->
        scope.launch {
            busy = true
            message = runCatching { T("已保存 %1\$s 到 Download").format(repository.exportAsset(book.id, asset.name)) }.getOrElse { it.message ?: T("保存失败") }
            busy = false
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlyphButton("back", T("返回阅读"), close)
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(T("书斋图鉴"), fontFamily = FontFamily.Serif, fontSize = 23.sp)
                        Text(book.title, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(
                        enabled = !busy && !assets.isNullOrEmpty(),
                        onClick = {
                            busy = true
                            scope.launch {
                                message = runCatching { repository.exportAll(book.id) }.getOrElse { it.message ?: T("保存失败") }
                                busy = false
                            }
                        }
                    ) { Text(T("全部下载")) }
                }
                val list = assets
                when {
                    list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    list.isEmpty() -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        PaperIcon("book", Modifier.size(44.dp), PaperGold)
                        Spacer(Modifier.height(18.dp))
                        Text(T("这本书还没有随附素材"), fontFamily = FontFamily.Serif, fontSize = 18.sp)
                        Text(T("在书文件夹里放入图片 / 视频 / 音频即可在此展开"), Modifier.padding(top = 10.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize().padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 40.dp)
                    ) {
                        AssetKind.entries.forEach { kind ->
                            val group = list.filter { it.kind == kind }
                            if (group.isNotEmpty()) {
                                item(key = "head-${kind.name}") {
                                    Text(
                                        "${kindLabel(kind)} · ${group.size}",
                                        Modifier.padding(top = 14.dp, bottom = 2.dp),
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 16.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (kind == AssetKind.Image) {
                                    item(key = "wall-image") {
                                        ImageWall(group, book.id, repository, { preview = it }, download)
                                    }
                                } else {
                                    items(group, key = { it.name }) { asset ->
                                        AssetRow(asset = asset, onPreview = { preview = asset }, onDownload = { download(asset) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    preview?.let { asset ->
        when (asset.kind) {
            AssetKind.Image -> ImagePreview(book, asset, repository) { preview = null }
            AssetKind.Video -> VideoPreview(book, asset, repository) { preview = null }
            AssetKind.Audio -> AudioPreview(book, asset, repository) { preview = null }
            else -> preview = null
        }
    }
    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            title = { Text(T("保存到 Download"), fontFamily = FontFamily.Serif) },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text(T("知道了")) } }
        )
    }
}

@Composable
private fun AssetRow(asset: BookAsset, onPreview: () -> Unit, onDownload: () -> Unit) {
    Surface(
        onClick = onPreview,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PaperIcon(iconOf(asset.kind), Modifier.size(24.dp), PaperGold)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(asset.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                Text(sizeText(asset.bytes), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDownload) { PaperIcon("download", Modifier.size(22.dp)) }
        }
    }
}

/** 图片以缩略图墙呈现：点开看大图，右上角一键存到 Download。 */
@Composable
private fun ImageWall(items: List<BookAsset>, bookId: String, repository: BookRepository, onPreview: (BookAsset) -> Unit, onDownload: (BookAsset) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { asset ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))) {
                        val thumb = assetBitmap(bookId, asset.name, repository, maxDim = 320)
                        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer))
                        Box(Modifier.fillMaxSize().clickable { onPreview(asset) })
                        Surface(
                            onClick = { onDownload(asset) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = .38f),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp)
                        ) { Box(contentAlignment = Alignment.Center) { PaperIcon("download", Modifier.size(15.dp), Color.White) } }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun assetBitmap(bookId: String, name: String, repository: BookRepository, maxDim: Int = 1600): ImageBitmap? {
    val bitmap by produceState<android.graphics.Bitmap?>(null, bookId, name) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val path = repository.materializeAsset(bookId, name)
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
                android.graphics.BitmapFactory.decodeFile(path, android.graphics.BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    return bitmap?.asImageBitmap()
}

@Composable
private fun ImagePreview(book: Book, asset: BookAsset, repository: BookRepository, close: () -> Unit) {
    val bitmap = assetBitmap(book.id, asset.name, repository)
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { close() }, contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            else CircularProgressIndicator(color = Color.White)
            Text(
                asset.name,
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                color = Color.White.copy(alpha = .72f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun VideoPreview(book: Book, asset: BookAsset, repository: BookRepository, close: () -> Unit) {
    val path by produceState<String?>(null, book.id, asset.name) {
        value = withContext(Dispatchers.IO) { runCatching { repository.materializeAsset(book.id, asset.name) }.getOrNull() }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            val local = path
            if (local == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
            } else {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setVideoPath(local)
                            setOnPreparedListener { it.isLooping = false; start() }
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            GlyphButton("close", T("关闭")) { close() }
            Text(
                asset.name,
                Modifier.align(Alignment.BottomCenter).padding(24.dp),
                color = Color.White.copy(alpha = .72f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun AudioPreview(book: Book, asset: BookAsset, repository: BookRepository, close: () -> Unit) {
    val path by produceState<String?>(null, book.id, asset.name) {
        value = withContext(Dispatchers.IO) { runCatching { repository.materializeAsset(book.id, asset.name) }.getOrNull() }
    }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var position by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { runCatching { player?.release() }; player = null }
    }
    LaunchedEffect(path) {
        val local = path ?: return@LaunchedEffect
        runCatching {
            val mp = MediaPlayer()
            mp.setDataSource(local)
            mp.prepare()
            mp.setOnCompletionListener { playing = false }
            duration = mp.duration.coerceAtLeast(0)
            player = mp
        }.onFailure { error = it.message ?: T("无法播放这段音频") }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
            delay(500)
        }
    }

    Dialog(onDismissRequest = { runCatching { player?.pause() }; close() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PaperIcon("sound", Modifier.size(56.dp), PaperGold)
                Spacer(Modifier.height(22.dp))
                Text(asset.name, fontFamily = FontFamily.Serif, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(28.dp))
                val span = duration.coerceAtLeast(1).toFloat()
                Slider(
                    value = position.coerceIn(0, duration).toFloat(),
                    onValueChange = { value ->
                        position = value.toInt()
                        runCatching { player?.seekTo(position) }
                    },
                    valueRange = 0f..span,
                    enabled = duration > 0
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(position), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatTime(duration), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(20.dp))
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        FilledIconButton(onClick = { runCatching { player?.seekTo((position - 10000).coerceAtLeast(0)) } }) { PaperIcon("back", Modifier.size(22.dp)) }
                        FilledTonalIconButton(
                            onClick = {
                                val mp = player ?: return@FilledTonalIconButton
                                if (playing) runCatching { mp.pause() } else runCatching { mp.start() }
                                playing = !playing
                            },
                            modifier = Modifier.size(64.dp)
                        ) { PaperIcon(if (playing) "pause" else "play", Modifier.size(28.dp)) }
                        FilledIconButton(onClick = { runCatching { player?.seekTo((position + 10000).coerceAtMost(duration)) } }) { PaperIcon("play", Modifier.size(22.dp)) }
                    }
                }
                Spacer(Modifier.height(30.dp))
                TextButton(onClick = { runCatching { player?.pause() }; close() }) { Text(T("关闭")) }
            }
        }
    }
}

private fun formatTime(ms: Int): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}
