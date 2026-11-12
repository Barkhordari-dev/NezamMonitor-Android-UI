package com.nezammonitor.app.ui.screens.reportgen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.ui.components.FolderBrowserDialog

/** The report paths page (opened from the generator's small "📂 مسیرها"
 *  button): the 3 paths with their defaults, each changeable through an
 *  in-app folder browser (no typing — navigate like a file manager) or
 *  reset to its default. Paths are stored in nezam_settings, the same
 *  keys the generator reads (templates_path / signed_templates_path /
 *  outputs_path). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportPathsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var paths by remember { mutableStateOf(loadReportPaths(context)) }
    // Which path key the folder browser is open for (null = closed).
    var browseKey by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("مسیرها") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } })
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "مسیر تمپلیت‌ها، تمپلیت‌های امضاشده و خروجی گزارش‌ها را اینجا ببینید و عوض کنید. برای تغییر مسیر، پوشه را مثل فایل‌منیجر انتخاب کنید؛ تایپ کردن لازم نیست.",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                ReportPathsRow(
                    label = "📂 تمپلیت‌ها",
                    path = paths.templatesPath,
                    defaultPath = DEFAULT_TEMPLATES_PATH,
                    onChange = { browseKey = "templates_path" },
                    onReset = {
                        saveReportPath(context, "templates_path", DEFAULT_TEMPLATES_PATH)
                        paths = paths.copy(templatesPath = DEFAULT_TEMPLATES_PATH)
                    }
                )
            }
            item {
                ReportPathsRow(
                    label = "📂 تمپلیت‌های امضاشده",
                    path = paths.signedTemplatesPath,
                    defaultPath = DEFAULT_SIGNED_TEMPLATES_PATH,
                    onChange = { browseKey = "signed_templates_path" },
                    onReset = {
                        saveReportPath(context, "signed_templates_path", DEFAULT_SIGNED_TEMPLATES_PATH)
                        paths = paths.copy(signedTemplatesPath = DEFAULT_SIGNED_TEMPLATES_PATH)
                    }
                )
            }
            item {
                ReportPathsRow(
                    label = "📂 خروجی",
                    path = paths.outputsPath,
                    defaultPath = DEFAULT_OUTPUTS_PATH,
                    onChange = { browseKey = "outputs_path" },
                    onReset = {
                        saveReportPath(context, "outputs_path", DEFAULT_OUTPUTS_PATH)
                        paths = paths.copy(outputsPath = DEFAULT_OUTPUTS_PATH)
                    }
                )
            }
        }
    }

    // Folder browser for the path being changed.
    if (browseKey != null) {
        val key = browseKey!!
        val title = when (key) {
            "signed_templates_path" -> "مسیر تمپلیت‌های امضاشده"
            "outputs_path" -> "مسیر خروجی"
            else -> "مسیر تمپلیت‌ها"
        }
        val initial = when (key) {
            "signed_templates_path" -> paths.signedTemplatesPath
            "outputs_path" -> paths.outputsPath
            else -> paths.templatesPath
        }
        FolderBrowserDialog(
            title = title,
            initialPath = initial,
            onPick = { picked ->
                saveReportPath(context, key, picked)
                paths = when (key) {
                    "signed_templates_path" -> paths.copy(signedTemplatesPath = picked)
                    "outputs_path" -> paths.copy(outputsPath = picked)
                    else -> paths.copy(templatesPath = picked)
                }
                browseKey = null
            },
            onDismiss = { browseKey = null }
        )
    }
}

@Composable
private fun ReportPathsRow(
    label: String,
    path: String,
    defaultPath: String,
    onChange: () -> Unit,
    onReset: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(path, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("پیش‌فرض: $defaultPath", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onChange) { Text("تغییر", fontSize = 12.sp) }
                if (path != defaultPath) {
                    TextButton(onClick = onReset) { Text("بازگردانی به پیش‌فرض", fontSize = 12.sp) }
                }
            }
        }
    }
}
