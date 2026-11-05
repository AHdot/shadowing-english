package com.ahdot.movieshadow100

import android.content.Context
import java.time.LocalDate

/**
 * Local learning-time statistics.
 * A minute is counted only while the video player is actively playing.
 * The store keeps aggregate minutes and distinct study dates on this device.
 */
class LearningStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("movie_shadow_learning_stats", Context.MODE_PRIVATE)

    @Synchronized
    fun recordOneMinute(date: LocalDate = LocalDate.now()) {
        val total = (prefs.getInt(KEY_TOTAL_MINUTES, 0) + 1).coerceAtLeast(0)
        val days = prefs.getStringSet(KEY_STUDY_DAYS, emptySet()).orEmpty().toMutableSet()
        days += date.toString()
        prefs.edit()
            .putInt(KEY_TOTAL_MINUTES, total)
            .putStringSet(KEY_STUDY_DAYS, days)
            .apply()
    }

    fun totalMinutes(): Int = prefs.getInt(KEY_TOTAL_MINUTES, 0).coerceAtLeast(0)

    fun studyDays(): Int = prefs.getStringSet(KEY_STUDY_DAYS, emptySet())?.size ?: 0

    companion object {
        private const val KEY_TOTAL_MINUTES = "total_study_minutes"
        private const val KEY_STUDY_DAYS = "study_days"
    }
}
