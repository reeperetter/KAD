package com.musicdownloader.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

object MusicRepository {

    private var initialized = false

    fun ensureInitialized() {
        if (!initialized) {
            NewPipe.init(NewPipeDownloaderImpl.create())
            initialized = true
        }
    }

    /**
     * Шукає треки на YouTube. Виконується у фоновому потоці (Dispatchers.IO),
     * бо і мережеві запити, і розбір HTML - це блокуючі операції.
     */
    suspend fun search(query: String, limit: Int): List<SearchResult> = withContext(Dispatchers.IO) {
        ensureInitialized()

        val service = ServiceList.YouTube
        val searchExtractor = service.getSearchExtractor("$query audio")
        searchExtractor.fetchPage()

        val items = searchExtractor.initialPage.items

        items
            .filterIsInstance<StreamInfoItem>()
            .take(limit)
            .map { item ->
                SearchResult(
                    title = item.name ?: "Невідома назва",
                    url = item.url,
                    durationSeconds = item.duration,
                    channel = item.uploaderName ?: ""
                )
            }
    }

    /**
     * Дістає пряме посилання на найкращий доступний аудіо-потік для
     * відтворення/завантаження. Аналог format="bestaudio" у yt-dlp.
     */
    suspend fun getBestAudioStream(videoUrl: String): AudioStream? = withContext(Dispatchers.IO) {
        ensureInitialized()

        val streamInfo = StreamInfo.getInfo(ServiceList.YouTube, videoUrl)
        streamInfo.audioStreams
            .filter { it.content != null }
            .maxByOrNull { it.averageBitrate }
    }
}
