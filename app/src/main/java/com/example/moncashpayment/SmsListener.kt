package com.example.moncashpayment

import android.content.Context
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun SmsListener(
    onMessageReceived: (ParsedMonCashSms) -> Unit,
    settingsManager: SettingsManager
) {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val smsReceiver = SmsReceiver(
            onSmsReceived = onMessageReceived,
            preferences = settingsManager.preferences
        )
        val filter = IntentFilter("android.provider.Telephony.SMS_RECEIVED").apply {
            priority = Int.MAX_VALUE
        }

        context.registerReceiver(smsReceiver, filter)

        onDispose {
            context.unregisterReceiver(smsReceiver)
        }
    }
}