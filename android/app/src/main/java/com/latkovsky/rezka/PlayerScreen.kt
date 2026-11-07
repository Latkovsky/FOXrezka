package com.latkovsky.rezka

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val PLAYER_UA =
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/126.0.0.0 Mobile Safari/537.36"

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    qualities: List<Quality>,
    title: String,
    subtitle: String,
    startPositionMs: Long,
    onBack: () -> Unit,
    onSavePosition: (Long) -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableIntStateOf(0) }
    var showQualityPicker by remember { mutableStateOf(false) }

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
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && !restored) {
                    if (startPositionMs > 0 && startPositionMs < player.duration) {
                        player.seekTo(startPositionMs)
                    }
                    restored = true
                }
            }

            override fun onPlayerError(playbackError: PlaybackException) {
                error = playbackError.errorCodeName + ": " +
                    (playbackError.cause?.message ?: playbackError.message)
            }
        }
        player.addListener(listener)
        onDispose {
            onSavePosition(player.currentPosition)
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (isActive) {
            delay(5_000)
            if (player.isPlaying) onSavePosition(player.currentPosition)
        }
    }

    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val activity = view.context as? Activity
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            view.keepScreenOn = false
            if (previousOrientation != null) {
                activity?.requestedOrientation = previousOrientation
            }
        }
    }

    BackHandler(onBack = onBack)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    setShowRewindButton(false)
                    setShowFastForwardButton(false)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        IconButton(
            onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Replay10, contentDescription = "Назад на 10 секунд", tint = Color.White)
        }
        IconButton(
            onClick = {
                val target = player.currentPosition + 10_000
                player.seekTo(if (player.duration > 0) minOf(target, player.duration) else target)
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(Icons.Default.Forward10, contentDescription = "Вперёд на 10 секунд", tint = Color.White)
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = Color.White,
                )
            }
            Column(Modifier.padding(end = 12.dp)) {
                Text(
                    title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
            if (qualities.size > 1) {
                TextButton(onClick = { showQualityPicker = true }) {
                    Text(
                        qualities.getOrNull(selectedQuality)?.label.orEmpty(),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (showQualityPicker) {
            AlertDialog(
                onDismissRequest = { showQualityPicker = false },
                title = { Text("Качество видео") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                                        showQualityPicker = false
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
                                        showQualityPicker = false
                                    },
                                )
                                Column(Modifier.padding(start = 8.dp)) {
                                    Text(quality.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (quality.isHls) "Адаптивный поток" else "Прямой поток",
                                        color = TextDim,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showQualityPicker = false }) { Text("Готово") }
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
