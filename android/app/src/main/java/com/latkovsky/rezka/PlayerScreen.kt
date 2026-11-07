package com.latkovsky.rezka

import android.app.Activity
import android.content.pm.ActivityInfo
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextOverflow
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

private enum class PlayerDialog {
    SPEED,
    EPISODE,
}

private enum class PlayerMenu {
    QUALITY,
    AUDIO,
}

@Composable
private fun SelectionDialog(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier
                    .width(340.dp)
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEachIndexed { index, label ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = index == selectedIndex,
                            onClick = { onSelect(index) },
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово") }
        },
    )
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
    onEpisodeCompleted: () -> Unit,
    onSavePosition: (positionMs: Long, durationMs: Long) -> Unit,
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var selectedQuality by remember { mutableIntStateOf(0) }
    var activeDialog by remember { mutableStateOf<PlayerDialog?>(null) }
    var activeMenu by remember { mutableStateOf<PlayerMenu?>(null) }
    var showControls by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(1f) }
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
            delay(1_000)
            currentPosition = player.currentPosition
            if (player.isPlaying && player.playbackState == Player.STATE_READY) {
                lastPositionSave += 1_000L
            }
            if (lastPositionSave >= 5_000L) {
                onSavePosition(player.currentPosition, player.duration.coerceAtLeast(0L))
                lastPositionSave = 0L
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
                            if (controlsLocked) return@detectTapGestures
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
                        onTap = {
                            if (!controlsLocked) showControls = !showControls
                        },
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

        if (showControls && !controlsLocked) {
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
                TextButton(onClick = { activeDialog = PlayerDialog.SPEED }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Скорость", color = Color.White)
                        Text("${selectedSpeed}×", color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                }
                Box {
                    TextButton(onClick = { activeMenu = PlayerMenu.QUALITY }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Качество", color = Color.White)
                            Text(
                                qualities.getOrNull(selectedQuality)?.label.orEmpty(),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = activeMenu == PlayerMenu.QUALITY,
                        onDismissRequest = { activeMenu = null },
                    ) {
                        qualities.forEachIndexed { index, quality ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        quality.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                onClick = {
                                    selectedQuality = index
                                    val position = player.currentPosition
                                    player.setMediaItem(MediaItem.fromUri(quality.url), position)
                                    player.prepare()
                                    player.play()
                                    activeMenu = null
                                },
                                trailingIcon = {
                                    if (index == selectedQuality) {
                                        Icon(Icons.Default.Check, contentDescription = "Выбрано")
                                    }
                                },
                            )
                        }
                    }
                }
                if (translators.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { activeMenu = PlayerMenu.AUDIO }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Озвучка", color = Color.White)
                                Text(
                                    translators.firstOrNull { it.id == selectedTranslatorId }?.name.orEmpty(),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = activeMenu == PlayerMenu.AUDIO,
                            onDismissRequest = { activeMenu = null },
                        ) {
                            translators.forEach { translator ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            translator.name + if (translator.premium) " · Premium" else "",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    },
                                    onClick = {
                                        if (translator.id != selectedTranslatorId) {
                                            player.pause()
                                            onSelectTranslator(translator.id, player.currentPosition)
                                        }
                                        activeMenu = null
                                    },
                                    trailingIcon = {
                                        if (translator.id == selectedTranslatorId) {
                                            Icon(Icons.Default.Check, contentDescription = "Выбрано")
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                if (episodes.isNotEmpty()) {
                    TextButton(onClick = { activeDialog = PlayerDialog.EPISODE }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Серия", color = Color.White)
                            Text(
                                episodes.firstOrNull { it.episodeId == selectedEpisodeId }?.title.orEmpty(),
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                            )
                        }
                    }
                }
                IconButton(onClick = { controlsLocked = true; showControls = false }) {
                    Icon(Icons.Default.LockOpen, contentDescription = "Заблокировать управление", tint = Color.White)
                }
            }

            Row(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(32.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                IconButton(
                    onClick = {
                        player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                        seekFeedback = "−10 сек"
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Replay10, contentDescription = "Назад на 10 секунд", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = { if (player.isPlaying) player.pause() else player.play() },
                    modifier = Modifier.size(72.dp),
                ) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "Пауза" else "Продолжить",
                        tint = Color.White,
                        modifier = Modifier.size(54.dp),
                    )
                }
                IconButton(
                    onClick = {
                        val target = player.currentPosition + 10_000
                        player.seekTo(if (player.duration > 0) minOf(target, player.duration) else target)
                        seekFeedback = "+10 сек"
                    },
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(Icons.Default.Forward10, contentDescription = "Вперёд на 10 секунд", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatPlayerTime(currentPosition), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = currentPosition.toFloat().coerceAtLeast(0f),
                    onValueChange = { currentPosition = it.toLong() },
                    valueRange = 0f..player.duration.coerceAtLeast(1L).toFloat(),
                    onValueChangeFinished = { player.seekTo(currentPosition) },
                    modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
                )
                Text(formatPlayerTime(player.duration.coerceAtLeast(0L)), color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (controlsLocked) {
            IconButton(
                onClick = { controlsLocked = false; showControls = true },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = "Разблокировать управление", tint = Color.White)
            }
        }

        when (activeDialog) {
            PlayerDialog.SPEED -> SelectionDialog(
                title = "Скорость воспроизведения",
                options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).map { "${it}×" },
                selectedIndex = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f).indexOf(selectedSpeed),
                onSelect = { index ->
                    selectedSpeed = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)[index]
                    player.setPlaybackSpeed(selectedSpeed)
                    activeDialog = null
                },
                onDismiss = { activeDialog = null },
            )
            PlayerDialog.EPISODE -> SelectionDialog(
                title = "Выбрать серию",
                options = episodes.map { it.title },
                selectedIndex = episodes.indexOfFirst { it.episodeId == selectedEpisodeId },
                onSelect = { index ->
                    val episode = episodes[index]
                    if (episode.episodeId != selectedEpisodeId) {
                        player.pause()
                        onSelectEpisode(episode.episodeId)
                    }
                    activeDialog = null
                },
                onDismiss = { activeDialog = null },
            )
            null -> Unit
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
