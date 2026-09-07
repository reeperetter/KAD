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

object DownloadManager {

    private val client = OkHttpClient.Builder().build()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(150).ifBlank { "track" }

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
                ?: return@withContext false
            val streamUrl = audioStream.content ?: return@withContext false

            val rawExtension = when (audioStream.format) {
                org.schabi.newpipe.extractor.MediaFormat.M4A -> "m4a"
                org.schabi.newpipe.extractor.MediaFormat.WEBMA,
                org.schabi.newpipe.extractor.MediaFormat.WEBMA_OPUS -> "webm"
                org.schabi.newpipe.extractor.MediaFormat.MP3 -> "mp3"
                else -> "audio"
            }
            rawFile = File(cacheDir, "raw_$timestamp.$rawExtension")
            mp3File = File(cacheDir, "converted_$timestamp.mp3")

            // 1. Завантажуємо сирий аудіо-файл
            onProgress("Завантажую: ${item.title}")
            val request = Request.Builder()
                .url(streamUrl)
                .header("User-Agent", NETWORK_USER_AGENT)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val body = response.body ?: return@withContext false
                rawFile.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
            }

            if (!rawFile.exists() || rawFile.length() == 0L) return@withContext false

            // 2. Конвертуємо в mp3 через ffmpeg-kit (192 kbps, стерео)
            onProgress("Конвертую в mp3: ${item.title}")
            val session = FFmpegKit.execute(
                "-y -i \"${rawFile.absolutePath}\" -vn -ar 44100 -ac 2 -b:a 192k \"${mp3File.absolutePath}\""
            )

            if (!ReturnCode.isSuccess(session.returnCode)) {
                return@withContext false
            }
            if (!mp3File.exists() || mp3File.length() == 0L) return@withContext false

            // 3. Зберігаємо в публічну папку "Завантаження"
            onProgress("Зберігаю: ${item.title}")
            val fileName = sanitizeFileName(item.title) + ".mp3"
            saveToPublicDownloads(context, mp3File, fileName)
        } catch (e: Exception) {
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
