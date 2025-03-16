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
        """Vous avez re.u (\d+) HTG de .+?(\d{8}).+?TransCode: (\d+)""",
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
            Log.d("SmsReceiver", "Attempting to parse NatCash message: $messageBody")

            // Extract amount - using a more flexible pattern that works with encoding issues
            val amountPattern = Regex("""avez re.{1,3}u (\d+) HTG""", RegexOption.IGNORE_CASE)
            val amountMatch = amountPattern.find(messageBody)
            val amount = amountMatch?.groupValues?.get(1)

            // Extract phone number - looking for 8 digits
            val phonePattern = Regex("""(\d{8})""")
            val phoneMatch = phonePattern.find(messageBody)
            val phone = phoneMatch?.groupValues?.get(1)

            // Extract transaction code
            val txnPattern = Regex("""TransCode: (\d+)""", RegexOption.IGNORE_CASE)
            val txnMatch = txnPattern.find(messageBody)
            val txnId = txnMatch?.groupValues?.get(1)

            Log.d("SmsReceiver", "Extracted - Amount: $amount, Phone: $phone, TxnId: $txnId")

            if (amount != null && phone != null && txnId != null) {
                processValidSms(
                    amount = amount,
                    senderNumber = phone,
                    txnId = txnId,
                    provider = "Natcash ($sender)"
                )
                Log.d("SmsReceiver", "Successfully processed NatCash message")
            } else {
                Log.e("SmsReceiver", "Failed to extract all required fields from NatCash message")
                if (amount == null) Log.e("SmsReceiver", "Amount extraction failed")
                if (phone == null) Log.e("SmsReceiver", "Phone extraction failed")
                if (txnId == null) Log.e("SmsReceiver", "Transaction ID extraction failed")
            }
        } catch (e: Exception) {
            Log.e("SmsReceiver", "NatCash processing error", e)
            e.printStackTrace()
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