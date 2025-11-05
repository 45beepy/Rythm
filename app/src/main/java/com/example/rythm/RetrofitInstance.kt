package com.example.rythm

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

// This object creates and holds our single instance of the LrcLibService
object RetrofitInstance {

    val api: LrcLibService by lazy {
        Retrofit.Builder()
            .baseUrl(LrcLibService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibService::class.java)
    }
}