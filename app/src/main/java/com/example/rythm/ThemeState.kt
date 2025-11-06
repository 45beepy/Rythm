package com.example.rythm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * A simple object to hold our app-wide theme state.
 */
object ThemeState {
    // By default, the app is in light mode.
    var isDarkTheme by mutableStateOf(false)
}