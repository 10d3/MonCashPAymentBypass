package com.example.moncashpayment

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface SmsApiService {
    @POST("transactions")
    suspend fun sendSmsToBackend(@Body transaction: ParsedMonCashSms): Response<ApiResponse>
}