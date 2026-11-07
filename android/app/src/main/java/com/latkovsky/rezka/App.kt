package com.latkovsky.rezka

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.latkovsky.rezka.core.MovieDetail
import com.latkovsky.rezka.core.StreamSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    data class Detail(val detail: MovieDetail) : Screen
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
)

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
                .put("watchedAt", item.watchedAt),
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
    var cachedHome by remember { mutableStateOf(loadCachedHomepage(prefs)) }
    var selectedDestination by rememberSaveable { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val saved = prefs.getString("cookies", null)
                if (saved.isNullOrBlank()) return@runCatching null
                Core.client.importCookies(saved)
                Core.client.profile()
            }.getOrNull()
        }
        if (result?.loggedIn == true) {
            loggedIn = true
            email = result.email
        } else {
            withContext(Dispatchers.IO) { Core.client.logout() }
            prefs.edit().remove("cookies").apply()
        }
        booting = false
    }

    fun persistSession(profileEmail: String) {
        email = profileEmail
        loggedIn = true
        scope.launch(Dispatchers.IO) {
            val cookies = runCatching { Core.client.exportCookies() }.getOrDefault("")
            prefs.edit().putString("cookies", cookies).apply()
        }
    }

    fun openDetail(path: String) {
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { Core.client.open(path) }
            }
            busy = false
            result
                .onSuccess { screen = Screen.Detail(it) }
                .onFailure { error = it.userMessage() }
        }
    }

    fun startPlayback(
        detail: MovieDetail,
        translatorId: String,
        seasonId: String,
        episodeId: String,
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
                            startMs = prefs.getLong(key, 0L),
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
                        cachedHome = cachedHome,
                        onHomeCardsUpdated = { cachedHome = it },
                        prefs = prefs,
                        selectedDestination = selectedDestination,
                        onDestinationSelected = { selectedDestination = it },
                        onLogout = {
                            Core.client.logout()
                            prefs.edit().remove("cookies").apply()
                            loggedIn = false
                            screen = Screen.Home
                        },
                        onOpen = ::openDetail,
                    )

                    is Screen.Detail -> DetailScreen(
                        detail = current.detail,
                        busy = busy,
                        onBack = { screen = Screen.Home },
                        onPlay = { translatorId, seasonId, episodeId ->
                            startPlayback(current.detail, translatorId, seasonId, episodeId)
                        },
                    )

                    is Screen.Play -> {
                        val args = current.args
                        PlayerScreen(
                            qualities = args.streams.qualities,
                            title = args.detail.title,
                            subtitle = args.subtitle,
                            startPositionMs = args.startMs,
                            onBack = { screen = Screen.Detail(args.detail) },
                            onSavePosition = { ms ->
                                prefs.edit().putLong(args.resumeKey, ms).apply()
                            },
                        )
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
            Text(
                "FOXrezka",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = Accent,
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
        Text(
            "FOXrezka",
            fontSize = 42.sp,
            fontWeight = FontWeight.Black,
            color = Accent,
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
    cachedHome: List<Card>,
    onHomeCardsUpdated: (List<Card>) -> Unit,
    prefs: android.content.SharedPreferences,
    selectedDestination: Int,
    onDestinationSelected: (Int) -> Unit,
    onLogout: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var homeCards by remember { mutableStateOf(cachedHome) }
    var catalogCards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var homeLoading by remember { mutableStateOf(cachedHome.isEmpty()) }
    var catalogLoading by remember { mutableStateOf(false) }
    var homeError by remember { mutableStateOf<String?>(null) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var homeRefresh by remember { mutableIntStateOf(0) }
    var catalogRefresh by remember { mutableIntStateOf(0) }

    val kinds = listOf("films", "series", "cartoons")
    val categoryTitles = listOf("Фильмы", "Сериалы", "Мультики")
    val destinationTitles = listOf("Главная", "Каталог", "Профиль")

    LaunchedEffect(homeRefresh) {
        if (homeCards.isEmpty()) homeLoading = true
        homeError = null
        val result = withContext(Dispatchers.IO) {
            runCatching { Core.client.homepage() }
        }
        result
            .onSuccess {
                homeCards = it
                onHomeCardsUpdated(homeCards)
                saveCachedHomepage(prefs, homeCards)
            }
            .onFailure {
                homeError = it.userMessage()
                homeCards = emptyList()
            }
        homeLoading = false
    }

    LaunchedEffect(selectedDestination, category, query, catalogRefresh) {
        if (selectedDestination != 1) return@LaunchedEffect
        if (query.isNotBlank()) delay(500)
        catalogLoading = true
        catalogError = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) Core.client.category(kinds[category])
                else Core.client.search(query)
            }
        }
        result
            .onSuccess { catalogCards = it }
            .onFailure {
                catalogError = it.userMessage()
                catalogCards = emptyList()
            }
        catalogLoading = false
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
                }
            }

            else -> {
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
                    }
                }
            }
        }

        NavigationBar(containerColor = SurfaceDark) {
            val icons = listOf(Icons.Default.Home, Icons.Default.Movie, Icons.Default.Person)
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
    busy: Boolean,
    onBack: () -> Unit,
    onPlay: (String, String, String) -> Unit,
) {
    var translator by remember(detail.path) {
        mutableStateOf(
            detail.defaultTranslator.ifBlank {
                detail.translators.firstOrNull()?.id ?: ""
            },
        )
    }
    var season by remember(detail.path) {
        mutableStateOf(
            detail.defaultSeason.ifBlank {
                detail.seasons.firstOrNull()?.id ?: ""
            },
        )
    }
    var episode by remember(detail.path) {
        mutableStateOf(
            detail.defaultEpisode.ifBlank {
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
                detail.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(230.dp),
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
                                listOf(Color.Transparent, Bg),
                                startY = 200f,
                            )
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
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    detail.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (detail.originalTitle.isNotBlank()) {
                    Text(
                        detail.originalTitle,
                        color = TextDim,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (detail.info.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        detail.info,
                        color = TextDim,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (detail.description.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        detail.description,
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
                                    color = if (selected) Accent else SurfaceHigher,
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
                                            t.premium -> "Premium"
                                            selected -> "Выбрана"
                                            else -> "Озвучка"
                                        },
                                        color = if (selected) Accent else TextDim,
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
