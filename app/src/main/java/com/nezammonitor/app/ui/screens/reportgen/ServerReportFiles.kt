package com.nezammonitor.app.ui.screens.reportgen

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nezammonitor.app.api.NezamApi
import com.nezammonitor.app.data.ReportEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ==================== SERVER REPORT FILES (v3.11.0, feature 1) ====================
// Downloaded server reports live in ONE visible home (v3.11.3):
//   <reportPath>/nezam_output/<n> - <owner> - <reversedCase>/<id>_<safeType><ext>
// — the same per-case folder shape as the generated reports (only the
// root differs: nezam_output instead of outputs). The Update screen
// downloads there, the on-demand download writes there, and the history
// tab reuses the same helper — one name scheme everywhere, and a file
// that is already there is reused instead of downloaded again.
//
// The OLD homes are still READ as legacy fallbacks, so files already on
// the phone keep opening: <reportPath>/downloads/<n>_<owner>_<case>/…
// (v3.8.8–v3.11.2 Update downloads) and <cacheDir>/server_reports/
// (on-demand downloads before v3.11.3, under two names). Nothing is
// moved — the old folders are only read, never written. The history
// tab itself is otherwise untouched.

/** The base folder the Update screen downloads into: the "report_path"
 *  pref in nezam_update (default /storage/emulated/0/NezamMonitor). A saved
 *  storage-root value is migrated in memory, exactly like UpdateScreen. */
internal fun loadDownloadsBasePath(context: Context): String {
    val saved = context.getSharedPreferences("nezam_update", Context.MODE_PRIVATE)
        .getString("report_path", DEFAULT_NEZAM_ROOT) ?: DEFAULT_NEZAM_ROOT
    return if (saved == Environment.getExternalStorageDirectory().absolutePath) DEFAULT_NEZAM_ROOT else saved
}

/** The new home for downloaded server reports (v3.11.3):
 *  <reportPath>/nezam_output. */
internal fun loadNezamOutputDir(context: Context): File =
    File(loadDownloadsBasePath(context), "nezam_output")

/** Find the file downloaded for this server report, or null when it was
 *  not downloaded (or was deleted). Tries, in order:
 *  (1) the nezam_output folder of the case (same shape as the generated
 *      reports' folders), exact file name first, then any file in that
 *      folder — preferring the one whose name carries the report type;
 *  (2) the legacy downloads/ counter folders, exact name first;
 *  (3) the cache copy (<id>_<safeType><ext>, then the old history-tab
 *      name — the sanitized imageName);
 *  (4) any file in the legacy case folder. rowNo is NOT used — it is
 *  blank for synced reports (only .nzmdata imports set it).
 *
 *  v3.11.1: the lookup is broader, because on the phone the exact match
 *  failed even though a copy of the file was on the system. */
internal fun findDownloadedServerReportFile(context: Context, report: ReportEntity): File? {
    if (!report.hasFile || report.imageName.isBlank()) return null
    val basePath = loadDownloadsBasePath(context)
    return findLocalServerReportFile(
        File(basePath, "nezam_output"),
        File(basePath, "downloads"),
        File(context.cacheDir, "server_reports"),
        report
    )
}

/** The testable core of [findDownloadedServerReportFile]: the same
 *  steps, but over plain folders (no Context, no SharedPreferences). */
internal fun findLocalServerReportFile(
    nezamOutputDir: File,
    downloadsDir: File,
    cacheReportsDir: File?,
    report: ReportEntity
): File? {
    if (!report.hasFile || report.imageName.isBlank()) return null
    val safeCase = report.caseNumber.replace("/", "_")
    val ext = if (report.imageName.contains(".")) ".${report.imageName.substringAfterLast(".")}" else ".jpg"
    val safeType = report.reportType.replace("/", "_").replace("\\", "_").replace(":", "_")
    // (1) nezam_output — the current home.
    val namePattern = Regex("^\\d+_" + Regex.escape(safeType) + Regex.escape(ext) + "$")
    val nezamFolder = findReportCaseFolder(nezamOutputDir, report.caseNumber)
    if (nezamFolder != null) {
        nezamFolder.listFiles()?.firstOrNull { it.isFile && namePattern.matches(it.name) }?.let { return it }
        val files = nezamFolder.listFiles()?.filter { it.isFile } ?: emptyList()
        files.firstOrNull { it.name.contains(safeType) }?.let { return it }
        files.sortedBy { it.name }.firstOrNull()?.let { return it }
    }
    // (2) Legacy downloads/ counter folders, exact name first.
    val caseFolders = if (downloadsDir.isDirectory) {
        downloadsDir.listFiles()?.filter { it.isDirectory && it.name.endsWith("_$safeCase") } ?: emptyList()
    } else emptyList()
    for (folder in caseFolders) {
        val match = folder.listFiles()?.firstOrNull { it.isFile && namePattern.matches(it.name) }
        if (match != null) return match
    }
    // (3) The cache copy (same name the on-demand download writes),
    // then the old history-tab name (the sanitized imageName).
    if (cacheReportsDir != null) {
        val cached = File(cacheReportsDir, "${report.id}_$safeType$ext")
        if (cached.isFile && cached.length() > 0) return cached
        val legacyName = report.imageName.replace(Regex("[/\\\\:*?\"<>|]"), "_")
        val legacy = File(cacheReportsDir, legacyName)
        if (legacy.isFile && legacy.length() > 0) return legacy
    }
    // (4) Any file in the legacy case folder — prefer the one whose
    // name carries the report type, otherwise the first file.
    for (folder in caseFolders) {
        val files = folder.listFiles()?.filter { it.isFile } ?: continue
        if (files.isEmpty()) continue
        val byType = files.firstOrNull { it.name.contains(safeType) }
        if (byType != null) return byType
        return files.sortedBy { it.name }.first()
    }
    return null
}

/** What an on-demand report download returned: the file, or why it
 *  failed (v3.11.1 — the old code swallowed the reason and the button
 *  could only say «دانلود ناموفق بود»). */
internal sealed class ServerReportDownload {
    data class Ok(val file: File) : ServerReportDownload()
    data class Failed(val reason: String) : ServerReportDownload()
}

/** Download a server report into nezam_output (v3.11.3 — the cache is
 *  no longer written; the file lives in the visible per-case folder,
 *  one name scheme everywhere: <id>_<safeType><ext>), reusing an
 *  already-downloaded copy. [owner] is the case owner's name for the
 *  folder: the case's existing nezam_output folder is reused when there
 *  is one, otherwise a new "۰۱ - …" folder is created. Returns the
 *  file, or why it failed (v3.11.1 — the old code swallowed the reason
 *  and the button could only say «دانلود ناموفق بود»). */
internal suspend fun downloadServerReportFile(context: Context, report: ReportEntity, owner: String = ""): ServerReportDownload {
    if (report.imageName.isBlank()) return ServerReportDownload.Failed("نام فایل خالی است")
    return withContext(Dispatchers.IO) {
        try {
            val baseDir = loadNezamOutputDir(context).apply { mkdirs() }
            val folder = findReportCaseFolder(baseDir, report.caseNumber)
                ?: File(baseDir, reportCaseFolderName(1, owner, report.caseNumber)).apply { mkdirs() }
            val ext = if (report.imageName.contains(".")) ".${report.imageName.substringAfterLast(".")}" else ".jpg"
            val safeType = report.reportType.replace("/", "_").replace("\\", "_").replace(":", "_")
            val file = File(folder, "${report.id}_$safeType$ext")
            if (file.exists() && file.length() > 0) return@withContext ServerReportDownload.Ok(file)
            val result = NezamApi().downloadFile(report.imageName)
            val bytes = result.getOrNull()
            if (bytes == null) {
                val reason = result.exceptionOrNull()?.message?.takeIf { it.isNotBlank() }
                    ?: result.exceptionOrNull()?.javaClass?.simpleName
                    ?: "خطای نامشخص"
                return@withContext ServerReportDownload.Failed(reason)
            }
            file.writeBytes(bytes)
            ServerReportDownload.Ok(file)
        } catch (e: Exception) {
            ServerReportDownload.Failed(e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
        }
    }
}

/** Open a file with the system viewer (FileProvider + ACTION_VIEW), exactly
 *  like the history tab's «باز کردن». */
internal fun openFileWithViewer(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeForFile(file.name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

/** Share a file (FileProvider + ACTION_SEND), exactly like the history
 *  tab's «اشتراک». */
internal fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeForFile(file.name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "اشتراک‌گذاری"))
    } catch (_: Exception) {
    }
}

/** MIME type by extension (same mapping as the history tab). */
internal fun mimeForFile(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "pdf" -> "application/pdf"
    "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    "doc" -> "application/msword"
    else -> "*/*"
}

/** The small text buttons for one server report, in the history tab's
 *  style (12.sp): «📁 باز کردن» when the file was already downloaded
 *  during update, «⬇️ دانلود و باز کردن» otherwise. Nothing is shown when
 *  the server registered no file for the report. */
@Composable
internal fun ServerReportFileButtons(report: ReportEntity, owner: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloadedFile by remember(report.id) { mutableStateOf<File?>(null) }
    var checked by remember(report.id) { mutableStateOf(false) }
    var isDownloading by remember(report.id) { mutableStateOf(false) }
    var errorText by remember(report.id) { mutableStateOf("") }

    // One IO check per report: was it downloaded during update?
    LaunchedEffect(report.id) {
        downloadedFile = withContext(Dispatchers.IO) { findDownloadedServerReportFile(context, report) }
        checked = true
    }

    if (!checked) return
    val localFile = downloadedFile
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (localFile != null) {
            TextButton(onClick = { openFileWithViewer(context, localFile) }) {
                Text("📁 باز کردن", fontSize = 12.sp)
            }
        } else if (report.hasFile && report.imageName.isNotBlank()) {
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text("در حال دانلود...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                TextButton(onClick = {
                    scope.launch {
                        isDownloading = true
                        errorText = ""
                        when (val downloaded = downloadServerReportFile(context, report, owner)) {
                            is ServerReportDownload.Ok -> {
                                downloadedFile = downloaded.file
                                openFileWithViewer(context, downloaded.file)
                            }
                            is ServerReportDownload.Failed -> {
                                // v3.11.1: the real reason (HTTP code or
                                // error type) instead of a bare «ناموفق بود».
                                errorText = "دانلود ناموفق بود: ${downloaded.reason}"
                            }
                        }
                        isDownloading = false
                    }
                }) {
                    Text("⬇️ دانلود و باز کردن", fontSize = 12.sp)
                }
            }
        }
        if (errorText.isNotBlank()) {
            Text(errorText, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
    }
}
