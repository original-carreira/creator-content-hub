package com.creatorcontenthub.domain.util

object YoutubeUrlUtils {

    fun extractVideoId(url: String): String? {
        val regex = Regex("(?:v=|youtu\\.be/|shorts/)([a-zA-Z0-9_-]{11})")
        return regex.find(url)?.groupValues?.get(1)
    }
}