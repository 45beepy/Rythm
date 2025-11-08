package com.example.rythm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
// import androidx.compose.foundation.layout.weight // <-- IT IS GONE
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun StatsScreen() {
    val database = StatsDatabase.getDatabase(LocalContext.current)
    val viewModel: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(database.songStatDao())
    )
    val statsList by viewModel.allStats.collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // A simple header row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween // <-- WORKAROUND
            ) {
                Text(
                    text = "Song",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                    // modifier = Modifier.weight(1f) // <-- REMOVED
                )
                Text(
                    text = "Plays",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // The main list of stats
        items(statsList) { stat ->
            StatsListItem(stat = stat)
        }
    }
}

// This is the Composable for a single row in our stats list
@Composable
fun StatsListItem(stat: SongStat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column { // <-- MODIFIER REMOVED
            Text(
                text = stat.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1
            )
            Text(
                text = stat.artist,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stat.playCount.toString(),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}