package com.example.rythm

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val database = StatsDatabase.getDatabase(LocalContext.current)

    val viewModel: HistoryViewModel = viewModel(
        factory = HistoryViewModelFactory(database.playbackHistoryDao())
    )

    val historyList by viewModel.fullHistory.collectAsState(initial = null)

    if (historyList == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (historyList!!.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No playback history yet.")
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Loop through the list with an index
            itemsIndexed(historyList!!) { index, historyItem ->
                var showHeader = false
                if (index == 0) {
                    // Always show header for the very first item
                    showHeader = true
                } else {
                    // Check if this item is on a new day
                    val prevTimestamp = historyList!![index - 1].timestamp
                    showHeader = isNewDay(historyItem.timestamp, prevTimestamp)
                }

                // If it's a new day, show the date header
                if (showHeader) {
                    Text(
                        text = historyItem.timestamp.formatDate(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }

                // Show the song item
                HistoryListItem(item = historyItem)
            }
        }
    }
}

@Composable
fun HistoryListItem(item: PlaybackHistory) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        Text(
            text = item.artist,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}