package com.musicdownloader.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Теплі кольори - ті самі, що були в попередній (Flet) версії застосунку
private val DeepOrange = Color(0xFFFF5722)
private val WarmBackground = Color(0xFFFFF3E0)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Перемикаємось із теми екрана завантаження (темно-синьої) на
        // звичайну теплу тему ДО того, як Compose намалює перший кадр.
        setTheme(R.style.Theme_MusicDownloader)
        super.onCreate(savedInstanceState)

        setContent {
            MusicDownloaderApp()
        }
    }
}

@Composable
fun MusicDownloaderApp() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = WarmBackground
        ) {
            SearchScreen()
        }
    }
}

@Composable
fun SearchScreen() {
    // Це поки що "заглушка" - реальний пошук через NewPipeExtractor
    // додамо в наступній фазі. Мета цього кроку - переконатись, що
    // застосунок взагалі збирається і запускається на телефоні.
    var query by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = "\uD83C\uDFB5 Music Downloader",
            style = MaterialTheme.typography.headlineSmall
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Назва пісні або виконавця") },
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = { /* пошук буде додано у Фазі 2 */ },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DeepOrange)
        ) {
            Icon(Icons.Filled.Search, contentDescription = "Шукати")
            Text(text = "  Шукати", color = Color.White)
        }
    }
}
