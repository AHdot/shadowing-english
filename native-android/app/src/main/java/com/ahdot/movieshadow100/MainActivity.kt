package com.ahdot.movieshadow100

import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MovieShadowScreen(
                onFullscreen = { enabled ->
                    requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    val bars = WindowInsetsControllerCompat(window, window.decorView)
                    if (enabled) {
                        bars.hide(WindowInsetsCompat.Type.systemBars())
                        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else bars.show(WindowInsetsCompat.Type.systemBars())
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MovieShadowScreen(onFullscreen: (Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build().apply { repeatMode = Player.REPEAT_MODE_OFF } }
    val playbackStore = remember { PlaybackStore(context) }
    DisposableEffect(player) { onDispose { playbackStore.savePosition(player.currentPosition); player.release() } }

    var videoName by remember { mutableStateOf(playbackStore.videoName()) }
    var speed by remember { mutableFloatStateOf(1f) }
    var chineseOffsetText by remember { mutableStateOf("0") }
    var englishName by remember { mutableStateOf("") }
    var chineseName by remember { mutableStateOf("") }
    var englishCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var chineseCues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var cues by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var importLanguage by remember { mutableStateOf("en") }
    var fullscreen by remember { mutableStateOf(false) }
    var listVisible by remember { mutableStateOf(true) }
    var showEnglish by remember { mutableStateOf(true) }
    var showChinese by remember { mutableStateOf(true) }
    var loopA by remember { mutableStateOf<Long?>(null) }
    var loopB by remember { mutableStateOf<Long?>(null) }
    var loopEnabled by remember { mutableStateOf(false) }
    var sentenceRepeatCue by remember { mutableStateOf<SubtitleCue?>(null) }
    var timerText by remember { mutableStateOf("30") }
    var timerEnd by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("导入本地电影和字幕即可开始") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "本地电影"
            playbackStore.saveVideo(uri.toString(), videoName)
            playbackStore.savePosition(0L)
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            message = "电影已导入"
        }.onFailure { message = "电影导入失败：${it.message}" }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取字幕文件")
            val parsed = SubtitleParser.parseSingleLanguage(raw)
            if (parsed.isEmpty()) error("没有识别到字幕，请检查文件格式")
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "字幕文件"
            if (importLanguage == "zh") {
                chineseCues = parsed
                chineseName = name
                playbackStore.saveSubtitle("zh", uri.toString(), name)
                message = "中文字幕导入成功：${parsed.size} 条"
            } else {
                englishCues = parsed
                englishName = name
                playbackStore.saveSubtitle("en", uri.toString(), name)
                message = "英文字幕导入成功：${parsed.size} 条"
            }
            cues = when {
                englishCues.isNotEmpty() && chineseCues.isNotEmpty() -> SubtitleMerger.merge(englishCues, chineseCues)
                englishCues.isNotEmpty() -> englishCues
                else -> chineseCues.map { SubtitleCue(it.startMs, it.endMs, "", it.english) }
            }
        }.onFailure { message = "字幕导入失败：${it.message}" }
    }

    LaunchedEffect(player) {
        // Restore local media references and last playback checkpoint.
        val savedVideo = playbackStore.videoUri()
        if (savedVideo != null) runCatching {
            val uri = Uri.parse(savedVideo)
            player.setMediaItem(MediaItem.fromUri(uri), playbackStore.positionMs())
            player.prepare()
            player.playWhenReady = false
            message = "已恢复上次电影和播放进度"
        }.onFailure { message = "上次电影无法访问，请重新导入" }
        suspend fun restoreSubtitle(language: String): List<SubtitleCue> {
            val saved = playbackStore.subtitleUri(language) ?: return emptyList()
            return runCatching {
                val uri = Uri.parse(saved)
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("无法读取字幕")
                SubtitleParser.parseSingleLanguage(raw)
            }.getOrElse { emptyList() }
        }
        englishCues = restoreSubtitle("en")
        chineseCues = restoreSubtitle("zh")
        englishName = playbackStore.subtitleName("en")
        chineseName = playbackStore.subtitleName("zh")
        cues = when {
            englishCues.isNotEmpty() && chineseCues.isNotEmpty() -> SubtitleMerger.merge(englishCues, chineseCues)
            englishCues.isNotEmpty() -> englishCues
            else -> chineseCues.map { SubtitleCue(it.startMs, it.endMs, "", it.english) }
        }
        var lastCheckpoint = playbackStore.positionMs()
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            if (kotlin.math.abs(position - lastCheckpoint) >= 3000L) {
                playbackStore.savePosition(position)
                lastCheckpoint = position
            }
            val a = loopA
            val b = loopB
            if (loopEnabled && a != null && b != null && b > a && position >= b) {
                // Preserve pause state: seeking to A must not unexpectedly resume playback.
                player.seekTo(a)
            }
            val repeatCue = sentenceRepeatCue
            if (repeatCue != null && player.isPlaying && position >= repeatCue.endMs) {
                player.seekTo(repeatCue.startMs)
            }
            val end = timerEnd
            if (end != null && System.currentTimeMillis() >= end) {
                player.pause()
                timerEnd = null
                message = "定时暂停已执行"
            }
            delay(120)
        }
    }

    val activeIndex = cues.indexOfLast { position >= it.startMs && position <= it.endMs }
    val activeCue = cues.getOrNull(activeIndex)
    val sentenceListState = rememberLazyListState()
    LaunchedEffect(activeIndex, listVisible) {
        if (listVisible && activeIndex >= 0) sentenceListState.animateScrollToItem(activeIndex)
    }
    BackHandler(enabled = fullscreen) {
        fullscreen = false
        onFullscreen(false)
    }

    MaterialTheme(colorScheme = darkColorScheme(
        background = Color(0xFF101114), surface = Color(0xFF191B20), primary = Color(0xFF8AB4F8)
    )) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
            Column(Modifier.fillMaxSize().padding(if (fullscreen) PaddingValues(0.dp) else insets)) {
                if (!fullscreen) {
                    TopAppBar(
                        title = { Column {
                            Text("影语100", fontWeight = FontWeight.Bold)
                            Text("一部电影，反复学习100天", fontSize = 11.sp, color = Color.Gray)
                        } },
                        actions = {
                            TextButton(onClick = { videoPicker.launch(arrayOf("video/*")) }) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                                Text("电影")
                            }
                            TextButton(onClick = { importLanguage = "en"; subtitlePicker.launch(arrayOf("text/*", "*/*")) }) { Text("字幕 EN") }
                            TextButton(onClick = { importLanguage = "zh"; subtitlePicker.launch(arrayOf("text/*", "*/*")) }) { Text("字幕 中") }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF101114))
                    )
                }
                Box(
                    Modifier.fillMaxWidth()
                        .then(if (fullscreen) Modifier.weight(1f) else Modifier.heightIn(min = 210.dp, max = 280.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { PlayerView(it).apply {
                            this.player = player
                            useController = true
                            controllerAutoShow = true
                            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        } },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                    if (activeCue != null) {
                        Column(
                            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                                .background(Color(0xAA000000)).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (showEnglish && activeCue.english.isNotBlank())
                                Text(activeCue.english, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            if (showChinese && !activeCue.chinese.isNullOrBlank())
                                Text(activeCue.chinese!!, color = Color(0xFFE8E8E8), fontSize = 14.sp)
                        }
                    }
                    if (fullscreen) {
                        TextButton(
                            onClick = { fullscreen = false; onFullscreen(false) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                                .background(Color(0x99000000), shape = MaterialTheme.shapes.medium)
                        ) { Text("退出全屏", color = Color.White) }
                    }
                }
                if (!fullscreen) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(videoName, fontWeight = FontWeight.Bold, maxLines = 1)
                                Text(listOf(englishName, chineseName).filter { it.isNotBlank() }.joinToString(" + ").ifBlank { "尚未导入字幕" },
                                    color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                            }
                            TextButton(onClick = { fullscreen = true; onFullscreen(true) }) { Text("横屏全屏") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            FilterChip(showEnglish, { showEnglish = !showEnglish }, label = { Text("英文") })
                            FilterChip(showChinese, { showChinese = !showChinese }, label = { Text("中文") })
                            FilterChip(listVisible, { listVisible = !listVisible }, label = { Text(if (listVisible) "收起列表" else "句子列表") })
                            TextButton(onClick = {
                                englishCues = emptyList(); chineseCues = emptyList(); cues = emptyList()
                                englishName = ""; chineseName = ""; playbackStore.clearSubtitles(); message = "字幕已清除"
                            }) { Text("清除") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { loopA = player.currentPosition; message = "A 点已设置" }) { Text("设 A") }
                            TextButton(onClick = { loopB = player.currentPosition; message = "B 点已设置" }) { Text("设 B") }
                            FilterChip(loopEnabled, {
                                val valid = loopA != null && loopB != null && loopB!! > loopA!!
                                if (valid) { loopEnabled = !loopEnabled; if (loopEnabled) sentenceRepeatCue = null }
                                else message = "请先设置有效的 A、B 点"
                            }, label = { Text(if (loopEnabled) "循环中" else "A-B 循环") })
                            FilterChip(sentenceRepeatCue != null, {
                                if (sentenceRepeatCue != null) sentenceRepeatCue = null
                                else {
                                    val cue = cues.lastOrNull { position >= it.startMs && position <= it.endMs }
                                    if (cue != null) { sentenceRepeatCue = cue; loopEnabled = false; message = "已开启当前句循环" }
                                    else message = "请先播放到一条字幕句子上"
                                }
                            }, label = { Text("单句循环") })
                            TextButton(onClick = { loopA = null; loopB = null; loopEnabled = false; sentenceRepeatCue = null }) { Text("清除") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("倍速", color = Color.Gray, fontSize = 12.sp)
                            listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { rate ->
                                FilterChip(selected = speed == rate, onClick = {
                                    speed = rate
                                    player.setPlaybackSpeed(rate)
                                }, label = { Text(if (rate == 1f) "1.0×" else "${rate}×") })
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(chineseOffsetText, { chineseOffsetText = it.filter { c -> c == '-' || c.isDigit() }.take(7) },
                                label = { Text("中文偏移 ms") }, modifier = Modifier.width(140.dp), singleLine = true)
                            TextButton(onClick = {
                                val offset = chineseOffsetText.toLongOrNull() ?: 0L
                                val shifted = chineseCues.map { cue ->
                                    val start = (cue.startMs + offset).coerceAtLeast(0L)
                                    cue.copy(startMs = start, endMs = (cue.endMs + offset).coerceAtLeast(start + 1L))
                                }
                                cues = when {
                                    englishCues.isNotEmpty() && shifted.isNotEmpty() -> SubtitleMerger.merge(englishCues, shifted)
                                    englishCues.isNotEmpty() -> englishCues
                                    else -> shifted.map { SubtitleCue(it.startMs, it.endMs, "", it.english) }
                                }
                                message = "已应用中文字幕偏移：${offset} ms"
                            }) { Text("应用偏移") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(timerText, { timerText = it.filter(Char::isDigit).take(3) },
                                label = { Text("分钟") }, modifier = Modifier.width(105.dp), singleLine = true)
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val minutes = timerText.toLongOrNull()
                                if (minutes != null && minutes > 0) {
                                    timerEnd = System.currentTimeMillis() + minutes * 60_000L
                                    message = "${minutes} 分钟后暂停"
                                } else message = "请输入有效分钟数"
                            }) { Text("定时暂停") }
                            TextButton(onClick = { timerEnd = null; message = "定时已取消" }) { Text("取消") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("逐句字幕", fontWeight = FontWeight.Bold)
                            Text("${cues.size} 条 · ${formatTime(position)}", color = Color.Gray, fontSize = 11.sp)
                        }
                        if (listVisible) {
                            if (cues.isEmpty()) {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Text("分别导入英文和中文字幕，自动生成逐句列表", color = Color.Gray)
                                }
                            } else LazyColumn(state = sentenceListState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                                itemsIndexed(cues) { index, cue ->
                                    val selected = index == activeIndex
                                    Column(
                                        Modifier.fillMaxWidth()
                                            .background(if (selected) Color(0xFF252D3B) else Color.Transparent)
                                            .clickable { player.seekTo(cue.startMs); player.play() }
                                            .padding(horizontal = 8.dp, vertical = 9.dp)
                                    ) {
                                        Text("${index + 1}. ${formatTime(cue.startMs)}", color = Color(0xFF8AB4F8), fontSize = 11.sp)
                                        if (cue.english.isNotBlank()) Text(cue.english, color = Color.White, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        if (!cue.chinese.isNullOrBlank()) Text(cue.chinese!!, color = Color.Gray, fontSize = 13.sp)
                                    }
                                    HorizontalDivider(color = Color(0xFF292B30))
                                }
                            }
                        } else Spacer(Modifier.weight(1f))
                        Text(message, color = Color(0xFF8AB4F8), fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
