package com.example.rythm

import java.util.regex.Pattern

/**
 * This data class holds one single line of a lyric.
 * @param timeInMs The time in milliseconds when this line should be shown.
 * @param text The text of the lyric line.
 */
data class LyricLine(val timeInMs: Long, val text: String)

/**
 * This object is our parser. It has one function
 * that converts a raw .lrc string into a List of LyricLines.
 */
object LyricParser {

    // This is a "Regular Expression" (regex) that finds timestamps
    // like [00:12.34] or [00:12.345]
    private val lrcTimePattern: Pattern = Pattern.compile("^\\[(\\d{2}):(\\d{2})[.:](\\d{2,3})\\](.*)")

    fun parse(lrcText: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()

        lrcText.lines().forEach { line ->
            // Check if the line matches our timestamp pattern
            val matcher = lrcTimePattern.matcher(line)
            if (matcher.find()) {
                try {
                    // 1. Extract the time parts
                    val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                    val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                    var millis = matcher.group(3)?.toLongOrNull() ?: 0L

                    // Handle 2-digit (centiseconds) vs 3-digit (milliseconds)
                    if (matcher.group(3)?.length == 2) {
                        millis *= 10
                    }

                    // 2. Extract the text
                    val text = matcher.group(4)?.trim() ?: ""

                    // 3. Convert time to total milliseconds
                    val totalTimeInMs = (minutes * 60 * 1000) + (seconds * 1000) + millis

                    // Add the new line to our list
                    if (text.isNotEmpty()) {
                        lines.add(LyricLine(totalTimeInMs, text))
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return lines
    }
}