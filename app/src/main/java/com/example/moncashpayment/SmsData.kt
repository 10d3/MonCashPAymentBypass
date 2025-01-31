package com.example.moncashpayment

import com.google.gson.annotations.SerializedName

data class SmsData(
    @SerializedName("sender") val sender: String,
    @SerializedName("message") val message: String,
    @SerializedName("timestamp") val timestamp: Long = System.currentTimeMillis()
)
