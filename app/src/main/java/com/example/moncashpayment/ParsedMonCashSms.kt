package com.example.moncashpayment

data class ParsedMonCashSms(
    val amount: String,
    val senderNumber: String,
    val transactionId: String,
    val serviceProvider: String
)