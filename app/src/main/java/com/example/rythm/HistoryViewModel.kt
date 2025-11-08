package com.example.rythm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// --- 1. The ViewModel ---
class HistoryViewModel(
    private val historyDao: PlaybackHistoryDao
) : ViewModel() {

    // Get the "live stream" of all history items
    val fullHistory: Flow<List<PlaybackHistory>> = historyDao.getFullHistory()
}

// --- 2. The Factory ---
class HistoryViewModelFactory(
    private val dao: PlaybackHistoryDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- 3. Helper Functions ---

// Formats a timestamp (like 1678886400000) into "Mar 15, 2023"
fun Long.formatDate(): String {
    val date = Date(this)
    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return format.format(date)
}

// Checks if two timestamps are on different calendar days
fun isNewDay(timestamp1: Long, timestamp2: Long): Boolean {
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = timestamp2 }
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR) ||
            cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
}