package com.example.moncashpayment

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Telephony
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver(
    private val onSmsReceived: (ParsedMonCashSms) -> Unit,
    private val preferences: SharedPreferences
) : BroadcastReceiver() {

    // Updated regex patterns with better matching
    private val monCashPattern = Regex(
        """You have received (?:G)?([\d,]+(?:\.\d{2})?) with MonCash from (\d+). Txn ID: (\d+)""",
        RegexOption.IGNORE_CASE
    )

    private val natCashPattern = Regex(
        """resevwa\s([\d,]+)\sHTG\snan\s.*?(\d{8,15})\s.*?Transcode:\s*(\d+)""",
        RegexOption.IGNORE_CASE
    )


    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

            messages?.forEach { sms ->
                val sender = sms.displayOriginatingAddress ?: ""
                val messageBody = sms.displayMessageBody ?: ""

                Log.d("SmsReceiver", "=== New SMS Received ===")
                Log.d("SmsReceiver", "Sender: $sender")
                Log.d("SmsReceiver", "Body: $messageBody")

                when {
                    sender.contains("Mon Cash", true) -> {
                        Log.d("SmsReceiver", "Processing as Mon Cash message")
                        handleMonCash(messageBody, sender)
                    }
                    sender.contains("Natcash", true) -> {
                        Log.d("SmsReceiver", "Processing as NatCash message")
                        handleNatCash(messageBody, sender)
                    }
                    else -> Log.d("SmsReceiver", "Unknown sender type")
                }
            }
        }
    }

    private fun handleNatCash(messageBody: String, sender: String) {
        try {
            val matchResult = natCashPattern.find(messageBody)

            if (matchResult != null) {
                val (rawAmount, senderNumber, txnId) = matchResult.destructured

                // Clean the amount by removing commas
                val cleanAmount = rawAmount.replace(",", "")

                processValidSms(
                    amount = cleanAmount,
                    senderNumber = senderNumber,
                    txnId = txnId,
                    provider = "NatCash ($sender)"
                )
            } else {
                Log.e("SmsReceiver", "Failed to parse NatCash message")
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "NatCash processing error", e)
        }
    }


    private fun extractSenderNumber(message: String): String? {
        return Regex("""\b(\d{8,15})\b""").find(message)?.groupValues?.get(1)
    }


    private fun handleMonCash(messageBody: String, sender: String) {
        val matchResult = monCashPattern.find(messageBody)
        if (matchResult != null) {
            val (amount, senderNumber, txnId) = matchResult.destructured
            processValidSms(
                amount = amount.replace(",", ""),  // Removes commas from formatted numbers
                senderNumber = senderNumber,
                txnId = txnId,
                provider = "MonCash ($sender)"
            )
        }
    }

//    private fun handleNatCash(messageBody: String, sender: String) {
//        val matchResult = natCashPattern.find(messageBody)
//        if (matchResult != null) {
//            val (senderNumber, contenuValue, txnId) = matchResult.destructured
//            val cleanAmount = contenuValue.replace(",", "")
//            processValidSms(
//                amount = "$cleanAmount.00",
//                senderNumber = senderNumber,
//                txnId = txnId,
//                provider = "NatCash ($sender)"
//            )
//        }
//    }

    private fun processValidSms(amount: String, senderNumber: String, txnId: String, provider: String) {
        val parsedSms = ParsedMonCashSms(
            amount = amount,
            senderNumber = senderNumber,
            transactionId = txnId,
            serviceProvider = provider
        )

        CoroutineScope(Dispatchers.Main).launch {
            onSmsReceived(parsedSms)
        }

        val backendUrl = preferences.getString("backend_url", "http://localhost:3000/api/") ?: ""
        if (backendUrl.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    RetrofitClient.getInstance(backendUrl)
                        .sendSmsToBackend(parsedSms)
                    Log.d("SmsReceiver", "Successfully sent to backend")
                } catch (e: Exception) {
                    Log.e("SmsReceiver", "Backend error", e)
                }
            }
        }
    }
}