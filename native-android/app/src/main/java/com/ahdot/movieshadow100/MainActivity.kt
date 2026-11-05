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
    DisposableEffect(player) { onDispose { player.release() } }

    var videoName by remember { mutableStateOf("尚未导入电影") }
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
    var timerText by remember { mutableStateOf("30") }
    var timerEnd by remember { mutableStateOf<Long?>(null) }
    var position by remember { mutableLongStateOf(0L) }
    var message by remember { mutableStateOf("导入本地电影和字幕即可开始") }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "本地电影"
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playWhenReady = false
            message = "电影已导入"
        }.onFailure { message = "电影导入失败：${it.message}" }
    }
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) runCatching {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("无法读取字幕文件")
            val parsed = SubtitleParser.parseSingleLanguage(raw)
            if (parsed.isEmpty()) error("没有识别到字幕，请检查文件格式")
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "字幕文件"
            if (importLanguage == "zh") {
                chineseCues = parsed
                chineseName = name
                message = "中文字幕导入成功：${parsed.size} 条"
            } else {
                englishCues = parsed
                englishName = name
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
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            val a = loopA
            val b = loopB
            if (loopEnabled && a != null && b != null && b > a && position >= b) {
                player.seekTo(a)
                if (!player.isPlaying) player.play()
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
                                englishName = ""; chineseName = ""; message = "字幕已清除"
                            }) { Text("清除") }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { loopA = player.currentPosition; message = "A 点已设置" }) { Text("设 A") }
                            TextButton(onClick = { loopB = player.currentPosition; message = "B 点已设置" }) { Text("设 B") }
                            FilterChip(loopEnabled, {
                                val valid = loopA != null && loopB != null && loopB!! > loopA!!
                                if (valid) loopEnabled = !loopEnabled else message = "请先设置有效的 A、B 点"
                            }, label = { Text(if (loopEnabled) "循环中" else "A-B 循环") })
                            TextButton(onClick = { loopA = null; loopB = null; loopEnabled = false }) { Text("清除") }
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
                            } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
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
                } else {
                    Row(Modifier.fillMaxWidth().background(Color(0xFF101114).copy(alpha = .95f)).padding(6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(videoName, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                        TextButton(onClick = { fullscreen = false; onFullscreen(false) }) { Text("退出全屏") }
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
