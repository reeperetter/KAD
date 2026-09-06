package com.musicdownloader.app

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

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

    fun runSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            statusText = "Введіть назву треку!"
            return
        }
        isSearching = true
        statusText = "Пошук: $trimmed..."
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

    fun openPreview(url: String) {
        // Так само, як у попередній (Flet) версії - поки що відкриваємо
        // відео в YouTube/браузері. Реальний вбудований плеєр (ExoPlayer)
        // додамо в наступній фазі.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
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
                label = { Text("Назва пісні або виконавця") },
                singleLine = true,
                enabled = !isSearching,
                modifier = Modifier.weight(1f)
            )

            Spacer(width = 8.dp)

            Box {
                OutlinedButton(
                    onClick = { limitMenuExpanded = true },
                    enabled = !isSearching
                ) {
                    Text("$limit")
                }
                DropdownMenu(
                    expanded = limitMenuExpanded,
                    onDismissRequest = { limitMenuExpanded = false }
                ) {
                    listOf(10, 15, 20).forEach { option ->
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
                                IconButton(onClick = { openPreview(item.url) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Відкрити на YouTube")
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
