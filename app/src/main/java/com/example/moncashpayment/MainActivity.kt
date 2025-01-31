package com.example.moncashpayment

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    private var hasSmsPermissions by mutableStateOf(false)
    private var receivedSmsList by mutableStateOf(emptyList<ParsedMonCashSms>())
    private lateinit var settingsManager: SettingsManager
    private var backendUrl by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)
        backendUrl = settingsManager.backendUrl

        createNotificationChannel()
        checkSmsPermissions()

        setContent {
            MonCashPaymentTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        SettingsPanel(
                            backendUrl = backendUrl,
                            onUrlChanged = { newUrl ->
                                backendUrl = newUrl
                                settingsManager.backendUrl = newUrl
                            }
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        if (hasSmsPermissions) {
                            SmsListener(
                                onMessageReceived = { parsedSms ->
                                    receivedSmsList = receivedSmsList + parsedSms
                                    showSmsNotification(
                                        "Received ${parsedSms.amount} from ${parsedSms.senderNumber}"
                                    )
                                },
                                settingsManager = settingsManager
                            )

                            Text(
                                "Transactions:",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(8.dp)
                            )

                            LazyColumn {
                                items(receivedSmsList) { sms ->
                                    TransactionCard(sms)
                                }
                            }
                        } else {
                            Text(
                                "SMS permissions required",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun TransactionCard(sms: ParsedMonCashSms) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    sms.serviceProvider,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Amount: ${formatAmount(sms.amount)}",
                    style = MaterialTheme.typography.bodyLarge)
                Text("From: ${sms.senderNumber}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sms.senderNumber == "N/A") MaterialTheme.colorScheme.error
                    else LocalContentColor.current)
                Text("TXN: ${sms.transactionId}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    private fun formatAmount(rawAmount: String): String {
        return try {
            val amountValue = rawAmount.toDouble()
            DecimalFormat("#,##0.00").format(amountValue)
        } catch (e: NumberFormatException) {
            rawAmount  // Return raw value if formatting fails
        }
    }

    @Composable
    private fun SettingsPanel(backendUrl: String, onUrlChanged: (String) -> Unit) {
        var url by remember { mutableStateOf(backendUrl) }

        Column(modifier = Modifier.padding(8.dp)) {
            Text("Backend Configuration", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("API Endpoint") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrect = false
                )
            )
            Button(
                onClick = { onUrlChanged(url) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
            ) {
                Text("Save Configuration")
            }
        }
    }

    private fun checkSmsPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        ).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()

        if (permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }) {
            hasSmsPermissions = true
        } else {
            requestPermissions.launch(permissions)
        }
    }

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasSmsPermissions = permissions.values.all { it }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "SMS_CHANNEL",
                "SMS Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Shows notifications for received SMS messages"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showSmsNotification(message: String) {
        val builder = NotificationCompat.Builder(this, "SMS_CHANNEL")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New Transaction Received")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(this)) {
            try {
                notify(System.currentTimeMillis().toInt(), builder.build())
            } catch (e: SecurityException) {
//                Log.e("Notification", "Notification permission not granted", e)
            }
        }
    }
}