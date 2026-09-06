package com.musicdownloader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val DeepOrange = Color(0xFFFF5722)
private val WarmBackground = Color(0xFFFFF3E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_MusicDownloader)
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = WarmBackground) {
                    SearchScreen()
                }
            }
        }
    }
}

@Composable
fun SearchScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf(10) }
    var limitMenuExpanded by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Готовий до роботи") }

    val results = remember { mutableStateListOf<SearchResult>() }
    val selected = remember { mutableStateListOf<Boolean>() }

    var currentlyPlayingIndex by remember { mutableStateOf(-1) }
    var isBuffering by remember { mutableStateOf(false) }

    // Один спільний ExoPlayer на весь екран - переінакшуємо джерело
    // при кожному новому натисканні "▶", а не створюємо новий програвач
    // щоразу.
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        currentlyPlayingIndex = -1
                    }
                }

                // Раніше помилки плеєра ніде не оброблялись - якщо
                // відтворення падало (наприклад, через мережеву помилку),
                // застосунок мовчки "зависав" на іконці ⏹, нічого не
                // показуючи. Тепер про це буде видно в статусі.
                override fun onPlayerError(error: PlaybackException) {
                    statusText = "Помилка відтворення: ${error.errorCodeName}"
                    currentlyPlayingIndex = -1
                    isBuffering = false
                }
            })
        }
    }

    // Власний DataSource з тим самим User-Agent, що й у NewPipeDownloaderImpl -
    // без цього узгодження сервери YouTube нерідко мовчки відхиляють запит
    // на відтворення потоку.
    val dataSourceFactory = remember {
        DefaultHttpDataSource.Factory().setUserAgent(NETWORK_USER_AGENT)
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            statusText = "Введіть назву треку!"
            return
        }
        isSearching = true
        statusText = "Пошук: $trimmed..."
        player.stop()
        currentlyPlayingIndex = -1
        results.clear()
        selected.clear()

        scope.launch {
            try {
                val found = MusicRepository.search(trimmed, limit)
                results.addAll(found)
                selected.addAll(List(found.size) { false })
                statusText = if (found.isNotEmpty()) {
                    "Знайдено: ${found.size} треків."
                } else {
                    "Нічого не знайдено."
                }
            } catch (e: Exception) {
                statusText = "Помилка пошуку: ${e.message}"
            } finally {
                isSearching = false
            }
        }
    }

    fun togglePreview(index: Int, item: SearchResult) {
        if (currentlyPlayingIndex == index) {
            // Повторне натискання на той самий трек - зупиняємо.
            player.stop()
            currentlyPlayingIndex = -1
            return
        }

        player.stop()
        currentlyPlayingIndex = index
        isBuffering = true
        statusText = "Отримую аудіо-потік для: ${item.title}..."

        scope.launch {
            try {
                // Таймаут - якщо мережевий запит "зависне" назавжди, ми
                // побачимо про це чесне повідомлення замість вічного
                // індикатора завантаження без жодної реакції.
                val audioStream = withTimeout(15000) {
                    MusicRepository.getBestAudioStream(item.url)
                }
                val streamUrl = audioStream?.content

                if (streamUrl == null) {
                    statusText = "Не вдалося отримати аудіо для прослуховування (порожній потік)."
                    currentlyPlayingIndex = -1
                } else {
                    statusText = "Відтворюю (${audioStream.format?.name ?: "?"}, ${audioStream.averageBitrate} kbps)..."
                    val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(streamUrl))
                    player.setMediaSource(mediaSource)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                statusText = "Помилка відтворення: ${e::class.simpleName}: ${e.message}"
                currentlyPlayingIndex = -1
            } finally {
                isBuffering = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "\uD83C\uDFB5 Music Downloader",
            style = MaterialTheme.typography.headlineSmall
        )

        // --- Рядок пошуку: поле вводу + лічильник + кнопка-лупа ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = {
                    Text(
                        "Назва пісні або виконавця",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                enabled = !isSearching,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
            )

            Spacer(width = 8.dp)

            Box {
                OutlinedButton(
                    onClick = { limitMenuExpanded = true },
                    enabled = !isSearching,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("$limit")
                }
                DropdownMenu(
                    expanded = limitMenuExpanded,
                    onDismissRequest = { limitMenuExpanded = false }
                ) {
                    listOf(10, 25, 100, 500).forEach { option ->
                        DropdownMenuItem(
                            text = { Text("$option") },
                            onClick = {
                                limit = option
                                limitMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(width = 8.dp)

            IconButton(
                onClick = { runSearch() },
                enabled = !isSearching,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = DeepOrange,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Filled.Search, contentDescription = "Шукати")
            }
        }

        // --- Список результатів - займає все вільне місце ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn {
                    items(results.size) { index ->
                        val item = results[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.getOrElse(index) { false },
                                onCheckedChange = { checked -> selected[index] = checked }
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 4.dp)
                            ) {
                                Text(
                                    text = item.displayTitle,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = item.durationText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }

                            // Кнопка програвання у контейнері фіксованої
                            // ширини - той самий урок з Flet-версії, щоб
                            // вона не накладалась на текст назви.
                            Box(modifier = Modifier.width(48.dp)) {
                                if (isBuffering && currentlyPlayingIndex == index) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .width(24.dp)
                                            .height(24.dp)
                                    )
                                } else {
                                    IconButton(onClick = { togglePreview(index, item) }) {
                                        Icon(
                                            if (currentlyPlayingIndex == index) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                                            contentDescription = "Прослухати"
                                        )
                                    }
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        // --- Низ екрана: кнопки + статус ---
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { for (i in selected.indices) selected[i] = true },
                modifier = Modifier.weight(1f)
            ) { Text("Все") }

            Spacer(width = 8.dp)

            OutlinedButton(
                onClick = { for (i in selected.indices) selected[i] = false },
                modifier = Modifier.weight(1f)
            ) { Text("Скинути") }
        }

        Button(
            onClick = { /* завантаження додамо у Фазі 4 */ },
            enabled = selected.any { it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepOrange)
        ) {
            Icon(Icons.Filled.Download, contentDescription = null)
            Text("  Завантажити обране", color = Color.White)
        }

        Divider(modifier = Modifier.padding(vertical = 8.dp))
        Text(text = statusText, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Spacer(width: Dp) {
    Box(modifier = Modifier.width(width))
}
