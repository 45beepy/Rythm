package com.example.rythm

import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibService {

    // This is the "base URL" for all our calls
    companion object {
        const val BASE_URL = "https://lrclib.net/"
    }

    // This defines our "getLyrics" function
    @GET("api/get") // It's a GET request to the /api/get endpoint
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") track: String
    ): LrcLibResponse // It will return the LrcLibResponse we just defined
}