package com.nezammonitor.app.ui.components

import android.os.Environment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File

/** An in-app folder browser (full-screen dialog): navigate like a file
 *  manager — tap a subfolder to enter it, go up with "پوشهٔ بالاتر",
 *  create a new folder, and confirm with "انتخاب این پوشه". No typing
 *  the path. Starts at [initialPath] (or its nearest existing ancestor
 *  when it does not exist yet); going up stops at the external-storage
 *  root. Only subfolders are listed.
 *
 *  Shared by the report paths page, the Update screen (download folder)
 *  and the Export screen (exports folder) — same picker everywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowserDialog(
    title: String,
    initialPath: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val externalRoot = Environment.getExternalStorageDirectory()
    var current by remember { mutableStateOf(nearestExistingDir(initialPath, externalRoot)) }
    var refreshTick by remember { mutableStateOf(0) }
    var showNewFolder by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    val subfolders = remember(current, refreshTick) {
        current.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() } ?: emptyList()
    }
    val atRoot = current.absolutePath == externalRoot.absolutePath
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize().padding(8.dp), shape = MaterialTheme.shapes.large) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(current.absolutePath, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable(enabled = !atRoot) { current.parentFile?.let { current = it } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⬆️", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "پوشهٔ بالاتر",
                        fontSize = 14.sp,
                        color = if (atRoot) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                }
                Divider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    if (subfolders.isEmpty()) {
                        item {
                            Text(
                                "پوشه‌ای نیست",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                    items(subfolders) { dir ->
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { current = dir }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📁", fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(dir.name, fontSize = 14.sp)
                        }
                    }
                }
                Divider()
                if (showNewFolder) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = newFolderName,
                            onValueChange = { newFolderName = it },
                            label = { Text("نام پوشهٔ جدید", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            val name = newFolderName.trim()
                            if (name.isNotBlank()) {
                                val created = File(current, name)
                                if (created.mkdir()) {
                                    newFolderName = ""
                                    showNewFolder = false
                                    current = created
                                    refreshTick++
                                }
                            }
                        }) { Text("ایجاد", fontSize = 12.sp) }
                    }
                } else {
                    TextButton(onClick = { showNewFolder = true }) { Text("+ پوشهٔ جدید", fontSize = 12.sp) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { onPick(current.absolutePath) }, modifier = Modifier.weight(1f)) {
                        Text("✓ انتخاب این پوشه", fontSize = 13.sp)
                    }
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("انصراف", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** The folder itself when it exists, else the nearest ancestor that
 *  does (a saved path may point at a folder the user deleted). */
private fun nearestExistingDir(path: String, fallback: File): File {
    var f = File(path)
    while (!f.exists() || !f.isDirectory) {
        f = f.parentFile ?: return fallback
    }
    return f
}
