package com.example.rythm

// These data classes match the JSON structure from the LRCLIB API.
// We only include the fields we care about.

data class LrcLibResponse(
    val syncedLyrics: String? // This is the .lrc text we want!
)