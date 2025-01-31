package com.example.moncashpayment

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {
    val preferences: SharedPreferences =
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    var backendUrl: String
        get() = preferences.getString("backend_url", "") ?: ""
        set(value) = preferences.edit().putString("backend_url", value).apply()
}