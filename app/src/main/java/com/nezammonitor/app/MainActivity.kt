package com.nezammonitor.app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.nezammonitor.app.ui.NezamMonitorApp
import com.nezammonitor.app.ui.screens.settings.AppPreferences
import com.nezammonitor.app.ui.theme.NezamMonitorTheme
import com.nezammonitor.app.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request MANAGE_EXTERNAL_STORAGE for Android 11+
        requestStoragePermission()

        setContent {
            val context = LocalContext.current

            // Reactive preferences: Settings saves to the same SharedPreferences
            // file, the listener reloads them, and the theme / RTL direction
            // recompose instantly — no restart, no data reload.
            // (Previously `remember { load() }` read the prefs once, so a
            // theme change in Settings only took effect after a restart.)
            var prefs by remember { mutableStateOf(AppPreferences.load(context)) }
            DisposableEffect(context) {
                val sharedPrefs = context.getSharedPreferences(
                    "nezam_settings", Context.MODE_PRIVATE
                )
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                        prefs = AppPreferences.load(context)
                    }
                sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose {
                    sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
                }
            }

            // themeMode is unchanged from before: 0 = light, 1 = dark, 2 = system.
            val darkTheme = when (prefs.themeMode) {
                1 -> true
                2 -> isSystemInDarkTheme()
                else -> false
            }

            val layoutDirection = if (prefs.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                NezamMonitorTheme(
                    appTheme = prefs.appTheme,
                    darkTheme = darkTheme,
                    fontScale = prefs.fontScale
                ) {
                    NezamMonitorApp(viewModel = viewModel)
                }
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (_: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }
}
