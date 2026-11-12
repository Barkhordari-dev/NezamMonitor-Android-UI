package com.nezammonitor.app.ui.screens.reportgen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One entry (file or folder) of the public Google Drive templates folder. */
data class DriveEntry(val id: String, val name: String, val isFolder: Boolean)

/** The user's public templates folder on Google Drive. */
private const val DRIVE_ROOT_FOLDER_ID = "1jwVlDTrLMytQvKYy3pKaQ6SUeEhxzTcP"
/** The Ardakan mechanics templates subfolder (4 Word files named 1..4). */
private const val ARDAKAN_FOLDER_ID = "1-NGPFXw8nh4IzcIDXCffuqbox8GRMUMC"
private const val DRIVE_FOLDER_URL = "https://drive.google.com/drive/folders/$DRIVE_ROOT_FOLDER_ID"

/** Lists a public Drive folder via its embedded view (no sign-in, no API key).
 *  The page lists each entry as id="entry-<id>" with its title right after. */
private fun fetchDriveListing(folderId: String): List<DriveEntry> {
    val url = URL("https://drive.google.com/embeddedfolderview?id=$folderId#list")
    val conn = (url.openConnection() as HttpURLConnection).apply {
        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        connectTimeout = 15000
        readTimeout = 15000
    }
    val html = try {
        conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
    } finally {
        conn.disconnect()
    }
    val entryRe = Regex("""id="entry-([A-Za-z0-9_-]+)".{0,2000}?flip-entry-title">([^<]+)<""", RegexOption.DOT_MATCHES_ALL)
    return entryRe.findAll(html).map { m ->
        val id = m.groupValues[1]
        val name = m.groupValues[2].trim()
        // The entry's own <a href> sits between its id and its title: folders
        // link to /drive/folders/<id>, files to /file/d/<id>/view.
        val isFolder = m.value.contains("/drive/folders/$id")
        DriveEntry(id, name, isFolder)
    }.distinctBy { it.id }.toList()
}

/** Downloads a public Drive file (uc export URL) into [destDir] under [name]. */
private fun downloadDriveFile(fileId: String, name: String, destDir: File): Boolean {
    return try {
        destDir.mkdirs()
        val url = URL("https://drive.google.com/uc?export=download&id=$fileId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
            connectTimeout = 20000
            readTimeout = 60000
        }
        val bytes = try {
            conn.inputStream.readBytes()
        } finally {
            conn.disconnect()
        }
        // A real .docx is a ZIP; the Drive warning page is HTML.
        if (bytes.size < 100 || bytes[0] != 'P'.code.toByte() || bytes[1] != 'K'.code.toByte()) return false
        File(destDir, name).writeBytes(bytes)
        true
    } catch (_: Exception) {
        false
    }
}

/** The "راهنما" page: how to generate a report, where the templates live,
 *  and the Google Drive folder the user fills with templates — browsable
 *  and downloadable from inside the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templatesDir = File("/storage/emulated/0/NezamMonitor/templates")
    var currentFolderId by remember { mutableStateOf(DRIVE_ROOT_FOLDER_ID) }
    var currentFolderName by remember { mutableStateOf("پوشهٔ تمپلیت‌ها") }
    var entries by remember { mutableStateOf<List<DriveEntry>?>(null) }
    var listingError by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var downloadStatus by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    fun loadFolder(folderId: String, folderName: String) {
        currentFolderId = folderId
        currentFolderName = folderName
        entries = null
        listingError = ""
        isLoading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchDriveListing(folderId) }
            }
            isLoading = false
            result.onSuccess { entries = it }
                .onFailure { listingError = "خطا در خواندن پوشهٔ درایو — اینترنت را بررسی کن یا پوشه را در مرورگر باز کن." }
        }
    }

    fun downloadAllArdakan() {
        if (downloading) return
        downloading = true
        downloadStatus = ""
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val list = runCatching { fetchDriveListing(ARDAKAN_FOLDER_ID) }.getOrNull()
                    ?: return@withContext "خطا در خواندن پوشهٔ Ardakan-Mec"
                val files = list.filter { !it.isFolder && it.name.endsWith(".docx", ignoreCase = true) }
                if (files.isEmpty()) return@withContext "در پوشهٔ Ardakan-Mec فایلی پیدا نشد"
                var ok = 0
                files.forEach { if (downloadDriveFile(it.id, it.name, templatesDir)) ok++ }
                "✅ $ok فایل از ${files.size} تمپلیت در ${templatesDir.absolutePath} قرار گرفت"
            }
            downloadStatus = result
            downloading = false
        }
    }

    fun downloadOne(entry: DriveEntry) {
        if (downloading) return
        downloading = true
        downloadStatus = ""
        scope.launch {
            val ok = withContext(Dispatchers.IO) { downloadDriveFile(entry.id, entry.name, templatesDir) }
            downloadStatus = if (ok) "✅ «${entry.name}» در ${templatesDir.absolutePath} قرار گرفت"
            else "❌ دانلود «${entry.name}» ناموفق بود"
            downloading = false
        }
    }

    LaunchedEffect(Unit) { loadFolder(DRIVE_ROOT_FOLDER_ID, "پوشهٔ تمپلیت‌ها") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("راهنمای تولید گزارش") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("برای تولید گزارش این مراحل را انجام بده:", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            val steps = listOf(
                "۱. از منوی «بروزرسانی» وارد شو و اطلاعات پرونده‌ها را به‌روز کن.",
                "۲. در منوی «تولید گزارش»، مرحله را انتخاب کن و پرونده‌هایی را که می‌خواهی گزارششان تولید شود تیک بزن.",
                "۳. تمپلیت‌های Word را در پوشهٔ زیر قرار بده:\n${templatesDir.absolutePath}",
                "۴. دکمهٔ «📊 تولید گزارش» را بزن؛ فایل‌های Word در پوشهٔ خروجی ذخیره می‌شوند:\n/storage/emulated/0/NezamMonitor/outputs",
                "۵. از تب «سابقهٔ گزارش‌ها» می‌توانی فایل تولیدشده را باز کنی یا به اشتراک بگذاری."
            )
            steps.forEach { step ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(step, modifier = Modifier.padding(12.dp), fontSize = 13.sp)
                }
            }

            Text("نام‌گذاری تمپلیت‌ها", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "دو روش نام‌گذاری پشتیبانی می‌شود:\n• روش قدیمی: «مکانیک مرحله اول.docx» (رشته + مرحله)\n• روش جدید: «1.docx» تا «4.docx» (فقط شمارهٔ مرحله)\nاگر برای یک رشته تمپلیت مخصوص وجود نداشته باشد، تمپلیت شمارهٔ همان مرحله استفاده می‌شود.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("تمپلیت‌ها در گوگل درایو", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                "تمپلیت‌ها در این پوشهٔ گوگل درایو قرار می‌گیرند و کم‌کم اضافه می‌شوند. می‌توانی از همین‌جا مرور و دانلود کنی، یا پوشه را در مرورگر باز کنی و فایل‌ها را دستی در پوشهٔ تمپلیت‌ها قرار بدهی.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DRIVE_FOLDER_URL)))
                        } catch (_: Exception) {}
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("🌐 باز کردن در مرورگر", fontSize = 13.sp) }
                Button(
                    onClick = { downloadAllArdakan() },
                    enabled = !downloading,
                    modifier = Modifier.weight(1f)
                ) { Text("⬇️ دریافت تمپلیت‌های Ardakan-Mec", fontSize = 13.sp) }
            }
            if (downloading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("در حال دانلود...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (downloadStatus.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (downloadStatus.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
                    Text(downloadStatus, modifier = Modifier.padding(12.dp), fontSize = 12.sp)
                }
            }

            // Drive folder browser
            if (currentFolderId != DRIVE_ROOT_FOLDER_ID) {
                OutlinedButton(onClick = { loadFolder(DRIVE_ROOT_FOLDER_ID, "پوشهٔ تمپلیت‌ها") }, modifier = Modifier.fillMaxWidth()) {
                    Text("⬅️ بازگشت به پوشهٔ اصلی", fontSize = 13.sp)
                }
            }
            Text("📂 $currentFolderName", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            when {
                isLoading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("در حال خواندن پوشه...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                listingError.isNotBlank() -> {
                    Text(listingError, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
                entries.isNullOrEmpty() -> {
                    Text("در این پوشه موردی نیست.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> {
                    entries!!.forEach { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(if (entry.isFolder) "📁 ${entry.name}" else "📄 ${entry.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                if (entry.isFolder) {
                                    TextButton(onClick = { loadFolder(entry.id, entry.name) }) { Text("باز کردن", fontSize = 12.sp) }
                                } else {
                                    TextButton(onClick = { downloadOne(entry) }, enabled = !downloading) { Text("⬇️ دانلود", fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
