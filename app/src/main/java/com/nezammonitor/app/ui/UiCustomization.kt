package com.nezammonitor.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nezammonitor.app.R
import com.nezammonitor.app.ui.navigation.Screen

/**
 * Hidden-menu UI customisation (v3.8.5): menu titles / emojis / visibility,
 * the main UI texts, the display name, and the launcher icon choice.
 *
 * Everything lives in SharedPreferences "ui_custom" (never in Room, never
 * synced). Reads go through here so the hidden menu and the UI agree on the
 * keys and defaults. [revision] is read inside every helper: the read sites
 * call these helpers during composition, so bumping the revision recomposes
 * every customised text immediately — no restart needed.
 */
object UiCustomization {
    const val PREFS = "ui_custom"

    var revision by mutableStateOf(0)
        private set

    fun bump() {
        revision++
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(route: String) = route.replace("/", "_")

    // ---- Drawer menu items (drawer list only; the bottom bar is untouched) ----

    fun menuTitle(context: Context, screen: Screen): String {
        revision
        return prefs(context).getString("title_${key(screen.route)}", null)
            ?.takeIf { it.isNotBlank() } ?: screen.title
    }

    fun setMenuTitle(context: Context, screen: Screen, title: String) {
        prefs(context).edit().putString("title_${key(screen.route)}", title).apply()
        bump()
    }

    fun menuIcon(context: Context, screen: Screen): String {
        revision
        return prefs(context).getString("icon_${key(screen.route)}", null)
            ?.takeIf { it.isNotBlank() } ?: screen.icon
    }

    fun setMenuIcon(context: Context, screen: Screen, icon: String) {
        prefs(context).edit().putString("icon_${key(screen.route)}", icon).apply()
        bump()
    }

    fun isMenuVisible(context: Context, screen: Screen): Boolean {
        revision
        return prefs(context).getBoolean("visible_${key(screen.route)}", true)
    }

    fun setMenuVisible(context: Context, screen: Screen, visible: Boolean) {
        prefs(context).edit().putBoolean("visible_${key(screen.route)}", visible).apply()
        bump()
    }

    fun resetMenuItem(context: Context, screen: Screen) {
        prefs(context).edit()
            .remove("title_${key(screen.route)}")
            .remove("icon_${key(screen.route)}")
            .remove("visible_${key(screen.route)}")
            .apply()
        bump()
    }

    // ---- Main UI texts ----

    fun drawerTitle(context: Context): String {
        revision
        return prefs(context).getString("drawer_title", null)
            ?.takeIf { it.isNotBlank() } ?: "مانیتورینگ نظام مهندسی"
    }

    fun homeGreeting(context: Context): String {
        revision
        return prefs(context).getString("home_greeting", null)
            ?.takeIf { it.isNotBlank() } ?: "خوش آمدید"
    }

    fun homeBadge(context: Context): String {
        revision
        return prefs(context).getString("home_badge", null)
            ?.takeIf { it.isNotBlank() } ?: "ناظر فعال یزد"
    }

    fun homeSubtitle(context: Context): String {
        revision
        return prefs(context).getString("home_subtitle", null)
            ?.takeIf { it.isNotBlank() } ?: "سامانه جامع پایش پرونده‌ها و خدمات نظارت"
    }

    fun setText(context: Context, keyName: String, value: String) {
        prefs(context).edit().putString(keyName, value).apply()
        bump()
    }

    fun resetText(context: Context, keyName: String) {
        prefs(context).edit().remove(keyName).apply()
        bump()
    }

    // ---- Display name (display only; api_user_name is never touched) ----

    fun displayNameOverride(context: Context): String {
        revision
        return prefs(context).getString("display_name", null).orEmpty()
    }

    fun setDisplayNameOverride(context: Context, name: String) {
        prefs(context).edit().putString("display_name", name).apply()
        bump()
    }

    // ---- Launcher icon ----

    val LAUNCHER_ICONS = listOf(
        LauncherIconOption("default", "پیش‌فرض", "NezamMonitor Pro", R.mipmap.ic_launcher),
        LauncherIconOption("green", "سبز", "NezamMonitor Pro", R.mipmap.ic_launcher_green),
        LauncherIconOption("gold", "طلایی", "NezamMonitor Pro", R.mipmap.ic_launcher_gold),
        LauncherIconOption("dark", "تیره", "مانیتورینگ نظام مهندسی", R.mipmap.ic_launcher_dark)
    )

    data class LauncherIconOption(
        val key: String,
        val nameFa: String,
        val label: String,
        val resId: Int
    )

    fun launcherIconChoice(context: Context): String {
        revision
        return prefs(context).getString("launcher_icon", null)
            ?.takeIf { it in LAUNCHER_ICONS.map { o -> o.key } } ?: "default"
    }

    fun launcherIconRes(context: Context): Int {
        val choice = launcherIconChoice(context)
        return LAUNCHER_ICONS.firstOrNull { it.key == choice }?.resId
            ?: R.mipmap.ic_launcher
    }

    /** Enables exactly the chosen launcher alias and disables the other
     *  three, without killing the app. Safe to call on every launch. */
    fun applyLauncherIcon(context: Context, choice: String) {
        val aliases = mapOf(
            "default" to ".LauncherDefault",
            "green" to ".LauncherGreen",
            "gold" to ".LauncherGold",
            "dark" to ".LauncherDark"
        )
        val pm = context.packageManager
        for ((key, suffix) in aliases) {
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(context, context.packageName + suffix),
                    if (key == choice) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
                // A launcher that ignores alias state must never crash the app.
            }
        }
    }

    fun saveLauncherIcon(context: Context, choice: String) {
        prefs(context).edit().putString("launcher_icon", choice).apply()
        applyLauncherIcon(context, choice)
        bump()
    }

    /** Clears every UI customisation (menu, texts, display name, icon
     *  choice). Data overrides are deleted separately by the caller. */
    fun resetAll(context: Context) {
        prefs(context).edit().clear().apply()
        applyLauncherIcon(context, "default")
        bump()
    }
}
