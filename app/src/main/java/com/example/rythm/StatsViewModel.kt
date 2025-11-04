package com.example.rythm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.flow.Flow

// This is the ViewModel's "Factory".
// Its only job is to know how to create a StatsViewModel
// by passing it the 'dao'.
class StatsViewModelFactory(
    private val dao: SongStatDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StatsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StatsViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// This is our ViewModel.
// Notice its constructor takes the 'dao' as a parameter.
class StatsViewModel(
    private val dao: SongStatDao
) : ViewModel() {

    // This is the "nook and corner".
    // We get the "Flow" of stats from the DAO. A Flow is a
    // "live stream" of data. When the data changes in the
    // database, this 'allStats' stream will automatically
    // emit the new list to our UI.
    val allStats: Flow<List<SongStat>> = dao.getAllStats()
}