package com.reeperetter.sonicsnag

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Виконує завантаження й конвертацію треків як foreground-сервіс зі
 * сповіщенням. На відміну від корутини, прив'язаної просто до екрана
 * (Activity), Android не зупиняє такий сервіс, коли застосунок згорнутий -
 * саме це вирішує проблему "завантаження ламається у фоні".
 */
class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        private const val CHANNEL_ID = "sonicsnag_downloads"
        private const val NOTIFICATION_ID = 1001

        // Стан, який MainActivity читає, поки застосунок видимий - сервіс
        // працює незалежно від того, дивиться хтось на екран чи ні.
        val statusFlow = MutableStateFlow("")
        val isRunningFlow = MutableStateFlow(false)

        // Черга треків для завантаження. Проєкт однопроцесний, тому просто
        // передаємо список у пам'яті замість серіалізації через Intent.
        var pendingTracks: List<SearchResult> = emptyList()

        fun start(context: Context, tracks: List<SearchResult>) {
            pendingTracks = tracks
            val intent = Intent(context, DownloadService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val tracks = pendingTracks
        if (tracks.isEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Підготовка...", 0, tracks.size))
        isRunningFlow.value = true

        serviceScope.launch {
            var successCount = 0
            val failures = mutableListOf<String>()

            tracks.forEachIndexed { i, item ->
                val progressPrefix = "Трек ${i + 1}/${tracks.size}: ${item.title}"
                statusFlow.value = progressPrefix
                notify(buildNotification(progressPrefix, i, tracks.size))

                var lastMessage = ""
                val ok = DownloadManager.downloadTrack(applicationContext, item) { message ->
                    lastMessage = message
                    val text = "(${i + 1}/${tracks.size}) $message"
                    statusFlow.value = text
                    notify(buildNotification(text, i, tracks.size))
                }

                if (ok) successCount++ else failures.add("${item.title}: $lastMessage")

                // Невелика пауза між треками - без неї часті запити один за
                // одним до YouTube іноді призводять до випадкових відмов.
                if (i < tracks.lastIndex) delay(800)
            }

            val finalText = if (successCount == tracks.size) {
                "Готово! Збережено $successCount трек(ів) у папку \"Завантаження\"."
            } else {
                val shown = failures.take(3).joinToString(" | ")
                val more = if (failures.size > 3) " (і ще ${failures.size - 3})" else ""
                "Завершено: $successCount із ${tracks.size}. Причини: $shown$more"
            }
            statusFlow.value = finalText
            isRunningFlow.value = false

            notify(buildFinalNotification(finalText))
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Завантаження SonicSnag",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String, progress: Int, max: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SonicSnag")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(max, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun buildFinalNotification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SonicSnag")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(false)
            .build()

    private fun notify(notification: Notification) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
