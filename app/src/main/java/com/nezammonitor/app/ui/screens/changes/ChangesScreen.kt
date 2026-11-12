package com.nezammonitor.app.ui.screens.changes

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.nezammonitor.app.api.toPersianChars
import com.nezammonitor.app.data.CaseEntity
import com.nezammonitor.app.data.EngineerEntity
import com.nezammonitor.app.data.FeeEntity
import com.nezammonitor.app.data.ReportEntity
import com.nezammonitor.app.data.SnapshotData
import com.nezammonitor.app.data.SnapshotEntity
import com.nezammonitor.app.data.SnapshotStore
import com.nezammonitor.app.data.SpecificationEntity
import com.nezammonitor.app.ui.components.NezamEmptyState
import com.nezammonitor.app.ui.components.NezamSearchField
import com.nezammonitor.app.ui.components.feeTitle
import com.nezammonitor.app.ui.components.formatFeeAmount
import com.nezammonitor.app.ui.components.payStatusDisplay
import com.nezammonitor.app.ui.components.reportStageLabel
import com.nezammonitor.app.ui.screens.reportgen.buildingGroupLetter
import com.nezammonitor.app.ui.screens.reportgen.displayShamsiDate
import com.nezammonitor.app.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What changed in one snapshot versus the previous one. */
data class ChangedCase(val caseNumber: String, val owner: String, val changes: List<String>)

data class SnapshotDiff(
    val addedCases: List<CaseEntity>,
    val removedCases: List<CaseEntity>,
    val changedCases: List<ChangedCase>,
    val feeDelta: Int,
    val reportDelta: Int
)

/** Diff two snapshots (current vs previous; previous may be empty for the
 *  first snapshot). A case counts as changed when its key fields or its
 *  engineer/fee/report counts differ. */
fun diffSnapshots(current: SnapshotData, previous: SnapshotData): SnapshotDiff {
    val prevCases = previous.cases.associateBy { it.caseNumber }
    val curCases = current.cases.associateBy { it.caseNumber }
    val added = current.cases.filter { it.caseNumber !in prevCases }
    val removed = previous.cases.filter { it.caseNumber !in curCases }
    val changed = mutableListOf<ChangedCase>()
    for ((number, cur) in curCases) {
        val prev = prevCases[number] ?: continue
        val changes = mutableListOf<String>()
        fun field(label: String, a: String, b: String) {
            if (a != b) changes.add("$label: ${b.ifBlank { "—" }} ← ${a.ifBlank { "—" }}")
        }
        field("مالک", cur.owner, prev.owner)
        field("دفتر", cur.office, prev.office)
        field("شماره پروانه", cur.permitNumber, prev.permitNumber)
        field("تاریخ صدور", cur.permitDate, prev.permitDate)
        field("نشانی", cur.address, prev.address)
        val curFees = current.fees.count { it.caseNumber == number }
        val prevFees = previous.fees.count { it.caseNumber == number }
        if (curFees != prevFees) changes.add("حق‌الزحمه: $prevFees ← $curFees")
        val curReports = current.reports.count { it.caseNumber == number }
        val prevReports = previous.reports.count { it.caseNumber == number }
        if (curReports != prevReports) changes.add("گزارش: $prevReports ← $curReports")
        val curEng = current.engineers.count { it.caseNumber == number }
        val prevEng = previous.engineers.count { it.caseNumber == number }
        if (curEng != prevEng) changes.add("مهندس: $prevEng ← $curEng")
        if (changes.isNotEmpty()) changed.add(ChangedCase(number, cur.owner, changes))
    }
    return SnapshotDiff(
        addedCases = added,
        removedCases = removed,
        changedCases = changed,
        feeDelta = current.fees.size - previous.fees.size,
        reportDelta = current.reports.size - previous.reports.size
    )
}

/** Where the active (viewed) snapshot id is kept: -1 means "none active".
 *  Activation is read-only viewing — it never touches live data. */
private const val CHANGES_PREFS = "nezam_changes"
private const val KEY_ACTIVE_SNAPSHOT_ID = "active_snapshot_id"
private const val NO_ACTIVE_SNAPSHOT = -1L

/** Resolved (building group, discipline) for a snapshotted case: the case
 *  row itself, then its specification (group) and its engineers/fees/
 *  reports (discipline) — same sources as the Cases filter. */
private fun resolveSnapshotGroupDiscipline(
    case: CaseEntity,
    spec: SpecificationEntity?,
    engineers: List<EngineerEntity>,
    fees: List<FeeEntity>,
    reports: List<ReportEntity>
): Pair<String, String> {
    val group = case.buildingGroup.ifBlank { spec?.buildingGroup ?: "" }
    val disc = case.discipline.ifBlank {
        engineers.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: fees.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: reports.firstOrNull { it.caseNumber == case.caseNumber }?.discipline
            ?: ""
    }
    return group to disc
}

/** The Changes (Snapshots) menu: every sync/import saves a snapshot of the
 *  dataset. Pick one to see what changed versus the previous one, pick two
 *  and compare them, select several and merge them into the live data (new
 *  info wins, empty fields never overwrite), or select and delete old ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangesScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val snapshots by viewModel.snapshots.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var expandedId by remember { mutableStateOf<Long?>(null) }
    // Snapshot activation (read-only viewing): only one snapshot can be
    // active at a time; the id survives restarts via SharedPreferences.
    val context = LocalContext.current
    val changesPrefs = remember(context) { context.getSharedPreferences(CHANGES_PREFS, Context.MODE_PRIVATE) }
    var activeSnapshotId by remember { mutableStateOf(changesPrefs.getLong(KEY_ACTIVE_SNAPSHOT_ID, NO_ACTIVE_SNAPSHOT)) }
    var viewerQuery by remember { mutableStateOf("") }
    var viewerDetailCase by remember { mutableStateOf<CaseEntity?>(null) }
    fun setActiveSnapshot(id: Long) {
        changesPrefs.edit().putLong(KEY_ACTIVE_SNAPSHOT_ID, id).apply()
        activeSnapshotId = id
        viewerQuery = ""
        viewerDetailCase = null
    }
    // The two snapshots being compared (older id first, newer id second);
    // null when no comparison is open.
    var comparePair by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    var showMergeConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }

    // The diff for the expanded snapshot, computed on demand (parsing two
    // snapshot JSONs is fast, but there is no need to do it for every row).
    val expandedDiff: SnapshotDiff? = remember(expandedId, snapshots) {
        val snap = snapshots.firstOrNull { it.id == expandedId } ?: return@remember null
        val idx = snapshots.indexOf(snap)
        val prev = snapshots.getOrNull(idx + 1)
        val curData = SnapshotStore.fromJson(snap.dataJson) ?: return@remember null
        val prevData = prev?.let { SnapshotStore.fromJson(it.dataJson) } ?: SnapshotData()
        diffSnapshots(curData, prevData)
    }

    // The diff for the two snapshots being compared: the older one (smaller
    // id) is the "previous", the newer one (larger id) is the "current".
    // createdAt is a Jalali string, so ids — not dates — decide the order.
    val compareDiff: SnapshotDiff? = remember(comparePair, snapshots) {
        val pair = comparePair ?: return@remember null
        val older = snapshots.firstOrNull { it.id == pair.first } ?: return@remember null
        val newer = snapshots.firstOrNull { it.id == pair.second } ?: return@remember null
        val olderData = SnapshotStore.fromJson(older.dataJson) ?: return@remember null
        val newerData = SnapshotStore.fromJson(newer.dataJson) ?: return@remember null
        diffSnapshots(newerData, olderData)
    }

    // The snapshot being viewed (read-only): its data comes from its own
    // JSON via SnapshotStore.fromJson — the live database is never read
    // or written for the viewer.
    val activeSnapshot = snapshots.firstOrNull { it.id == activeSnapshotId }
    val activeData: SnapshotData? = remember(activeSnapshot) {
        activeSnapshot?.let { SnapshotStore.fromJson(it.dataJson) }
    }
    // Drop a stale active id once the real list has arrived and the
    // snapshot is no longer in it (e.g. deleted from another path).
    LaunchedEffect(snapshots) {
        if (snapshots.isNotEmpty() && activeSnapshotId != NO_ACTIVE_SNAPSHOT && activeSnapshot == null) {
            setActiveSnapshot(NO_ACTIVE_SNAPSHOT)
        }
    }

    fun doMerge() {
        if (selectedIds.isEmpty() || isWorking) return
        isWorking = true
        statusText = ""
        val selected = snapshots.filter { it.id in selectedIds }
        scope.launch {
            val result = withContext(Dispatchers.IO) { viewModel.mergeSnapshots(selected) }
            statusText = result
            isWorking = false
            selectedIds = emptySet()
            comparePair = null
        }
    }

    fun doDelete() {
        if (selectedIds.isEmpty() || isWorking) return
        isWorking = true
        viewModel.deleteSnapshots(selectedIds.toList())
        // Deleting the active snapshot clears the activation (and its viewer).
        if (activeSnapshotId in selectedIds) setActiveSnapshot(NO_ACTIVE_SNAPSHOT)
        statusText = "✅ ${selectedIds.size} اسنپ‌شات حذف شد"
        selectedIds = emptySet()
        expandedId = null
        comparePair = null
        isWorking = false
    }

    if (showMergeConfirm) {
        AlertDialog(
            onDismissRequest = { showMergeConfirm = false },
            title = { Text("ادغام اسنپ‌شات‌ها") },
            text = { Text("ادغام ${selectedIds.size} اسنپ‌شات در داده‌های فعلی؟\nاطلاعات جدید جایگزین می‌شود و فیلدهای خالی جایگذاری نمی‌شوند. پرونده‌های بایگانی‌شده دست نمی‌خورند.") },
            confirmButton = {
                TextButton(onClick = { showMergeConfirm = false; doMerge() }) { Text("ادغام", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = {
                TextButton(onClick = { showMergeConfirm = false }) { Text("لغو") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("حذف اسنپ‌شات‌ها") },
            text = { Text("حذف ${selectedIds.size} اسنپ‌شات؟\nاین کار برگشت‌پذیر نیست.") },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; doDelete() }) { Text("حذف", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("لغو") }
            }
        )
    }

    // Case detail from the active snapshot (read-only viewer).
    val detailCase = viewerDetailCase
    if (detailCase != null && activeData != null) {
        SnapshotCaseDetailDialog(caseItem = detailCase, data = activeData, onDismiss = { viewerDetailCase = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تغییرات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            androidx.compose.material.icons.Icons.Default.ArrowBack,
                            contentDescription = "بازگشت"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Text(
                "هر بروزرسانی و ایمپورت یک اسنپ‌شات از داده‌ها ذخیره می‌کند. روی یکی بزنید تا تغییرهایش نسبت به قبلی را ببینید؛ دو تا را انتخاب کنید و با «مقایسه دو اسنپ‌شات» تغییرها را ببینید؛ چند تا را انتخاب کنید و ادغام کنید یا حذف کنید. برای دیدن داده‌های یک اسنپ‌شات، آن را با «فعال کردن» فعال کنید؛ فعال‌سازی فقط نمایش است و داده‌های فعلی برنامه را تغییر نمی‌دهد.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (statusText.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (statusText.startsWith("✅")) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) { Text(statusText, modifier = Modifier.padding(12.dp), fontSize = 13.sp) }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (selectedIds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showMergeConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isWorking
                    ) {
                        if (isWorking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("🔀 ادغام (${selectedIds.size})", fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = !isWorking
                    ) {
                        Text("🗑 حذف (${selectedIds.size})", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
                // Exactly two selected: compare them (older → newer). The
                // button sits on its own row because the full label does not
                // fit next to merge/delete in a three-button row.
                if (selectedIds.size == 2) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val selected = snapshots.filter { it.id in selectedIds }.sortedBy { it.id }
                            if (selected.size == 2) {
                                comparePair = selected[0].id to selected[1].id
                                expandedId = null
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isWorking
                    ) {
                        Text("🔍 مقایسه دو اسنپ‌شات", fontSize = 13.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (snapshots.isEmpty()) {
                NezamEmptyState(
                    emoji = "📸",
                    title = "هنوز اسنپ‌شاتی نیست",
                    subtitle = "بعد از اولین بروزرسانی یا ایمپورت، اسنپ‌شات اینجا ذخیره می‌شود"
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The active snapshot viewer: first item of the list,
                    // so its cases scroll with the rest. Read-only — it
                    // shows the snapshot's own JSON, never live data.
                    if (activeSnapshot != null && activeData != null) {
                        item {
                            SnapshotViewerCard(
                                snapshot = activeSnapshot,
                                data = activeData,
                                query = viewerQuery,
                                onQueryChange = { viewerQuery = it },
                                onDeactivate = { setActiveSnapshot(NO_ACTIVE_SNAPSHOT) },
                                onCaseClick = { viewerDetailCase = it }
                            )
                        }
                    }

                    // The open two-snapshot comparison: first item of the
                    // list, so a long full diff scrolls with the rest.
                    val pair = comparePair
                    if (pair != null && compareDiff != null) {
                        val older = snapshots.firstOrNull { it.id == pair.first }
                        val newer = snapshots.firstOrNull { it.id == pair.second }
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("🔍 مقایسهٔ دو اسنپ‌شات", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            if (older != null && newer != null) {
                                                Text(
                                                    "از «${older.label}» (${displayShamsiDate(older.createdAt)}) به «${newer.label}» (${displayShamsiDate(newer.createdAt)})",
                                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        IconButton(onClick = { comparePair = null }) {
                                            Text("✕", fontSize = 16.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SnapshotDiffContent(
                                        diff = compareDiff,
                                        header = "تغییرها از اسنپ‌شات قبلی به اسنپ‌شات جدید:",
                                        showAll = true
                                    )
                                }
                            }
                        }
                    }

                    items(snapshots) { snap ->
                        val selected = snap.id in selectedIds
                        val expanded = expandedId == snap.id
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            ),
                            onClick = { expandedId = if (expanded) null else snap.id }
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            selectedIds = if (it) selectedIds + snap.id else selectedIds - snap.id
                                            comparePair = null
                                        }
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(snap.label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("📅 ${displayShamsiDate(snap.createdAt)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            "${snap.caseCount} پرونده | ${snap.feeCount} حق‌الزحمه | ${snap.reportCount} گزارش",
                                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (snap.id == activeSnapshotId) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                        ) {
                                            Text(
                                                "✓ فعال",
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    } else {
                                        TextButton(onClick = { setActiveSnapshot(snap.id) }) {
                                            Text("فعال کردن", fontSize = 12.sp)
                                        }
                                    }
                                    Text(if (expanded) "▲" else "▼", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (expanded && expandedDiff != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(8.dp))
                                    SnapshotDiffContent(
                                        diff = expandedDiff,
                                        header = "تغییر نسبت به اسنپ‌شات قبلی:",
                                        showAll = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The body of a snapshot diff, shared by the single-snapshot expansion and
 *  the two-snapshot comparison. `showAll = false` keeps the old truncation
 *  (10 changed cases, 5 added/removed numbers); `showAll = true` shows the
 *  full diff — every changed case with all of its per-field changes, and
 *  every added/removed case number. */
@Composable
private fun SnapshotDiffContent(diff: SnapshotDiff, header: String, showAll: Boolean) {
    Text(header, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    Spacer(modifier = Modifier.height(4.dp))
    Text("➕ ${diff.addedCases.size} پروندهٔ جدید", fontSize = 12.sp)
    Text("➖ ${diff.removedCases.size} پروندهٔ حذف‌شده", fontSize = 12.sp)
    Text("✏️ ${diff.changedCases.size} پروندهٔ تغییرکرده", fontSize = 12.sp)
    Text(
        "حق‌الزحمه: ${if (diff.feeDelta >= 0) "+" else ""}${diff.feeDelta} | گزارش: ${if (diff.reportDelta >= 0) "+" else ""}${diff.reportDelta}",
        fontSize = 12.sp
    )
    val shown = if (showAll) diff.changedCases else diff.changedCases.take(10)
    if (shown.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        shown.forEach { cc ->
            Text(
                "${cc.caseNumber} — ${cc.owner}",
                fontSize = 12.sp, fontWeight = FontWeight.Bold
            )
            cc.changes.forEach { ch ->
                Text("  • $ch", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!showAll && diff.changedCases.size > 10) {
            Text("…و ${diff.changedCases.size - 10} مورد دیگر", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    val addedShown = if (showAll) diff.addedCases else diff.addedCases.take(5)
    if (addedShown.isNotEmpty()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "پرونده‌های جدید: " + addedShown.joinToString("، ") { it.caseNumber },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    val removedShown = if (showAll) diff.removedCases else diff.removedCases.take(5)
    if (removedShown.isNotEmpty()) {
        Text(
            "پرونده‌های حذف‌شده: " + removedShown.joinToString("، ") { it.caseNumber },
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The viewer for the active snapshot: summary counts, a note that this is
 *  read-only, a search field over the snapshot's own cases, and one row
 *  per case (tap for details). Everything comes from the snapshot JSON —
 *  the live database is never touched. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotViewerCard(
    snapshot: SnapshotEntity,
    data: SnapshotData,
    query: String,
    onQueryChange: (String) -> Unit,
    onDeactivate: () -> Unit,
    onCaseClick: (CaseEntity) -> Unit
) {
    val q = toPersianChars(query).trim()
    val visibleCases = remember(data, q) {
        if (q.isBlank()) data.cases
        else data.cases.filter {
            toPersianChars(it.caseNumber).contains(q) || toPersianChars(it.owner).contains(q)
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("👁 نمایش اسنپ‌شات", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "«${snapshot.label}» — ${displayShamsiDate(snapshot.createdAt)}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${data.cases.size} پرونده | ${data.engineers.size} مهندس | ${data.fees.size} حق‌الزحمه | ${data.reports.size} گزارش",
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onDeactivate) {
                    Text("غیرفعال کردن", fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "نمایش داده‌های اسنپ‌شات — داده‌های فعلی برنامه تغییر نمی‌کند.",
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            NezamSearchField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = "جستجوی شماره پرونده / مالک..."
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (visibleCases.isEmpty()) {
                Text(
                    if (data.cases.isEmpty()) "این اسنپ‌شات پرونده‌ای ندارد" else "موردی یافت نشد",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                visibleCases.forEach { case ->
                    val engCount = data.engineers.count { it.caseNumber == case.caseNumber }
                    val feeCount = data.fees.count { it.caseNumber == case.caseNumber }
                    val repCount = data.reports.count { it.caseNumber == case.caseNumber }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { onCaseClick(case) }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(toPersianChars(case.owner), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text(case.caseNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$engCount مهندس | $feeCount حق‌الزحمه | $repCount گزارش",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Full details of one case as stored in the snapshot (read-only): case
 *  fields, then its engineers, fees and reports from the same snapshot. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SnapshotCaseDetailDialog(caseItem: CaseEntity, data: SnapshotData, onDismiss: () -> Unit) {
    val spec = data.specs.firstOrNull { it.caseNumber == caseItem.caseNumber }
    val caseEngineers = data.engineers.filter { it.caseNumber == caseItem.caseNumber }
    val caseFees = data.fees.filter { it.caseNumber == caseItem.caseNumber }
    val caseReports = data.reports.filter { it.caseNumber == caseItem.caseNumber }
    val (group, discipline) = resolveSnapshotGroupDiscipline(caseItem, spec, caseEngineers, caseFees, caseReports)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(toPersianChars(caseItem.owner), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("پرونده: ${caseItem.caseNumber}", fontSize = 13.sp)
                val permitNumber = caseItem.permitNumber.ifBlank { spec?.permitNumber ?: "" }
                val permitDate = caseItem.permitDate.ifBlank { spec?.permitDate ?: "" }
                if (permitNumber.isNotBlank()) Text("شماره پروانه: $permitNumber", fontSize = 12.sp)
                if (permitDate.isNotBlank()) Text("تاریخ صدور: ${displayShamsiDate(permitDate)}", fontSize = 12.sp)
                val address = caseItem.address.ifBlank { spec?.address ?: "" }
                if (address.isNotBlank()) Text("نشانی: ${toPersianChars(address)}", fontSize = 12.sp)
                if (group.isNotBlank()) Text("گروه ساختمانی: ${buildingGroupLetter(group)}", fontSize = 12.sp)
                if (discipline.isNotBlank()) Text("رشته: ${toPersianChars(discipline)}", fontSize = 12.sp)
                if (caseItem.office.isNotBlank()) Text("دفتر: ${caseItem.office}", fontSize = 12.sp)
                if (caseItem.stateTitle.isNotBlank()) Text("وضعیت: ${caseItem.stateTitle}", fontSize = 12.sp)
                if (caseItem.workType.isNotBlank()) Text("نوع کار: ${caseItem.workType}", fontSize = 12.sp)
                if (caseEngineers.isNotEmpty()) {
                    Text("مهندسان", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    caseEngineers.forEach { eng ->
                        Text(
                            "• ${toPersianChars(eng.name)} — ${toPersianChars(eng.discipline)}${if (eng.role.isNotBlank()) " (${eng.role})" else ""}",
                            fontSize = 12.sp
                        )
                    }
                }
                if (caseFees.isNotEmpty()) {
                    Text("حق‌الزحمه", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    caseFees.forEach { fee ->
                        Text(
                            "• ${feeTitle(fee, caseFees)} — ${formatFeeAmount(fee.amount)} — ${payStatusDisplay(fee)}",
                            fontSize = 12.sp
                        )
                    }
                }
                if (caseReports.isNotEmpty()) {
                    Text("گزارش‌ها", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    caseReports.forEach { rep ->
                        val stageLabel = reportStageLabel(rep.reportType, rep.stage)
                        Text(
                            "• ${toPersianChars(rep.reportType)}${if (stageLabel.isNotBlank()) " ($stageLabel)" else ""} — ${rep.visitDate}",
                            fontSize = 12.sp
                        )
                    }
                }
                if (caseEngineers.isEmpty() && caseFees.isEmpty() && caseReports.isEmpty()) {
                    Text(
                        "برای این پرونده مهندس، حق‌الزحمه یا گزارشی در اسنپ‌شات نیست.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("بستن") }
        }
    )
}
