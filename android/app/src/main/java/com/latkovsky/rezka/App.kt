package com.latkovsky.rezka

import android.content.Context
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
                        val best = args.streams.qualities.first()
                        PlayerScreen(
                            url = best.url,
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
                "rezka",
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
            "rezka",
            fontSize = 46.sp,
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
    onLogout: () -> Unit,
    onOpen: (String) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    val kinds = listOf("films", "series", "cartoons")
    val titles = listOf("Фильмы", "Сериалы", "Мультики")

    LaunchedEffect(tab, query, refreshTick) {
        if (query.isNotBlank()) delay(500)
        loading = true
        error = null
        val result = withContext(Dispatchers.IO) {
            runCatching {
                if (query.isBlank()) Core.client.category(kinds[tab])
                else Core.client.search(query)
            }
        }
        result
            .onSuccess { cards = it }
            .onFailure {
                error = it.userMessage()
                cards = emptyList()
            }
        loading = false
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "rezka",
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
                color = Accent,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                email,
                color = TextDim,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onLogout) {
                Icon(
                    Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Выйти",
                    tint = TextDim,
                )
            }
        }

        TabRow(
            selectedTabIndex = tab,
            containerColor = Color.Transparent,
            contentColor = Accent,
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = {
                        Text(
                            title,
                            fontWeight = if (tab == index) FontWeight.SemiBold else FontWeight.Normal,
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
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Box(Modifier.fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(
                    color = Accent,
                    modifier = Modifier.align(Alignment.Center),
                )

                error != null -> Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(error ?: "", color = TextDim)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { refreshTick++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Повторить")
                    }
                }

                cards.isEmpty() -> Text(
                    if (query.isBlank()) "Пусто" else "Ничего не найдено",
                    color = TextDim,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(cards, key = { it.path }) { card ->
                        PosterCard(card = card, onClick = { onOpen(card.path) })
                    }
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
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        detail.translators.forEach { t ->
                            FilterChip(
                                selected = translator == t.id,
                                onClick = { translator = t.id },
                                label = {
                                    Text(
                                        if (t.premium) "${t.name} · Premium" else t.name,
                                    )
                                },
                            )
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
