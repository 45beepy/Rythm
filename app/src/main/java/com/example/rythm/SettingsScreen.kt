package com.example.rythm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen() {
    // Read the theme state from our global object
    val isDarkTheme = ThemeState.isDarkTheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Dark Mode Toggle Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    // When the row is clicked, toggle the state
                    ThemeState.isDarkTheme = !isDarkTheme
                }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Dark Mode",
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = isDarkTheme,
                onCheckedChange = {
                    // When the switch is clicked, toggle the state
                    ThemeState.isDarkTheme = it
                }
            )
        }
    }
}