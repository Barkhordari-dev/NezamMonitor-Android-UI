package com.nezammonitor.app.ui.components

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import java.io.File
import java.io.FileOutputStream

/** Copy text to the clipboard and confirm with a toast. */
fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("NezamMonitor", text))
    Toast.makeText(context, "کپی شد", Toast.LENGTH_SHORT).show()
}

/** Capture the current screen into NezamMonitor/screenshots (PNG) and toast
 *  the result. PixelCopy needs the Activity's window; a short delay lets an
 *  open menu close first so it is not in the shot. */
fun takeScreenshot(context: Context) {
    val activity = context as? Activity ?: run {
        Toast.makeText(context, "اسکرین شات ممکن نیست", Toast.LENGTH_SHORT).show()
        return
    }
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            val view = activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            PixelCopy.request(activity.window, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    try {
                        val dir = File("/storage/emulated/0/NezamMonitor/screenshots").apply { mkdirs() }
                        val file = File(dir, "screenshot-${System.currentTimeMillis()}.png")
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        Toast.makeText(context, "✅ اسکرین شات ذخیره شد", Toast.LENGTH_SHORT).show()
                    } catch (_: Exception) {
                        Toast.makeText(context, "❌ ذخیرهٔ اسکرین شات ناموفق بود", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(context, "❌ اسکرین شات ناموفق بود", Toast.LENGTH_SHORT).show()
                }
            }, Handler(Looper.getMainLooper()))
        } catch (_: Exception) {
            Toast.makeText(context, "❌ اسکرین شات ناموفق بود", Toast.LENGTH_SHORT).show()
        }
    }, 300)
}

/** Wrap a list row with a long-press menu: «کپی کردن» + «اسکرین شات» and
 *  any extra per-item actions. The normal tap still runs [onClick] — so the
 *  row must NOT have its own clickable; use this wrapper's onClick instead,
 *  or a tap would fire both. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LongPressMenu(
    copyText: String,
    extraItems: List<Pair<String, () -> Unit>> = emptyList(),
    onClick: () -> Unit = {},
    enabled: Boolean = true,
    showScreenshot: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.combinedClickable(
            enabled = enabled,
            onClick = onClick,
            onLongClick = { menuExpanded = true }
        )
    ) {
        content()
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("📋 کپی کردن", fontSize = 13.sp) },
                onClick = { menuExpanded = false; copyToClipboard(context, copyText) }
            )
            if (showScreenshot) {
                DropdownMenuItem(
                    text = { Text("📸 اسکرین شات", fontSize = 13.sp) },
                    onClick = { menuExpanded = false; takeScreenshot(context) }
                )
            }
            extraItems.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp) },
                    onClick = { menuExpanded = false; action() }
                )
            }
        }
    }
}
