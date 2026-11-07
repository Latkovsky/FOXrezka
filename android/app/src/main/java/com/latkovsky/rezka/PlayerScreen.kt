package com.latkovsky.rezka

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.latkovsky.rezka.core.Quality
import com.latkovsky.rezka.core.Episode
import com.latkovsky.rezka.core.Translator
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

private fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1_000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    qualities: List<Quality>,
    translators: List<Translator>,
    selectedTranslatorId: String,
    episodes: List<Episode>,
    selectedEpisodeId: String,
    title: String,
    subtitle: String,
    startPositionMs: Long,
    onBack: () -> Unit,
    onSelectTranslator: (String, Long) -> Unit,
    onSelectEpisode: (String) -> Unit,
    onWatchTimeDelta: (Long) -> Unit,
    onEpisodeCompleted: () -> Unit,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(startPositionMs) }
    var seekFeedback by remember { mutableStateOf<String?>(null) }

    val player = remember {
        val dataSource = DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(
                mapOf(
                    "Referer" to "https://rezka.fi/",
                    "User-Agent" to PLAYER_UA,
                )
            )
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSource)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(qualities.first().url))
                playWhenReady = true
                prepare()
            }
    }

    var restored by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        var completionReported = false
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !restored) {
                    if (startPositionMs > 0 && startPositionMs < player.duration) {
                        player.seekTo(startPositionMs)
                    }
                    restored = true
                } else if (playbackState == Player.STATE_ENDED && !completionReported) {
                    completionReported = true
                    onEpisodeCompleted()
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.errorCodeName + ": " +
                    (playbackError.cause?.message ?: playbackError.message)
            }
        }
        player.addListener(listener)
        onDispose {
            onSavePosition(player.currentPosition, player.duration.coerceAtLeast(0L))
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        var lastPositionSave = 0L
        while (isActive) {
            val sampleStarted = SystemClock.elapsedRealtime()
            delay(1_000)
            val now = SystemClock.elapsedRealtime()
            currentPosition = player.currentPosition
            if (player.isPlaying && player.playbackState == Player.STATE_READY) {
                onWatchTimeDelta((now - sampleStarted).coerceIn(0L, 1_500L))
            }
            if (player.isPlaying && now - lastPositionSave >= 5_000L) {
                onSavePosition(player.currentPosition, player.duration.coerceAtLeast(0L))
                lastPositionSave = now
            }
        }
    }
    LaunchedEffect(seekFeedback) {
        if (seekFeedback != null) {
            delay(900)
            seekFeedback = null
        }
    }
    LaunchedEffect(showControls, player.isPlaying) {
        if (showControls && player.isPlaying) {
            delay(3_500)
            showControls = false
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val activity = view.context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    fun leavePlayer() {
        (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onBack()
    }

    BackHandler(onBack = ::leavePlayer)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(player) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            when {
                                tap.x < size.width * 0.4f -> {
                                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                    seekFeedback = "−10 сек"
                                }
                                tap.x > size.width * 0.6f -> {
                                    val target = player.currentPosition + 10_000
                                    player.seekTo(if (player.duration > 0) minOf(target, player.duration) else target)
                                    seekFeedback = "+10 сек"
                                }
                            }
                        },
                        onTap = { showControls = !showControls },
                    )
                },
        )

        seekFeedback?.let { feedback ->
            Text(
                feedback,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .align(if (feedback.startsWith("−")) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 42.dp, vertical = 16.dp)
                    .background(Color.Black.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        }

        if (showControls) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.78f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = ::leavePlayer) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Default.Settings, contentDescription = "Настройки видео", tint = Color.White)
                }
            }
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))))
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                    showControls = true
                }) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Пауза" else "Продолжить",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
                Slider(
                    value = currentPosition.toFloat().coerceAtLeast(0f),
                    onValueChange = { currentPosition = it.toLong() },
                    valueRange = 0f..player.duration.coerceAtLeast(1L).toFloat(),
                    onValueChangeFinished = { player.seekTo(currentPosition) },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    Text(formatPlayerTime(player.duration.coerceAtLeast(0L)), color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        if (showSettings) {
            AlertDialog(
                onDismissRequest = { showSettings = false },
                title = { Text("Настройки видео") },
                text = {
                    Column(
                        Modifier
                            .width(340.dp)
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Качество", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        qualities.forEachIndexed { index, quality ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val position = player.currentPosition
                                        player.setMediaItem(
                                            MediaItem.fromUri(quality.url),
                                            position,
                                        )
                                        player.prepare()
                                        selectedQuality = index
                                        showSettings = false
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedQuality == index,
                                    onClick = {
                                        val position = player.currentPosition
                                        player.setMediaItem(
                                            MediaItem.fromUri(quality.url),
                                            position,
                                        )
                                        player.prepare()
                                        selectedQuality = index
                                        showSettings = false
                                    },
                                )
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(quality.label, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (translators.isNotEmpty()) {
                            Text("Озвучка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            translators.forEach { translator ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (translator.id != selectedTranslatorId) {
                                                player.pause()
                                                onSelectTranslator(translator.id, player.currentPosition)
                                            }
                                            showSettings = false
                                        }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = translator.id == selectedTranslatorId,
                                        onClick = {
                                            if (translator.id != selectedTranslatorId) {
                                                player.pause()
                                                onSelectTranslator(translator.id, player.currentPosition)
                                            }
                                            showSettings = false
                                        },
                                    )
                                    Text(
                                        translator.name + if (translator.premium) " · Premium" else "",
                                        fontWeight = if (translator.premium) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }

                        }
                        if (episodes.isNotEmpty()) {
                            Text("Серия", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            episodes.forEach { episode ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (episode.episodeId != selectedEpisodeId) {
                                                player.pause()
                                                onSelectEpisode(episode.episodeId)
                                            }
                                            showSettings = false
                                        }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = episode.episodeId == selectedEpisodeId,
                                        onClick = {
                                            if (episode.episodeId != selectedEpisodeId) {
                                                player.pause()
                                                onSelectEpisode(episode.episodeId)
                                            }
                                            showSettings = false
                                        },
                                    )
                                    Text(episode.title, maxLines = 1)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSettings = false }) { Text("Готово") }
                },
            )
        }

        if (error != null) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Не удалось воспроизвести видео",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    error ?: "",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    "Если сеть блокирует видео-CDN — включите VPN и попробуйте снова.",
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
