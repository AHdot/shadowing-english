package com.ahdot.movieshadow100

object SubtitleMerger {
    /** Align separate subtitle tracks by maximum time-interval overlap. */
    fun merge(english: List<SubtitleCue>, chinese: List<SubtitleCue>, minimumOverlapRatio: Double = 0.20): List<SubtitleCue> {
        require(minimumOverlapRatio in 0.0..1.0)
        val zhSorted = chinese.sortedBy { it.startMs }
        return english.sortedBy { it.startMs }.map { en ->
            val duration = (en.endMs - en.startMs).coerceAtLeast(1L)
            val best = zhSorted.asSequence()
                .filter { it.endMs > en.startMs && it.startMs < en.endMs }
                .map { zh ->
                    val overlap = (minOf(en.endMs, zh.endMs) - maxOf(en.startMs, zh.startMs)).coerceAtLeast(0L)
                    zh to overlap
                }.maxByOrNull { it.second }
            val translation = best?.takeIf { it.second.toDouble() / duration >= minimumOverlapRatio }?.first?.english
            en.copy(chinese = translation)
        }
    }
}
