package com.reeperetter.sonicsnag

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object DownloadManager {

    // Таймаути ловлять лише справжнє "зависання" з'єднання (немає нових
    // даних довше вказаного часу) - вони НЕ обмежують загальний розмір чи
    // тривалість файлу, тож довгі збірки якісно завантажуються.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "track" }

    // Розмір одного "шматка" при завантаженні частинами. YouTube нерідко
    // штучно обмежує швидкість (throttling) для одного суцільного запиту
    // без Range-заголовків - реальні застосунки й завантажувачі якраз
    // тому й тягнуть файл частинами. 10 МБ - розумний баланс між
    // кількістю запитів і уникненням троттлінгу.
    private const val CHUNK_SIZE = 10L * 1024 * 1024

    /**
     * Завантажує файл частинами через Range-запити замість одного суцільного
     * GET. Без цього YouTube нерідко обмежує швидкість до приблизно
     * реальної швидкості відтворення - тобто трихвилинна пісня якісно
     * скачається саме близько трьох хвилин, а не за кілька секунд.
     */
    private fun downloadInChunks(url: String, outputFile: File): Boolean {
        val headRequest = Request.Builder()
            .url(url)
            .header("User-Agent", NETWORK_USER_AGENT)
            .head()
            .build()

        val totalSize = client.newCall(headRequest).execute().use { response ->
            if (!response.isSuccessful) return@use -1L
            response.header("Content-Length")?.toLongOrNull() ?: -1L
        }

        outputFile.outputStream().use { output ->
            if (totalSize <= 0) {
                // Сервер не повідомив розмір - завантажуємо звичайним
                // способом (рідкісний випадок).
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", NETWORK_USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return false
                    val body = response.body ?: return false
                    body.byteStream().copyTo(output)
                }
                return true
            }

            var start = 0L
            while (start < totalSize) {
                val end = minOf(start + CHUNK_SIZE - 1, totalSize - 1)
                val rangeRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", NETWORK_USER_AGENT)
                    .header("Range", "bytes=$start-$end")
                    .build()

                client.newCall(rangeRequest).execute().use { response ->
                    // 206 Partial Content - очікувана відповідь на Range-запит
                    if (!response.isSuccessful) return false
                    val body = response.body ?: return false
                    body.byteStream().copyTo(output)
                }
                start = end + 1
            }
        }
        return true
    }

    /**
     * Повний конвеєр для одного треку: завантажити сирий аудіо-потік ->
     * сконвертувати в mp3 (ffmpeg-kit) -> зберегти в публічну папку
     * "Завантаження" через MediaStore. onProgress викликається на кожному
     * етапі, щоб інтерфейс міг чесно показати, що саме зараз відбувається.
     */
    suspend fun downloadTrack(
        context: Context,
        item: SearchResult,
        onProgress: (String) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val timestamp = System.currentTimeMillis()
        var rawFile: File? = null
        var mp3File: File? = null

        try {
            onProgress("Отримую аудіо-потік: ${item.title}")
            val audioStream = MusicRepository.getBestAudioStream(item.url)
            if (audioStream == null) {
                onProgress("Не вдалось знайти аудіо-потік для: ${item.title}")
                return@withContext false
            }
            val streamUrl = audioStream.content
            if (streamUrl == null) {
                onProgress("Порожнє посилання на потік для: ${item.title}")
                return@withContext false
            }

            val rawExtension = when (audioStream.format) {
                org.schabi.newpipe.extractor.MediaFormat.M4A -> "m4a"
                org.schabi.newpipe.extractor.MediaFormat.WEBMA,
                org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS -> "webm"
                org.schabi.newpipe.extractor.MediaFormat.MP3 -> "mp3"
                else -> "audio"
            }
            rawFile = File(cacheDir, "raw_$timestamp.$rawExtension")
            mp3File = File(cacheDir, "converted_$timestamp.mp3")

            // 1. Завантажуємо сирий аудіо-файл (частинами - див. коментар
            // біля downloadInChunks щодо троттлінгу)
            onProgress("Завантажую: ${item.title}")
            val downloadOk = downloadInChunks(streamUrl, rawFile)
            if (!downloadOk) {
                onProgress("Мережева помилка при завантаженні: ${item.title}")
                return@withContext false
            }

            if (!rawFile.exists() || rawFile.length() == 0L) {
                onProgress("Завантажений файл порожній: ${item.title}")
                return@withContext false
            }

            // 2. Конвертуємо в mp3 через ffmpeg-kit (192 kbps, стерео)
            onProgress("Конвертую в mp3: ${item.title}")
            val session = FFmpegKit.execute(
                "-y -i \"${rawFile.absolutePath}\" -vn -ar 44100 -ac 2 -b:a 192k \"${mp3File.absolutePath}\""
            )

            if (!ReturnCode.isSuccess(session.returnCode)) {
                onProgress("ffmpeg не зміг сконвертувати: ${item.title} (код ${session.returnCode})")
                return@withContext false
            }
            if (!mp3File.exists() || mp3File.length() == 0L) {
                onProgress("Файл після конвертації порожній: ${item.title}")
                return@withContext false
            }

            // 3. Зберігаємо в публічну папку "Завантаження"
            onProgress("Зберігаю: ${item.title}")
            val fileName = sanitizeFileName(item.title) + ".mp3"
            val saved = saveToPublicDownloads(context, mp3File, fileName)
            if (!saved) {
                onProgress("Не вдалось зберегти файл: ${item.title}")
            }
            saved
        } catch (e: Exception) {
            onProgress("Помилка (${e::class.simpleName}) для ${item.title}: ${e.message}")
            false
        } finally {
            rawFile?.delete()
            mp3File?.delete()
        }
    }

    private fun saveToPublicDownloads(context: Context, sourceFile: File, displayName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ - через MediaStore, дозволів не потрібно,
                // застосунок пише лише свої власні записи.
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                    put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return false

                resolver.openOutputStream(uri)?.use { output ->
                    sourceFile.inputStream().use { input -> input.copyTo(output) }
                } ?: return false

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                true
            } else {
                // Android 9 і старіші - пряме записування у файлову систему.
                // Примітка: на цих версіях WRITE_EXTERNAL_STORAGE є
                // "небезпечним" дозволом, який теоретично вимагає runtime-
                // запиту (ActivityCompat.requestPermissions). Оскільки
                // цільові тестові пристрої - Android 11+, тут спрощено;
                // якщо знадобиться підтримка Android 9 і старіших повноцінно -
                // треба буде додати цей запит.
                @Suppress("DEPRECATION")
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                val destFile = File(downloadsDir, displayName)
                sourceFile.copyTo(destFile, overwrite = true)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
