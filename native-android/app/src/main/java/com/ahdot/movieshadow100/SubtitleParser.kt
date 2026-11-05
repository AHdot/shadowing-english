package com.ahdot.movieshadow100

data class SubtitleCue(val startMs: Long, val endMs: Long, val english: String, val chinese: String? = null)

object SubtitleParser {
    fun parse(raw: String): List<SubtitleCue> {
        val text = raw.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        return when {
            text.contains("[Events]", true) || text.lineSequence().any { it.startsWith("Dialogue:", true) } -> parseAss(text)
            text.contains("-->") -> parseSrtVtt(text)
            else -> emptyList()
        }.sortedBy { it.startMs }
    }

    fun parseSingleLanguage(raw: String): List<SubtitleCue> = parse(raw).map {
        it.copy(english = it.english.replace(Regex("\\s*\\n\\s*"), " ").trim(), chinese = null)
    }

    private fun parseSrtVtt(text: String): List<SubtitleCue> =
        text.split(Regex("\\n[\\t ]*\\n+")).mapNotNull { block ->
            val lines = block.lines()
            val timeIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeIndex < 0) return@mapNotNull null
            val times = lines[timeIndex].split("-->", limit = 2)
            if (times.size != 2) return@mapNotNull null
            val start = parseTimestamp(times[0]) ?: return@mapNotNull null
            val end = parseTimestamp(times[1].trim().substringBefore(' ')) ?: return@mapNotNull null
            val body = lines.drop(timeIndex + 1).joinToString("\n") { stripMarkup(it) }.trim()
            if (body.isBlank() || end <= start) null else SubtitleCue(start, end, body)
        }

    private fun parseAss(text: String): List<SubtitleCue> {
        var fields = listOf("layer", "start", "end", "style", "name", "marginl", "marginr", "marginv", "effect", "text")
        val result = mutableListOf<SubtitleCue>()
        text.lineSequence().forEach { line ->
            if (line.startsWith("Format:", true)) {
                fields = line.substringAfter(':').split(',').map { it.trim().lowercase() }
            } else if (line.startsWith("Dialogue:", true)) {
                val si = fields.indexOf("start").takeIf { it >= 0 } ?: 1
                val ei = fields.indexOf("end").takeIf { it >= 0 } ?: 2
                val ti = fields.indexOf("text").takeIf { it >= 0 } ?: fields.lastIndex
                val parts = line.substringAfter(':').trim().split(',', limit = ti + 1)
                val start = parts.getOrNull(si)?.let(::parseAssTimestamp) ?: return@forEach
                val end = parts.getOrNull(ei)?.let(::parseAssTimestamp) ?: return@forEach
                val body = parts.getOrNull(ti)?.replace(Regex("\\{[^}]*}"), "")
                    ?.replace("\\N", "\n")?.replace("\\n", "\n")?.trim() ?: return@forEach
                if (body.isNotBlank() && end > start) result += SubtitleCue(start, end, body)
            }
        }
        return result
    }

    private fun stripMarkup(value: String) = value.replace(Regex("<[^>]+>"), "").replace(Regex("\\{[^}]*}"), "").trim()
    private fun parseTimestamp(value: String): Long? {
        val p = value.trim().replace(',', '.').split(':')
        if (p.size !in 2..3) return null
        return runCatching {
            val sec = p.last().toDouble()
            val min = p[p.size - 2].toLong()
            val hour = if (p.size == 3) p[0].toLong() else 0L
            ((hour * 3600 + min * 60) * 1000 + (sec * 1000).toLong())
        }.getOrNull()
    }
    private fun parseAssTimestamp(value: String): Long? {
        val p = value.trim().split(':')
        if (p.size != 3) return null
        return runCatching { p[0].toLong() * 3600000 + p[1].toLong() * 60000 + (p[2].toDouble() * 1000).toLong() }.getOrNull()
    }
}
