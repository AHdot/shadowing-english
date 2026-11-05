package com.ahdot.movieshadow100

import android.content.Context

/**
 * Small local checkpoint store. URI grants are persisted by the Activity;
 * this class stores only references and playback metadata, never media bytes.
 */
class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("movie_shadow_100", Context.MODE_PRIVATE)

    fun saveVideo(uri: String, name: String) {
        prefs.edit().putString("video_uri", uri).putString("video_name", name).apply()
    }

    fun videoUri(): String? = prefs.getString("video_uri", null)
    fun videoName(): String = prefs.getString("video_name", "尚未导入电影") ?: "尚未导入电影"

    fun saveSubtitle(language: String, uri: String, name: String) {
        prefs.edit()
            .putString("${language}_subtitle_uri", uri)
            .putString("${language}_subtitle_name", name)
            .apply()
    }

    fun subtitleUri(language: String): String? = prefs.getString("${language}_subtitle_uri", null)
    fun subtitleName(language: String): String = prefs.getString("${language}_subtitle_name", "") ?: ""

    fun clearSubtitles() {
        prefs.edit()
            .remove("en_subtitle_uri").remove("en_subtitle_name")
            .remove("zh_subtitle_uri").remove("zh_subtitle_name")
            .apply()
    }

    fun savePosition(positionMs: Long) {
        prefs.edit().putLong("position_ms", positionMs.coerceAtLeast(0L)).apply()
    }

    fun positionMs(): Long = prefs.getLong("position_ms", 0L).coerceAtLeast(0L)
}
