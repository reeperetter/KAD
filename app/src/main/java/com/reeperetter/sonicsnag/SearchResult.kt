package com.reeperetter.sonicsnag

data class SearchResult(
    val title: String,
    val url: String,
    val durationSeconds: Long,
    val channel: String
) {
    val durationText: String
        get() {
            if (durationSeconds < 0) return "[LIVE]"
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                "[%d:%02d:%02d]".format(hours, minutes, seconds)
            } else {
                "[%02d:%02d]".format(minutes, seconds)
            }
        }

    val displayTitle: String
        get() = if (channel.isNotBlank() && !title.startsWith(channel)) {
            "$channel — $title"
        } else {
            title
        }
}
