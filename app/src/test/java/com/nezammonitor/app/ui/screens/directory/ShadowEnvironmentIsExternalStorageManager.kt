package com.nezammonitor.app.ui.screens.directory

import android.os.Environment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * v3.11.6, test-only: MainActivity.onCreate calls
 * Environment.isExternalStorageManager() (requestStoragePermission).
 * Under Robolectric the real method throws ArrayIndexOutOfBoundsException
 * (its storage-volumes array is empty), which fails the activity launch
 * before any test runs. This shadow answers "not granted" — the app then
 * attempts the permission intent and swallows the failure, exactly as it
 * does on a device without the grant. No app code is affected.
 */
@Implements(Environment::class)
class ShadowEnvironmentIsExternalStorageManager {
    companion object {
        @JvmStatic
        @Implementation
        fun isExternalStorageManager(): Boolean = false
    }
}
