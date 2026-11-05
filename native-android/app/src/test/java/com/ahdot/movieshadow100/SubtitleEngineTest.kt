package com.ahdot.movieshadow100

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleEngineTest {
    @Test fun parsesSrtAndKeepsMultilineCueAsOneSentence() {
        val cues = SubtitleParser.parseSingleLanguage("""
            1
            00:00:01,200 --> 00:00:03,500
            I thought we could
            try again.

            2
            00:00:04,000 --> 00:00:05,000
            Come on!
        """.trimIndent())
        assertEquals(2, cues.size)
        assertEquals(1200L, cues[0].startMs)
        assertEquals("I thought we could try again.", cues[0].english)
    }

    @Test fun parsesWebVttCue() {
        val cues = SubtitleParser.parse("""
            WEBVTT

            00:00:02.000 --> 00:00:04.250
            Hello there.
        """.trimIndent())
        assertEquals(1, cues.size)
        assertEquals(2250L, cues[0].endMs - cues[0].startMs)
    }

    @Test fun mergesSeparateLanguageTracksByGreatestOverlap() {
        val en = listOf(SubtitleCue(1000, 3000, "Good morning"), SubtitleCue(4000, 6000, "How are you?"))
        val zh = listOf(SubtitleCue(900, 3100, "早上好"), SubtitleCue(4500, 6500, "你好吗"))
        val merged = SubtitleMerger.merge(en, zh)
        assertEquals("早上好", merged[0].chinese)
        assertEquals("你好吗", merged[1].chinese)
    }

    @Test fun leavesUnmatchedTranslationEmpty() {
        val merged = SubtitleMerger.merge(listOf(SubtitleCue(1000, 2000, "Good night")), listOf(SubtitleCue(5000, 6000, "晚安")))
        assertNull(merged.single().chinese)
    }
}
