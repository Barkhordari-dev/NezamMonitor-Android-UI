package com.nezammonitor.app.ui.screens.update

import android.content.Context
import android.os.Environment
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalAutofill
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nezammonitor.app.api.NezamApi
import com.nezammonitor.app.ui.components.NezamCard
import com.nezammonitor.app.ui.screens.reportgen.displayShamsiDate
import com.nezammonitor.app.ui.screens.reportgen.todayJalali
import com.nezammonitor.app.ui.theme.nezamSemantic
import com.nezammonitor.app.ui.toPersianDigits
import com.nezammonitor.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.gson.JsonObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = context.getSharedPreferences("nezam_update", Context.MODE_PRIVATE)

    var username by remember { mutableStateOf(prefs.getString("username", "") ?: "") }
    var password by remember { mutableStateOf(prefs.getString("password", "") ?: "") }
    var passwordVisible by remember { mutableStateOf(false) }
    var saveCredentials by remember { mutableStateOf(prefs.getBoolean("save_creds", false)) }

    // Google Password Manager (autofill): the two fields advertise
    // themselves as username/password, so the system offers the saved login
    // when they gain focus and offers to save it after a successful login.
    // The app's own "ذخیره اطلاعات ورود" checkbox stays as well.
    val usernameAutofillNode = remember {
        AutofillNode(autofillTypes = listOf(AutofillType.Username), onFill = { username = it })
    }
    val passwordAutofillNode = remember {
        AutofillNode(autofillTypes = listOf(AutofillType.Password), onFill = { password = it })
    }
    val autofillTree = LocalAutofillTree.current
    val autofill = LocalAutofill.current
    DisposableEffect(autofillTree) {
        autofillTree += usernameAutofillNode
        autofillTree += passwordAutofillNode
        onDispose {
            // AutofillTree in this Compose version has plusAssign but no
            // minusAssign — remove the nodes from its children map directly.
            (autofillTree.children as? MutableMap<Int, AutofillNode>)?.remove(usernameAutofillNode.id)
            (autofillTree.children as? MutableMap<Int, AutofillNode>)?.remove(passwordAutofillNode.id)
        }
    }
    var isUpdating by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var progress by remember { mutableFloatStateOf(0f) }
    var lastUpdate by remember { mutableStateOf(prefs.getString("last_update", "هنوز بروزرسانی نشده") ?: "هنوز بروزرسانی نشده") }

    // Report save path
    var reportPath by remember { mutableStateOf(prefs.getString("report_path", Environment.getExternalStorageDirectory().absolutePath) ?: Environment.getExternalStorageDirectory().absolutePath) }
    var downloadReports by remember { mutableStateOf(false) }
    var updateArchived by remember { mutableStateOf(prefs.getBoolean("update_archived", false)) }
    var showPathDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("بروزرسانی اطلاعات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Last update info
            NezamCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("آخرین بروزرسانی", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    // Stored as Shamsi since 3.7.5; older Gregorian values are
                    // converted for display, so this is always Shamsi.
                    Text(if (lastUpdate == "هنوز بروزرسانی نشده") lastUpdate else displayShamsiDate(lastUpdate), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Login credentials
            Text("اطلاعات ورود", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("شماره عضویت") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { usernameAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { state ->
                        if (state.isFocused) autofill?.requestAutofillForNode(usernameAutofillNode)
                        else autofill?.cancelAutofillForNode(usernameAutofillNode)
                    },
                singleLine = true,
                shape = MaterialTheme.shapes.large
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("رمز عبور") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { passwordAutofillNode.boundingBox = it.boundsInWindow() }
                    .onFocusChanged { state ->
                        if (state.isFocused) autofill?.requestAutofillForNode(passwordAutofillNode)
                        else autofill?.cancelAutofillForNode(passwordAutofillNode)
                    },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "پنهان کردن رمز عبور" else "نمایش رمز عبور"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = saveCredentials,
                    onCheckedChange = { saveCredentials = it }
                )
                Text("ذخیره اطلاعات ورود", fontSize = 14.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = downloadReports,
                    onCheckedChange = { downloadReports = it }
                )
                Text("بروزرسانی گزارش‌ها (دانلود فایل‌ها)", fontSize = 14.sp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = updateArchived,
                    onCheckedChange = { updateArchived = it; prefs.edit().putBoolean("update_archived", it).apply() }
                )
                Text("بروزرسانی پرونده‌های بایگانی‌شده", fontSize = 14.sp)
            }

            // Report save path
            Text("مسیر ذخیره‌سازی گزارش‌ها", fontWeight = FontWeight.Bold, fontSize = 16.sp)

            NezamCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showPathDialog = true }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("مسیر فعلی:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(reportPath, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("برای تغییر کلیک کنید", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }

            // Update button
            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        statusText = "لطفاً شماره عضویت و رمز عبور را وارد کنید"
                        return@Button
                    }

                    // Save credentials if checked
                    prefs.edit().apply {
                        if (saveCredentials) {
                            putString("username", username)
                            putString("password", password)
                            putBoolean("save_creds", true)
                        } else {
                            remove("username")
                            remove("password")
                            putBoolean("save_creds", false)
                        }
                        putString("report_path", reportPath)
                        apply()
                    }

                    isUpdating = true
                    scope.launch {
                        try {
                            val api = NezamApi()
                            statusText = "در حال ورود..."
                            progress = 0.1f

                            val loginResult = api.login(username, password)
                            if (loginResult.isFailure) {
                                statusText = "خطا: ${loginResult.exceptionOrNull()?.message}"
                                isUpdating = false
                                return@launch
                            }

                            // Save the logged-in user's name from the API
                            // (/panel/api/user, name + family) so the home
                            // screen and drawer show the real user instead
                            // of a hardcoded name. MainViewModel picks this
                            // up via its SharedPreferences listener.
                            if (api.userName.isNotBlank()) {
                                context.getSharedPreferences("nezam_settings", Context.MODE_PRIVATE)
                                    .edit().putString("api_user_name", api.userName).apply()
                            }

                            statusText = "دریافت لیست پرونده‌ها..."
                            progress = 0.2f
                            val casesResult = api.getCases()
                            if (casesResult.isFailure) {
                                statusText = "خطا در دریافت پرونده‌ها"
                                isUpdating = false
                                return@launch
                            }
                            val apiCases = casesResult.getOrNull() ?: emptyList()
                            statusText = "${apiCases.size} پرونده یافت شد"

                            val db = NezamDatabase.getDatabase(context)
                            val dao = db.nezamDao()

                            // Archived cases: when the "update archived" checkbox is
                            // off, their data is left untouched (not deleted, not
                            // re-inserted); when it is on, they update like the
                            // rest but keep their archived flag.
                            val archivedNumbers = dao.getArchivedCaseNumbers()
                            val syncCases = if (updateArchived) apiCases else apiCases.filter {
                                "${safe(it, "das_year")}/${safe(it, "das_number")}" !in archivedNumbers
                            }

                            // Clear old data
                            if (updateArchived || archivedNumbers.isEmpty()) {
                                dao.deleteAllCases()
                                dao.deleteAllSpecifications()
                                dao.deleteAllEngineers()
                                dao.deleteAllFees()
                                dao.deleteAllReports()
                            } else {
                                dao.deleteNonArchivedCases()
                                dao.deleteSpecificationsExceptArchived(archivedNumbers)
                                dao.deleteEngineersExceptArchived(archivedNumbers)
                                dao.deleteFeesExceptArchived(archivedNumbers)
                                dao.deleteReportsExceptArchived(archivedNumbers)
                            }

                            // Process cases
                            val cases = mutableListOf<CaseEntity>()
                            val specs = mutableListOf<SpecificationEntity>()
                            val allEngineers = mutableListOf<EngineerEntity>()
                            val allFees = mutableListOf<FeeEntity>()
                            val allReports = mutableListOf<ReportEntity>()

                            for ((index, apiCase) in syncCases.withIndex()) {
                                progress = 0.2f + (0.5f * index / syncCases.size)
                                statusText = "پرونده ${index + 1}/${syncCases.size}: ${safe(apiCase, "own_name")} ${safe(apiCase, "own_famil")}"

                                val caseNumber = "${safe(apiCase, "das_year")}/${safe(apiCase, "das_number")}"
                                val serial = safe(apiCase, "das_serial")
                                val dbId = safe(apiCase, "db_id")

                                cases.add(CaseEntity(
                                    caseNumber = caseNumber,
                                    serial = serial,
                                    owner = "${safe(apiCase, "own_name")} ${safe(apiCase, "own_famil")}".trim(),
                                    ownerMobile = safe(apiCase, "own_mob"),
                                    responsibility = safe(apiCase, "ref_state_title"),
                                    capacityDate = safe(apiCase, "kasr_date"),
                                    office = safe(apiCase, "daf_name"),
                                    ownerFather = safe(apiCase, "own_father"),
                                    ownerNationalCode = safe(apiCase, "own_code_melli"),
                                    ownerAddress = safe(apiCase, "own_address"),
                                    ownerZip = safe(apiCase, "own_zip"),
                                    ownerTel = safe(apiCase, "own_tel"),
                                    ownerBirthLoc = safe(apiCase, "own_birth_loc"),
                                    permitNumber = safe(apiCase, "das_parvana_num"),
                                    permitDate = safe(apiCase, "das_parvana_date"),
                                    renovationCode = safe(apiCase, "das_code_nosazi"),
                                    address = safe(apiCase, "das_address"),
                                    blockNumber = safe(apiCase, "das_blocknum"),
                                    landArea = safe(apiCase, "das_masahat"),
                                    municipalArea = safe(apiCase, "db_metraj_shahrdari"),
                                    effectiveArea = safe(apiCase, "db_metraj_effective_nezarat"),
                                    floors = safe(apiCase, "db_tabagha_shahrdari"),
                                    units = safe(apiCase, "db_vahed_count"),
                                    buildingGroup = safe(apiCase, "bg_name"),
                                    structureType = safe(apiCase, "sazetyp_title"),
                                    usageType = safe(apiCase, "usetyp_title"),
                                    planZone = safe(apiCase, "mah_title"),
                                    issuer = safe(apiCase, "das_sad_title"),
                                    stateTitle = safe(apiCase, "ref_state_title"),
                                    workType = safe(apiCase, "dastyp_title"),
                                    discipline = safe(apiCase, "mas_title"),
                                    unitTitle = safe(apiCase, "db_title"),
                                    executionDate = safe(apiCase, "das_date_tarkhis"),
                                    dbId = dbId,
                                    archived = caseNumber in archivedNumbers
                                ))

                                // Specification
                                specs.add(SpecificationEntity(
                                    caseNumber = caseNumber,
                                    address = safe(apiCase, "das_address"),
                                    buildingGroup = safe(apiCase, "bg_name"),
                                    renovationCode = safe(apiCase, "das_code_nosazi"),
                                    permitNumber = safe(apiCase, "das_parvana_num"),
                                    permitDate = safe(apiCase, "das_parvana_date"),
                                    releaseDate = safe(apiCase, "das_date_tarkhis"),
                                    landArea = safe(apiCase, "das_masahat"),
                                    capacityArea = safe(apiCase, "db_metraj_effective_nezarat"),
                                    structureType = safe(apiCase, "sazetyp_title"),
                                    blockTitle = safe(apiCase, "db_title"),
                                    blockCount = safe(apiCase, "das_blocknum"),
                                    floors = safe(apiCase, "db_tabagha_shahrdari"),
                                    units = safe(apiCase, "db_vahed_count"),
                                    issuer = safe(apiCase, "das_sad_title"),
                                    planZone = safe(apiCase, "mah_title"),
                                    usageType = safe(apiCase, "usetyp_title")
                                ))

                                // Engineers
                                if (dbId.toIntOrNull() != null) {
                                    val engResult = api.getEngineers(dbId.toInt())
                                    engResult.getOrNull()?.forEach { eng ->
                                        allEngineers.add(EngineerEntity(
                                            caseNumber = caseNumber,
                                            discipline = safe(eng, "mas_title"),
                                            name = "${safe(eng, "ozh_name")} ${safe(eng, "ozh_famil")}".trim(),
                                            role = ""
                                        ))
                                    }
                                }

                                // Fees
                                if (dbId.toIntOrNull() != null) {
                                    val feeResult = api.getFees(dbId.toInt())
                                    feeResult.getOrNull()?.forEach { fee ->
                                        val dbmPType = safe(fee, "dbm_p_type")
                                        val amountType = when(dbmPType) {
                                            "n" -> "نظارت"
                                            "b" -> "بیمه"
                                            "ho" -> "حسن انجام کار"
                                            "t" -> "مالیات نظارت سهم سازمان"
                                            "pn" -> "نظارت سهم سازمان"
                                            else -> dbmPType
                                        }
                                        val selected = safe(fee, "selected")
                                        val confirmStatus = if (selected == "1") "تایید شده" else ""
                                        val amount = safe(fee, "dbm_mablagh")
                                        val formattedAmount = try {
                                            val num = amount.toLong()
                                            "%,d".format(num) + " ریال"
                                        } catch (e: Exception) { amount }

                                        allFees.add(FeeEntity(
                                            caseNumber = caseNumber,
                                            discipline = safe(fee, "mas_title"),
                                            serviceType = when(safe(fee, "tnh_type")) { "n" -> "نظارت"; else -> safe(fee, "tnh_type") },
                                            stage = safe(fee, "dbm_marhala_id"),
                                            startDate = safe(fee, "sanad_date"),
                                            endDate = safe(fee, "pardakht_date"),
                                            amount = formattedAmount,
                                            payStatus = when(safe(fee, "payed")) { "yes" -> "پرداخت شده"; "ok" -> "تایید شده"; else -> "پرداخت نشده" },
                                            confirmStatus = confirmStatus,
                                            amountType = amountType,
                                            description = ""
                                        ))
                                    }
                                }

                                // Reports
                                if (dbId.toIntOrNull() != null) {
                                    val rptResult = api.getReports(dbId.toInt())
                                    rptResult.getOrNull()?.forEach { rpt ->
                                        allReports.add(ReportEntity(
                                            caseNumber = caseNumber,
                                            reportType = safe(rpt, "brt_report_title"),
                                            stage = safe(rpt, "brt_marhale_title"),
                                            engineer = "${safe(rpt, "ozh_name")} ${safe(rpt, "ozh_famil")}".trim(),
                                            discipline = safe(rpt, "mas_title"),
                                            visitDate = safe(rpt, "br_bazdid_date"),
                                            ceilingCount = safe(rpt, "br_exec_ceil"),
                                            indicator = safe(rpt, "br_andicator"),
                                            hasFile = safe(rpt, "image_name").isNotEmpty(),
                                            imageName = safe(rpt, "image_name")
                                        ))
                                    }
                                }
                            }

                            // Save to database
                            statusText = "ذخیره‌سازی در پایگاه داده..."
                            progress = 0.8f
                            dao.insertCases(cases)
                            dao.insertSpecifications(specs)
                            dao.insertEngineers(allEngineers)
                            dao.insertFees(allFees)
                            dao.insertReports(allReports)

                            // Download report files (only if checkbox checked)
                            var downloadedFiles = 0
                            if (downloadReports) {
                            statusText = "دانلود فایل‌های گزارش‌ها..."
                            progress = 0.85f
                            var caseNum = 0
                            for ((index, apiCase) in syncCases.withIndex()) {
                                caseNum++
                                val dbId = safe(apiCase, "db_id")
                                if (dbId.toIntOrNull() == null) continue

                                val rptResult = api.getReports(dbId.toInt())
                                val rptList = rptResult.getOrNull() ?: continue
                                if (rptList.isEmpty()) continue

                                val owner = "${safe(apiCase, "own_name")} ${safe(apiCase, "own_famil")}".trim()
                                val caseNum2 = "${safe(apiCase, "das_year")}/${safe(apiCase, "das_number")}"
                                val safeOwner = owner.replace("/", "_").replace("\\", "_").replace(":", "_")
                                val safeCase = caseNum2.replace("/", "_")
                                val caseFolder = java.io.File(reportPath, "NezamMonitor/$caseNum" + "_${safeOwner}_$safeCase")
                                caseFolder.mkdirs()

                                for ((rptIdx, rpt) in rptList.withIndex()) {
                                    val imageName = safe(rpt, "image_name")
                                    if (imageName.isEmpty()) continue
                                    val reportType = safe(rpt, "brt_report_title")
                                    val safeType = reportType.replace("/", "_").replace("\\", "_").replace(":", "_")
                                    val ext = if (imageName.contains(".")) ".${imageName.split(".").last()}" else ".jpg"
                                    val fileName = "${rptIdx + 1}_${safeType}$ext"

                                    try {
                                        val fileResult = api.downloadFile(imageName)
                                        if (fileResult.isSuccess) {
                                            val file = java.io.File(caseFolder, fileName)
                                            file.writeBytes(fileResult.getOrNull() ?: byteArrayOf())
                                            downloadedFiles++
                                        }
                                    } catch (_: Exception) {}
                                }

                                progress = 0.85f + (0.15f * (index + 1) / syncCases.size)
                                if ((index + 1) % 10 == 0 || index == syncCases.size - 1) {
                                    statusText = "دانلود فایل‌ها: ${index + 1}/${syncCases.size} ($downloadedFiles فایل)"
                                }
                            }
                            } // end downloadReports

                            // Done — store the date in Shamsi (Jalali), not Gregorian.
                            val timePart = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                            val now = "${todayJalali().format()} $timePart"
                            prefs.edit().putString("last_update", now).apply()
                            lastUpdate = now
                            // Snapshot the synced dataset for the Changes menu.
                            com.nezammonitor.app.data.SnapshotStore.saveSnapshot(dao, "بروزرسانی از سامانه", now)
                            // Permanent hidden-menu overrides: the sync just
                            // rewrote every row, so re-apply them now. The
                            // snapshot above keeps the pure server data.
                            try { applyHiddenOverrides(dao) } catch (_: Exception) {}
                            statusText = "✅ بروزرسانی کامل شد!\n${cases.size} پرونده | ${allEngineers.size} مهندس | ${allFees.size} حق‌الزحمه | ${allReports.size} گزارش | $downloadedFiles فایل"
                            progress = 1f
                        } catch (e: Exception) {
                            statusText = "خطا: ${e.message}"
                        } finally {
                            isUpdating = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !isUpdating && username.isNotBlank() && password.isNotBlank()
            ) {
                if (isUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("در حال بروزرسانی...")
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بروزرسانی اطلاعات")
                }
            }

            // Building under construction — it rises with the update
            // progress; when the update finishes, the completed building
            // shows a green check. It stays visible after completion
            // (progress == 1f) and hides on error.
            if (isUpdating || progress >= 1f) {
                UpdateBuildingProgress(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Status text
            if (statusText.isNotEmpty()) {
                NezamCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = if (statusText.startsWith("✅")) MaterialTheme.nezamSemantic.successContainer
                    else if (statusText.startsWith("خطا")) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = statusText,
                        modifier = Modifier.padding(16.dp),
                        fontSize = 13.sp
                    )
                }
            }
        }
    }

    // Path selection dialog
    if (showPathDialog) {
        AlertDialog(
            onDismissRequest = { showPathDialog = false },
            title = { Text("انتخاب مسیر ذخیره‌سازی") },
            text = {
                Column {
                    Text("مسیر فعلی:", fontSize = 12.sp)
                    Text(reportPath, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("مسیرهای پیشنهادی:", fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    val suggestions = listOf(
                        Environment.getExternalStorageDirectory().absolutePath to "حافظه اصلی",
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath to "Documents",
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath to "Downloads",
                        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).absolutePath to "DCIM"
                    )

                    suggestions.forEach { (path, label) ->
                        TextButton(onClick = {
                            reportPath = path
                            prefs.edit().putString("report_path", path).apply()
                            showPathDialog = false
                        }) {
                            Text("$label\n$path", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPathDialog = false }) {
                    Text("بستن")
                }
            }
        )
    }
}

private fun safe(obj: JsonObject, key: String): String {
    return try {
        if (obj.has(key) && !obj.get(key).isJsonNull) {
            obj.get(key).asString
        } else ""
    } catch (_: Exception) { "" }
}

/**
 * A building under construction that rises with [progress] (0f..1f):
 * the fill climbs from the ground up, each floor's windows light up as
 * the fill passes them, and a tower crane works beside it. When the
 * update finishes ([progress] == 1f) the crane is gone and a green check
 * badge marks the completed building.
 */
@Composable
private fun UpdateBuildingProgress(progress: Float, modifier: Modifier = Modifier) {
    val isDone = progress >= 1f
    // Smooth the fill between the discrete progress jumps of the sync.
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300)
    )
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.onSurfaceVariant
    val success = MaterialTheme.nezamSemantic.success
    val litWindow = Color(0xFFFFB44A)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(modifier = Modifier.size(220.dp, 180.dp)) {
            val w = size.width
            val h = size.height
            val groundY = h - 12.dp.toPx()
            val bLeft = w * 0.14f
            val bRight = w * 0.58f
            val bTop = h * 0.08f
            val bBottom = groundY
            val bWidth = bRight - bLeft
            val bHeight = bBottom - bTop
            val floors = 5
            val floorH = bHeight / floors
            val stroke = Stroke(width = 2.dp.toPx())

            // Ground line
            drawLine(
                color = outline,
                start = Offset(w * 0.04f, groundY),
                end = Offset(w * 0.96f, groundY),
                strokeWidth = 2.dp.toPx()
            )

            // Tower crane beside the building (only while building)
            if (!isDone) {
                val mastX = w * 0.74f
                val jibY = h * 0.05f
                // Mast
                drawLine(outline, Offset(mastX, groundY), Offset(mastX, jibY), 2.dp.toPx())
                // Jib over the building + counter-jib
                drawLine(outline, Offset(w * 0.30f, jibY), Offset(w * 0.86f, jibY), 2.dp.toPx())
                // Hook cable hanging down to the current construction level
                val hookX = w * 0.48f
                val hookY = bTop + (1f - animatedProgress) * bHeight
                drawLine(outline, Offset(hookX, jibY), Offset(hookX, hookY), 1.dp.toPx())
                drawRect(
                    color = outline,
                    topLeft = Offset(hookX - 3.dp.toPx(), hookY),
                    size = Size(6.dp.toPx(), 6.dp.toPx())
                )
            }

            // Building fill, rising from the ground (clipped to the body)
            val fillH = animatedProgress * bHeight
            if (fillH > 0f) {
                clipRect(left = bLeft, top = bTop, right = bRight, bottom = bBottom) {
                    drawRect(
                        color = primary.copy(alpha = 0.85f),
                        topLeft = Offset(bLeft, bBottom - fillH),
                        size = Size(bWidth, fillH)
                    )
                }
            }

            // Building outline
            drawRect(color = outline, topLeft = Offset(bLeft, bTop), size = Size(bWidth, bHeight), style = stroke)

            // Floor separators
            for (i in 1 until floors) {
                val y = bTop + i * floorH
                drawLine(
                    color = outline.copy(alpha = 0.45f),
                    start = Offset(bLeft, y),
                    end = Offset(bRight, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Windows: 3 per floor, lit (amber) once the fill passed the floor.
            // The ground floor's middle window is the entrance door instead.
            val winW = bWidth * 0.16f
            val winH = floorH * 0.42f
            for (f in 0 until floors) {
                val lit = animatedProgress >= (f + 1) / floors.toFloat()
                val cy = bBottom - (f + 0.5f) * floorH
                for (j in 0 until 3) {
                    if (f == 0 && j == 1) continue
                    val cx = bLeft + bWidth * ((j + 0.5f) / 3f)
                    val topLeft = Offset(cx - winW / 2, cy - winH / 2)
                    if (lit) {
                        drawRect(color = litWindow, topLeft = topLeft, size = Size(winW, winH))
                    } else {
                        drawRect(
                            color = outline.copy(alpha = 0.6f),
                            topLeft = topLeft,
                            size = Size(winW, winH),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }

            // Entrance door
            val doorW = bWidth * 0.22f
            val doorH = floorH * 0.62f
            drawRect(
                color = outline,
                topLeft = Offset(bLeft + (bWidth - doorW) / 2, bBottom - doorH),
                size = Size(doorW, doorH)
            )

            // Green check badge on the completed building
            if (isDone) {
                val badgeR = 15.dp.toPx()
                val badgeC = Offset(bRight, bTop)
                drawCircle(color = success, radius = badgeR, center = badgeC)
                val check = Path().apply {
                    moveTo(badgeC.x - 6.dp.toPx(), badgeC.y)
                    lineTo(badgeC.x - 1.5f.dp.toPx(), badgeC.y + 5.dp.toPx())
                    lineTo(badgeC.x + 7.dp.toPx(), badgeC.y - 5.dp.toPx())
                }
                drawPath(
                    path = check,
                    color = Color.White,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isDone) "تکمیل شد" else "${toPersianDigits(((progress * 100).toInt()).toString())}٪",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDone) success else primary
        )
    }
}
