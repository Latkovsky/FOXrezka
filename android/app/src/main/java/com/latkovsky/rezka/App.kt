package com.latkovsky.rezka

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.latkovsky.rezka.core.Card
import com.latkovsky.rezka.core.Client
import com.latkovsky.rezka.core.CoreException
import com.latkovsky.rezka.core.Episode
import com.latkovsky.rezka.core.MovieDetail
import com.latkovsky.rezka.core.StreamSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object Core {
    val client: Client by lazy { Client() }
}

private const val PREFS = "rezka"
private const val RECENT_KEY = "recently_watched"
private const val HOME_CACHE_KEY = "homepage_series"

private sealed interface Screen {
    data object Home : Screen
    data class Detail(val detail: MovieDetail, val resumeItem: WatchedItem? = null) : Screen
    data class Play(val args: PlayerArgs) : Screen
}

data class PlayerArgs(
    val detail: MovieDetail,
    val streams: StreamSet,
    val translatorId: String,
    val seasonId: String,
    val episodeId: String,
    val subtitle: String,
    val startMs: Long,
    val resumeKey: String,
)

private data class WatchedItem(
    val path: String,
    val title: String,
    val poster: String,
    val subtitle: String,
    val watchedAt: Long,
    val translatorId: String,
    val seasonId: String,
    val episodeId: String,
    val positionMs: Long,
)

private data class FavoriteItem(
    val path: String,
    val title: String,
    val poster: String,
    val info: String,
)

private const val FAVORITES_KEY = "favorites"
private const val TOTAL_WATCHED_MS_KEY = "total_watched_ms"
private const val COMPLETED_SERIES_KEY = "completed_series_episodes"
private const val THEME_KEY = "appearance_theme"
private const val ENCRYPTED_COOKIES_KEY = "cookies_encrypted"
private const val SESSION_KEY_ALIAS = "foxrezka_session_key"

private fun sessionKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(SESSION_KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    generator.init(
        KeyGenParameterSpec.Builder(
            SESSION_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build(),
    )
    return generator.generateKey()
}

private fun encryptSession(value: String): String {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, sessionKey())
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
}

private fun decryptSession(value: String): String {
    val payload = Base64.decode(value, Base64.NO_WRAP)
    require(payload.size > 12) { "Повреждённые данные сессии" }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(
        Cipher.DECRYPT_MODE,
        sessionKey(),
        GCMParameterSpec(128, payload.copyOfRange(0, 12)),
    )
    return cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
}

private fun loadSessionCookies(prefs: android.content.SharedPreferences): String? {
    prefs.getString(ENCRYPTED_COOKIES_KEY, null)?.let { return decryptSession(it) }
    val legacy = prefs.getString("cookies", null)?.takeIf { it.isNotBlank() } ?: return null
    val encrypted = encryptSession(legacy)
    prefs.edit()
        .putString(ENCRYPTED_COOKIES_KEY, encrypted)
        .remove("cookies")
        .apply()
    return legacy
}

private fun loadFavorites(prefs: android.content.SharedPreferences): List<FavoriteItem> =
    runCatching {
        val items = JSONArray(prefs.getString(FAVORITES_KEY, "[]"))
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) {
                    add(
                        FavoriteItem(
                            path = path,
                            title = item.optString("title"),
                            poster = item.optString("poster"),
                            info = item.optString("info"),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

private fun saveFavorites(
    prefs: android.content.SharedPreferences,
    favorites: List<FavoriteItem>,
) {
    val json = JSONArray()
    favorites.forEach { favorite ->
        json.put(
            JSONObject()
                .put("path", favorite.path)
                .put("title", favorite.title)
                .put("poster", favorite.poster)
                .put("info", favorite.info),
        )
    }
    prefs.edit().putString(FAVORITES_KEY, json.toString()).apply()
}

private fun loadRecent(prefs: android.content.SharedPreferences): List<WatchedItem> =
    runCatching {
        val items = JSONArray(prefs.getString(RECENT_KEY, "[]"))
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) {
                    add(
                        WatchedItem(
                            path = path,
                            title = item.optString("title"),
                            poster = item.optString("poster"),
                            subtitle = item.optString("subtitle"),
                            watchedAt = item.optLong("watchedAt"),
                            translatorId = item.optString("translatorId"),
                            seasonId = item.optString("seasonId"),
                            episodeId = item.optString("episodeId"),
                            positionMs = item.optLong("positionMs"),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

private fun saveRecent(prefs: android.content.SharedPreferences, items: List<WatchedItem>) {
    val json = JSONArray()
    items.forEach { item ->
        json.put(
            JSONObject()
                .put("path", item.path)
                .put("title", item.title)
                .put("poster", item.poster)
                .put("subtitle", item.subtitle)
                .put("watchedAt", item.watchedAt)
                .put("translatorId", item.translatorId)
                .put("seasonId", item.seasonId)
                .put("episodeId", item.episodeId)
                .put("positionMs", item.positionMs),
        )
    }
    prefs.edit().putString(RECENT_KEY, json.toString()).apply()
}

private fun loadCachedHomepage(prefs: android.content.SharedPreferences): List<Card> =
    runCatching {
        val items = JSONArray(prefs.getString(HOME_CACHE_KEY, "[]"))
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) {
                    add(
                        Card(
                            id = item.optString("id"),
                            title = item.optString("title"),
                            path = path,
                            poster = item.optString("poster"),
                            info = item.optString("info"),
                            kind = item.optString("kind", "series"),
                        ),
                    )
                }
            }
        }
    }.getOrDefault(emptyList())

private fun saveCachedHomepage(
    prefs: android.content.SharedPreferences,
    cards: List<Card>,
) {
    val json = JSONArray()
    cards.forEach { card ->
        json.put(
            JSONObject()
                .put("id", card.id)
                .put("title", card.title)
                .put("path", card.path)
                .put("poster", card.poster)
                .put("info", card.info)
                .put("kind", card.kind),
        )
    }
    prefs.edit().putString(HOME_CACHE_KEY, json.toString()).apply()
}

private fun resumeKey(path: String, translator: String, season: String, episode: String) =
    "pos:$path|$translator|$season|$episode"

private fun formatWatchTime(milliseconds: Long): String {
    val minutes = (milliseconds / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return if (hours > 0) "$hours ч $remainingMinutes мин" else "$minutes мин"
}

private fun Throwable.userMessage(): String = when (this) {
    is CoreException.Auth -> msg
    is CoreException.Network -> msg
    is CoreException.Parse -> msg
    else -> message ?: "Неизвестная ошибка"
}

@Composable
fun App() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()

    var booting by remember { mutableStateOf(true) }
    var loggedIn by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var recent by remember { mutableStateOf(loadRecent(prefs)) }
    var favorites by remember { mutableStateOf(loadFavorites(prefs)) }
    var cachedHome by remember { mutableStateOf(loadCachedHomepage(prefs)) }
    var totalWatchedMs by remember { mutableLongStateOf(prefs.getLong(TOTAL_WATCHED_MS_KEY, 0L)) }
    var completedSeriesEpisodes by remember {
        mutableStateOf(prefs.getStringSet(COMPLETED_SERIES_KEY, emptySet()).orEmpty().toSet())
    }
    var selectedTheme by remember {
        mutableStateOf(FoxThemeVariant.fromKey(prefs.getString(THEME_KEY, "green")))
    }
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        setFoxTheme(selectedTheme)
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val saved = loadSessionCookies(prefs) ?: return@runCatching null
                Core.client.importCookies(saved)
                Core.client.profile()
            }
        }
        if (result.getOrNull()?.loggedIn == true) {
            loggedIn = true
            email = result.getOrNull()?.email.orEmpty()
        } else {
            withContext(Dispatchers.IO) { Core.client.logout() }
            if (result.isFailure) {
                error = "Не удалось восстановить сохранённую сессию. Войдите снова."
            }
            prefs.edit().remove("cookies").remove(ENCRYPTED_COOKIES_KEY).apply()
        }
        booting = false
    }

    fun persistSession(profileEmail: String) {
        email = profileEmail
        loggedIn = true
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                val cookies = Core.client.exportCookies()
                require(cookies.isNotBlank()) { "Пустая сессия" }
                prefs.edit().putString(ENCRYPTED_COOKIES_KEY, encryptSession(cookies)).apply()
                prefs.edit().remove("cookies").apply()
            }
            if (result.isFailure) {
                withContext(Dispatchers.Main) {
                    error = "Не удалось безопасно сохранить сессию."
                }
            }
        }
    }

    fun openDetail(path: String, resumeItem: WatchedItem? = null) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Core.client.open(path) }
            }
            busy = false
            result
                .onSuccess { screen = Screen.Detail(it, resumeItem) }
                .onFailure { error = it.userMessage() }
        }
    }

    fun startPlayback(
        detail: MovieDetail,
        translatorId: String,
        seasonId: String,
        episodeId: String,
        startAtMs: Long? = null,
    ) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    Core.client.streams(detail, translatorId, seasonId, episodeId)
                }
            }
            busy = false
            result
                .onSuccess { streams ->
                    val best = streams.qualities.firstOrNull()
                    if (best == null) {
                        error = "Не удалось найти видео"
                        return@onSuccess
                    }
                    val key = resumeKey(detail.path, translatorId, seasonId, episodeId)
                    val subtitle = buildSubtitle(detail, translatorId, seasonId, episodeId)
                    recent = (
                        listOf(
                            WatchedItem(
                                path = detail.path,
                                title = detail.title,
                                poster = detail.poster,
                                subtitle = subtitle,
                                watchedAt = System.currentTimeMillis(),
                                translatorId = translatorId,
                                seasonId = seasonId,
                                episodeId = episodeId,
                                positionMs = startAtMs ?: prefs.getLong(key, 0L),
                            ),
                        ) + recent.filterNot { it.path == detail.path }
                    ).take(30)
                    saveRecent(prefs, recent)
                    screen = Screen.Play(
                        PlayerArgs(
                            detail = detail,
                            streams = streams,
                            translatorId = translatorId,
                            seasonId = seasonId,
                            episodeId = episodeId,
                            subtitle = subtitle,
                            startMs = startAtMs ?: prefs.getLong(key, 0L),
                            resumeKey = key,
                        )
                    )
                }
                .onFailure { error = it.userMessage() }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when {
                booting -> SplashScreen()

                !loggedIn -> LoginScreen(
                    loading = busy,
                    errorText = error,
                    onSubmit = { loginEmail, password ->
                        busy = true
                        error = null
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                runCatching { Core.client.login(loginEmail, password) }
                            }
                            busy = false
                            result
                                .onSuccess { profile ->
                                    if (profile.loggedIn) {
                                        persistSession(profile.email.ifBlank { loginEmail })
                                    } else {
                                        error = "Не удалось войти"
                                    }
                                }
                                .onFailure { error = it.userMessage() }
                        }
                    },
                )

                else -> when (val current = screen) {
                    is Screen.Home -> HomeScreen(
                        email = email,
                        recent = recent,
                        favorites = favorites,
                        totalWatchedMs = totalWatchedMs,
                        completedSeriesCount = completedSeriesEpisodes.size,
                        themeVariant = selectedTheme,
                        onThemeChange = { variant ->
                            selectedTheme = variant
                            setFoxTheme(variant)
                            prefs.edit().putString(THEME_KEY, variant.key).apply()
                        },
                        cachedHome = cachedHome,
                        onHomeCardsUpdated = { cachedHome = it },
                        prefs = prefs,
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { selectedDestination = it },
                        onContinue = { watched -> openDetail(watched.path, watched) },
                        onLogout = {
                            Core.client.logout()
                            prefs.edit().remove("cookies").remove(ENCRYPTED_COOKIES_KEY).apply()
                            loggedIn = false
                            screen = Screen.Home
                        },
                        onOpen = ::openDetail,
                    )

                    is Screen.Detail -> DetailScreen(
                        detail = current.detail,
                        resumeItem = current.resumeItem,
                        isFavorite = favorites.any { it.path == current.detail.path },
                        busy = busy,
                        onBack = { screen = Screen.Home },
                        onToggleFavorite = {
                            val detail = current.detail
                            val existing = favorites.firstOrNull { it.path == detail.path }
                            favorites = if (existing == null) {
                                listOf(
                                    FavoriteItem(
                                        path = detail.path,
                                        title = detail.title,
                                        poster = detail.poster,
                                        info = detail.info,
                                    ),
                                ) + favorites
                            } else {
                                favorites.filterNot { it.path == detail.path }
                            }
                            saveFavorites(prefs, favorites)
                        },
                        onPlay = { translatorId, seasonId, episodeId ->
                            startPlayback(current.detail, translatorId, seasonId, episodeId)
                        },
                    )

                    is Screen.Play -> {
                        val args = current.args
                        key(args.resumeKey, args.startMs, args.streams.qualities.firstOrNull()?.url) {
                            PlayerScreen(
                                qualities = args.streams.qualities,
                                translators = args.detail.translators,
                                selectedTranslatorId = args.translatorId,
                                episodes = args.detail.seasons
                                    .firstOrNull { it.id == args.seasonId }
                                    ?.episodes
                                    .orEmpty(),
                                selectedEpisodeId = args.episodeId,
                                title = args.detail.title,
                                subtitle = args.subtitle,
                                startPositionMs = args.startMs,
                                onBack = { screen = Screen.Detail(args.detail) },
                                onSelectTranslator = { translatorId, position ->
                                    startPlayback(
                                        args.detail,
                                        translatorId,
                                        args.seasonId,
                                        args.episodeId,
                                        position,
                                    )
                                },
                                onSelectEpisode = { episodeId ->
                                    startPlayback(
                                        args.detail,
                                        args.translatorId,
                                        args.seasonId,
                                        episodeId,
                                    )
                                },
                                onWatchTimeDelta = { deltaMs ->
                                    totalWatchedMs += deltaMs
                                    prefs.edit().putLong(TOTAL_WATCHED_MS_KEY, totalWatchedMs).apply()
                                },
                                onEpisodeCompleted = {
                                    if (args.detail.isSeries) {
                                        val key = "${args.detail.path}|${args.seasonId}|${args.episodeId}"
                                        completedSeriesEpisodes = completedSeriesEpisodes + key
                                        prefs.edit()
                                            .putStringSet(COMPLETED_SERIES_KEY, completedSeriesEpisodes)
                                            .apply()
                                    }
                                },
                                onSavePosition = { ms, durationMs ->
                                    prefs.edit().putLong(args.resumeKey, ms).apply()
                                    prefs.edit()
                                        .putLong("duration:${args.resumeKey.removePrefix("pos:")}", durationMs)
                                        .apply()
                                    recent = recent.map { item ->
                                        if (item.path == args.detail.path &&
                                            item.translatorId == args.translatorId &&
                                            item.seasonId == args.seasonId &&
                                            item.episodeId == args.episodeId
                                        ) {
                                            item.copy(positionMs = ms, watchedAt = System.currentTimeMillis())
                                        } else {
                                            item
                                        }
                                    }
                                    saveRecent(prefs, recent)
                                },
                            )
                        }
                    }
                }
            }

            if (busy) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Accent)
                }
            }

            error?.let { message ->
                if (loggedIn) {
                    AlertDialog(
                        onDismissRequest = { error = null },
                        confirmButton = {
                            TextButton(onClick = { error = null }) { Text("Ок") }
                        },
                        title = { Text("Ошибка") },
                        text = { Text(message) },
                    )
                }
            }
        }
    }
}

private fun buildSubtitle(
    detail: MovieDetail,
    translatorId: String,
    seasonId: String,
    episodeId: String,
): String {
    val parts = mutableListOf<String>()
    if (detail.isSeries) {
        detail.seasons.firstOrNull { it.id == seasonId }?.let { season ->
            parts += season.title
            season.episodes.firstOrNull { it.episodeId == episodeId }?.let {
                parts += it.title
            }
        }
    }
    detail.translators.firstOrNull { it.id == translatorId }?.let {
        parts += it.name
    }
    return parts.joinToString(" • ")
}

@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = R.drawable.foxrezka_logo,
                contentDescription = "FOXrezka",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = 250.dp, height = 240.dp),
            )
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginScreen(
    loading: Boolean,
    errorText: String?,
    onSubmit: (String, String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = R.drawable.foxrezka_logo,
            contentDescription = "FOXrezka",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(width = 250.dp, height = 240.dp),
        )
        Text(
            "Кино без лишнего",
            color = TextDim,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            singleLine = true,
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (errorText != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                errorText,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { onSubmit(email.trim(), password) },
            enabled = !loading && email.isNotBlank() && password.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("Войти", fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "Вход в аккаунт rezka.fi",
            color = TextDim,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    email: String,
    recent: List<WatchedItem>,
    favorites: List<FavoriteItem>,
    totalWatchedMs: Long,
    completedSeriesCount: Int,
    themeVariant: FoxThemeVariant,
    onThemeChange: (FoxThemeVariant) -> Unit,
    cachedHome: List<Card>,
    onHomeCardsUpdated: (List<Card>) -> Unit,
    prefs: android.content.SharedPreferences,
    selectedDestination: Int,
    onDestinationSelected: (Int) -> Unit,
    onContinue: (WatchedItem) -> Unit,
    onLogout: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var homeCards by remember { mutableStateOf(cachedHome) }
    var catalogCards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(cachedHome.isEmpty()) }
    var homeLoadingMore by remember { mutableStateOf(false) }
    var homeHasMore by remember { mutableStateOf(true) }
    var homeMoreError by remember { mutableStateOf(false) }
    var homePage by remember { mutableIntStateOf(2) }
    var homeRetry by remember { mutableIntStateOf(0) }
    var catalogLoading by remember { mutableStateOf(false) }
    var catalogLoadingMore by remember { mutableStateOf(false) }
    var catalogHasMore by remember { mutableStateOf(false) }
    var catalogMoreError by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableIntStateOf(2) }
    var catalogRetry by remember { mutableIntStateOf(0) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var homeRefresh by remember { mutableIntStateOf(0) }
    var catalogRefresh by remember { mutableIntStateOf(0) }
    val homeGridState = rememberLazyGridState()
    val catalogGridState = rememberLazyGridState()

    val kinds = listOf("films", "series", "cartoons")
    val categoryTitles = listOf("Фильмы", "Сериалы", "Мультики")
    val destinationTitles = listOf("Главная", "Каталог", "Профиль", "Настройки")

    LaunchedEffect(homeRefresh) {
        homeLoading = true
        homeHasMore = false
        homeError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { Core.client.homepagePage(1u) }
        }
        result
            .onSuccess {
                homeCards = it
                homePage = 2
                homeHasMore = it.isNotEmpty()
                homeMoreError = false
                onHomeCardsUpdated(homeCards)
                saveCachedHomepage(prefs, homeCards)
            }
            .onFailure {
                homeError = it.userMessage()
                homeCards = emptyList()
            }
        homeLoading = false
    }

    LaunchedEffect(
        selectedDestination,
        homeCards.size,
        homeHasMore,
        homeLoading,
        homeMoreError,
        homePage,
        homeRetry,
    ) {
        if (selectedDestination != 0 || !homeHasMore || homeLoading || homeLoadingMore || homeMoreError) {
            return@LaunchedEffect
        }
        snapshotFlow { homeGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                if (homeCards.isNotEmpty() && lastVisible >= homeCards.size - 5) {
                    homeLoadingMore = true
                    val page = homePage
                    val result = withContext(Dispatchers.IO) {
                        runCatching { Core.client.homepagePage(page.toUInt()) }
                    }
                    result
                        .onSuccess { next ->
                            val merged = (homeCards + next).distinctBy { it.path }
                            homeHasMore = next.isNotEmpty() && merged.size > homeCards.size
                            if (merged.size > homeCards.size) {
                                homeCards = merged
                                homePage = page + 1
                                onHomeCardsUpdated(merged)
                                saveCachedHomepage(prefs, merged)
                            }
                        }
                        .onFailure { homeMoreError = true }
                    homeLoadingMore = false
                }
            }
    }

    LaunchedEffect(selectedDestination, category, query, catalogRefresh) {
        if (selectedDestination != 1) return@LaunchedEffect
        if (query.isNotBlank()) delay(500)
        catalogLoading = true
        catalogHasMore = false
        catalogMoreError = false
        catalogPage = 2
        catalogError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) Core.client.categoryPage(kinds[category], 1u)
                else Core.client.searchPage(query, 1u)
            }
        }
        result
            .onSuccess {
                catalogCards = it
                catalogHasMore = it.isNotEmpty()
            }
            .onFailure {
                catalogError = it.userMessage()
                catalogCards = emptyList()
            }
        catalogLoading = false
    }

    LaunchedEffect(
        selectedDestination,
        category,
        query,
        catalogCards.size,
        catalogHasMore,
        catalogLoading,
        catalogMoreError,
        catalogPage,
        catalogRetry,
    ) {
        if (selectedDestination != 1 || catalogLoading || catalogLoadingMore ||
            !catalogHasMore || catalogMoreError || catalogCards.isEmpty()
        ) {
            return@LaunchedEffect
        }
        snapshotFlow { catalogGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collectLatest { lastVisible ->
                if (lastVisible >= catalogCards.size - 5) {
                    catalogLoadingMore = true
                    val page = catalogPage
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            if (query.isBlank()) Core.client.categoryPage(kinds[category], page.toUInt())
                            else Core.client.searchPage(query, page.toUInt())
                        }
                    }
                    result
                        .onSuccess { next ->
                            val merged = (catalogCards + next).distinctBy { it.path }
                            catalogHasMore = next.isNotEmpty() && merged.size > catalogCards.size
                            if (merged.size > catalogCards.size) {
                                catalogCards = merged
                                catalogPage = page + 1
                            }
                        }
                        .onFailure { catalogMoreError = true }
                    catalogLoadingMore = false
                }
            }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "FOXrezka",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = Accent,
            )
            Spacer(Modifier.weight(1f))
            Text(destinationTitles[selectedDestination], color = TextDim, style = MaterialTheme.typography.labelLarge)
        }

        when (selectedDestination) {
            0 -> {
                Text(
                    "Сериалы, которые сейчас смотрят",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 18.dp, top = 12.dp, bottom = 4.dp),
                )
                Text(
                    "Подборка с главной страницы Rezka",
                    color = TextDim,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = 18.dp, bottom = 12.dp),
                )
                if (recent.isNotEmpty()) {
                    Text(
                        "Продолжить просмотр",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 18.dp, bottom = 8.dp),
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 14.dp),
                    ) {
                        items(recent.take(10), key = { "continue:${it.path}" }) { watched ->
                            Surface(
                                onClick = { onContinue(watched) },
                                color = SurfaceDark,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.width(260.dp),
                            ) {
                                Row(
                                    Modifier.padding(9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = watched.poster,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 54.dp, height = 76.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceHigher),
                                    )
                                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                        Text(
                                            watched.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            watched.subtitle.ifBlank { "Продолжить" },
                                            color = TextDim,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (watched.positionMs > 0) {
                                            val duration = prefs.getLong(
                                                "duration:${watched.path}|${watched.translatorId}|${watched.seasonId}|${watched.episodeId}",
                                                0L,
                                            )
                                            if (duration > 0) {
                                                androidx.compose.material3.LinearProgressIndicator(
                                                    progress = { (watched.positionMs.toFloat() / duration).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    color = Accent,
                                                    trackColor = SurfaceHigher,
                                                )
                                            }
                                        }
                                    }
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Продолжить", tint = Accent)
                                }
                            }
                        }
                    }
                }
                if (homeError != null && homeCards.isNotEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Не удалось обновить подборку. Показаны сохранённые данные.",
                            color = TextDim,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { homeRefresh++ }) { Text("Повторить") }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        homeLoading -> CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        homeError != null -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(homeError.orEmpty(), color = TextDim)
                            TextButton(onClick = { homeRefresh++ }) { Text("Повторить") }
                        }

                        homeCards.isEmpty() -> Text(
                            "На главной Rezka пока нет сериалов для показа",
                            color = TextDim,
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        )

                        else -> LazyVerticalGrid(
                            state = homeGridState,
                            columns = GridCells.Adaptive(minSize = 108.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(homeCards, key = { it.path }) { card ->
                                PosterCard(card = card, onClick = { onOpen(card.path) })
                            }
                        }
                    }
                    if (homeLoadingMore) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).size(28.dp),
                            strokeWidth = 3.dp,
                        )
                    } else if (homeMoreError) {
                        TextButton(
                            onClick = {
                                homeMoreError = false
                                homeRetry++
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Text("Не удалось загрузить. Повторить")
                        }
                    }
                }
            }

            1 -> {
                TabRow(
                    selectedTabIndex = category,
                    containerColor = Color.Transparent,
                    contentColor = Accent,
                ) {
                    categoryTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = category == index,
                            onClick = { category = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (category == index) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            },
                        )
                    }
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Поиск фильмов и сериалов") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when {
                        catalogLoading -> CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        catalogError != null -> Column(
                            Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(catalogError.orEmpty(), color = TextDim)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { catalogRefresh++ }) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Повторить")
                            }
                        }

                        catalogCards.isEmpty() -> Text(
                            if (query.isBlank()) "Пока ничего нет" else "Ничего не найдено",
                            color = TextDim,
                            modifier = Modifier.align(Alignment.Center),
                        )

                        else -> LazyVerticalGrid(
                            state = catalogGridState,
                            columns = GridCells.Adaptive(minSize = 108.dp),
                            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            items(catalogCards, key = { it.path }) { card ->
                                PosterCard(card = card, onClick = { onOpen(card.path) })
                            }
                        }
                    }
                    if (catalogLoadingMore) {
                        CircularProgressIndicator(
                            color = Accent,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp).size(28.dp),
                            strokeWidth = 3.dp,
                        )
                    } else if (catalogMoreError) {
                        TextButton(
                            onClick = {
                                catalogMoreError = false
                                catalogRetry++
                            },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Text("Не удалось загрузить. Повторить")
                        }
                    }
                }
            }

            2 -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "profile") {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) {
                            Column(Modifier.padding(20.dp)) {
                                Text(
                                    email.take(1).uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .background(Accent, RoundedCornerShape(18.dp))
                                        .padding(horizontal = 18.dp, vertical = 12.dp),
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(email, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Ваш профиль FOXrezka", color = TextDim, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(14.dp))
                                TextButton(onClick = onLogout) {
                                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Выйти из аккаунта")
                                }
                            }
                        }
                    }

                    item(key = "watch-stats") {
                        Surface(
                            color = SurfaceDark,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(18.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("Время просмотра", color = TextDim, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        formatWatchTime(totalWatchedMs),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Завершено серий", color = TextDim, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        completedSeriesCount.toString(),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Accent,
                                    )
                                }
                            }
                        }
                    }

                    item(key = "recent-title") {
                        Text(
                            "Смотрели недавно",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    if (recent.isEmpty()) {
                        item(key = "recent-empty") {
                            Surface(
                                color = SurfaceDark,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    "Здесь появятся фильмы и серии, которые вы запустите в FOXrezka.",
                                    color = TextDim,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(18.dp),
                                )
                            }
                        }
                    } else {
                        items(recent, key = { it.path }) { watched ->
                            Surface(
                                onClick = { onOpen(watched.path) },
                                color = SurfaceDark,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    AsyncImage(
                                        model = watched.poster,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 58.dp, height = 82.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(SurfaceHigher),
                                    )
                                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                        Text(
                                            watched.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (watched.subtitle.isNotBlank()) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                watched.subtitle,
                                                color = TextDim,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            java.text.DateFormat.getDateTimeInstance(
                                                java.text.DateFormat.MEDIUM,
                                                java.text.DateFormat.SHORT,
                                            ).format(java.util.Date(watched.watchedAt)),
                                            color = TextDim,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Открыть", tint = Accent)
                                }
                            }
                        }

                        item(key = "favorites-title") {
                            Text(
                                "Избранное",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 18.dp),
                            )
                        }
                        if (favorites.isEmpty()) {
                            item(key = "favorites-empty") {
                                Surface(
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Добавляйте фильмы и сериалы в избранное со страницы описания.",
                                        color = TextDim,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(18.dp),
                                    )
                                }
                            }
                        } else {
                            items(favorites, key = { "favorite:${it.path}" }) { favorite ->
                                Surface(
                                    onClick = { onOpen(favorite.path) },
                                    color = SurfaceDark,
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        AsyncImage(
                                            model = favorite.poster,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(width = 58.dp, height = 82.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(SurfaceHigher),
                                        )
                                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                            Text(favorite.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                            Text(
                                                favorite.info,
                                                color = TextDim,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                            )
                                        }
                                        Icon(Icons.Default.Favorite, contentDescription = "В избранном", tint = Accent)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            else -> SettingsScreen(
                selectedTheme = themeVariant,
                onThemeChange = onThemeChange,
            )
        }

        NavigationBar(containerColor = SurfaceDark) {
            val icons = listOf(
                Icons.Default.Home,
                Icons.Default.Movie,
                Icons.Default.Person,
                Icons.Default.Settings,
            )
            destinationTitles.forEachIndexed { index, title ->
                NavigationBarItem(
                    selected = selectedDestination == index,
                    onClick = { onDestinationSelected(index) },
                    icon = { Icon(icons[index], contentDescription = null) },
                    label = { Text(title) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                        selectedIconColor = Accent,
                        selectedTextColor = Accent,
                        indicatorColor = SurfaceHigher,
                    ),
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    selectedTheme: FoxThemeVariant,
    onThemeChange: (FoxThemeVariant) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Настройки",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Раздел готовится. Здесь появятся параметры FOXrezka.",
                    color = TextDim,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Оформление", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FoxThemeVariant.entries.forEach { variant ->
                        FilterChip(
                            selected = selectedTheme == variant,
                            onClick = { onThemeChange(variant) },
                            label = { Text(variant.title) },
                        )
                    }
                }
            }
        }

        Surface(
            color = SurfaceDark,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = R.drawable.foxrezka_logo,
                    contentDescription = "FOXrezka",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(52.dp),
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text("FOXrezka", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Версия ${BuildConfig.VERSION_NAME}",
                        color = TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterCard(card: Card, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceHigher),
        ) {
            AsyncImage(
                model = card.poster,
                contentDescription = card.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (card.info.isNotBlank()) {
                Text(
                    card.info.substringBefore(",").trim(),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(
                            Color.Black.copy(alpha = 0.55f),
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            card.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            card.info,
            style = MaterialTheme.typography.labelSmall,
            color = TextDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DetailScreen(
    detail: MovieDetail,
    resumeItem: WatchedItem?,
    isFavorite: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlay: (String, String, String) -> Unit,
) {
    var translator by remember(detail.path) {
        mutableStateOf(
            resumeItem?.translatorId?.takeIf { it.isNotBlank() }
                ?: detail.defaultTranslator.ifBlank { detail.translators.firstOrNull()?.id ?: "" },
        )
    }
    var season by remember(detail.path) {
        mutableStateOf(
            resumeItem?.seasonId?.takeIf { it.isNotBlank() }
                ?: detail.defaultSeason.ifBlank { detail.seasons.firstOrNull()?.id ?: "" },
        )
    }
    var episode by remember(detail.path) {
        mutableStateOf(
            resumeItem?.episodeId?.takeIf { it.isNotBlank() }
                ?: detail.defaultEpisode.ifBlank {
                    detail.seasons.firstOrNull()?.episodes?.firstOrNull()?.episodeId ?: ""
                },
        )
    }

    BackHandler(onBack = onBack)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
            Text(
                "О фильме",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Убрать из избранного" else "В избранное",
                    tint = if (isFavorite) Accent else TextDim,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(340.dp),
            ) {
                AsyncImage(
                    model = detail.poster,
                    contentDescription = detail.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Bg.copy(alpha = 0.18f), Bg),
                                startY = 120f,
                            ),
                        ),
                )
                if (detail.rating.isNotBlank()) {
                    Text(
                        "★ ${detail.rating}",
                        color = Color(0xFFFFC94D),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 20.dp),
                ) {
                    Text(
                        detail.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (detail.originalTitle.isNotBlank()) {
                        Text(
                            detail.originalTitle,
                            color = Color.White.copy(alpha = 0.76f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (detail.info.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            detail.info,
                            color = Color.White.copy(alpha = 0.72f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                if (detail.description.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Описание",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        detail.description,
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 21.sp,
                    )
                }

                if (detail.translators.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Озвучка",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(detail.translators, key = { it.id }) { t ->
                            val selected = translator == t.id
                            Surface(
                                onClick = { translator = t.id },
                                color = if (selected) Accent.copy(alpha = 0.16f) else SurfaceDark,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = when {
                                        selected -> Accent
                                        t.premium -> Color(0xFFFFC45B).copy(alpha = 0.72f)
                                        else -> SurfaceHigher
                                    },
                                ),
                            ) {
                                Column(
                                    Modifier
                                        .widthIn(min = 132.dp, max = 210.dp)
                                        .padding(horizontal = 14.dp, vertical = 11.dp),
                                ) {
                                    Text(
                                        t.name,
                                        color = if (selected) Accent else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        when {
                                            t.premium && selected -> "✓ Выбрана · Premium"
                                            t.premium -> "✦ Premium-озвучка"
                                            selected -> "Выбрана"
                                            else -> "Озвучка"
                                        },
                                        color = when {
                                            selected -> Accent
                                            t.premium -> Color(0xFFFFC45B)
                                            else -> TextDim
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }

                if (detail.seasons.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Сезон",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(detail.seasons, key = { it.id }) { s ->
                            FilterChip(
                                selected = season == s.id,
                                onClick = {
                                    season = s.id
                                    episode = s.episodes.firstOrNull()?.episodeId ?: ""
                                },
                                label = { Text(s.title) },
                            )
                        }
                    }

                    val current = detail.seasons.firstOrNull { it.id == season }
                    if (current != null && current.episodes.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Серия",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            current.episodes.forEach { e ->
                                FilterChip(
                                    selected = episode == e.episodeId,
                                    onClick = { episode = e.episodeId },
                                    label = { Text(e.title) },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }

        Surface(color = SurfaceDark) {
            Button(
                onClick = { onPlay(translator, season, episode) },
                enabled = !busy,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .navigationBarsPadding()
                    .height(52.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (detail.isSeries) "Смотреть серию" else "Смотреть",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
