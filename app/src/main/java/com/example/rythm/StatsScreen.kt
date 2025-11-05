package com.example.rythm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
    // 1. Get an instance of our StatsDatabase
    val database = StatsDatabase.getDatabase(LocalContext.current)

    // 2. Create our ViewModel using the Factory we made.
    //    This is the "nook and corner" for passing the DAO to the ViewModel.
    val viewModel: StatsViewModel = viewModel(
        factory = StatsViewModelFactory(database.songStatDao())
    )

    // 3. Collect the "live stream" of stats.
    //    'collectAsState' converts the Flow into a Compose State.
    //    Anytime the data changes, this 'statsList' will auto-update.
    val statsList by viewModel.allStats.collectAsState(initial = emptyList())

    // 4. The UI
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp) // Adds space between items
    ) {
        // A simple header row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "Song",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
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
        Column(modifier = Modifier.weight(1f)) {
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