package com.example.moncashpayment

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private var instance: SmsApiService? = null
    private var currentBaseUrl: String? = null

    fun getInstance(baseUrl: String): SmsApiService {
        if (instance == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            instance = retrofit.create(SmsApiService::class.java)
        }
        return instance!!
    }
}