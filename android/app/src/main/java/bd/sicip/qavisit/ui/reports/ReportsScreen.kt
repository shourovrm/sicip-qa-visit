// A1: Reports tab -- segmented In progress / Submitted (with counts), one card per report
// (type chip, flag-count chip, sync state, institute, date+arrival, "x of N sections" progress
// bar), then "Visits today with no report" (own non-deleted visits of the active tour or with
// start_date==today, no report yet) each with its own one-tap "Start report". FAB "New report"
// opens the S1 sheet: type radio (Surprise; Monitoring/QA disabled) + visit radio (active tour +
// today's + upcoming 7 days, own) + Start.
package bd.sicip.qavisit.ui.reports

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.data.db.Trip
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.computeProgress
import bd.sicip.qavisit.ui.common.TwoTabRow
import bd.sicip.qavisit.ui.shell.relativeTime
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class ReportsUiState(
    val reports: List<Report> = emptyList(),
    val myVisits: List<Visit> = emptyList(),
    val activeTrip: Trip? = null,
    val officerName: String = "",
)

// every template this build's assets actually have -- keyed by report/visit type. Loaded once
// with runCatching per entry (not the whole map) so a missing qa-v1.json (before the other
// agent's shared/ commit lands) only means QA reports/visits are quietly left out of this
// screen, never a crash for the surprise-report officer using the app meanwhile.
private fun loadTemplates(context: android.content.Context): Map<String, ReportTemplate> =
    buildMap {
        listOf(REPORT_TYPE_SURPRISE, REPORT_TYPE_QA).forEach { type ->
            runCatching { templateForType(context, type) }.getOrNull()?.let { put(type, it) }
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(officerId: String, db: AppDb, onOpenReport: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates = remember { loadTemplates(context) }

    // same reasoning as VisitsScreen/TeamScreen: kick a pull on entry so a colleague's sync
    // isn't the only thing that refreshes this list.
    LaunchedEffect(Unit) { SyncNow.enqueue(context) }

    val state by remember(db) {
        combine(
            db.reportDao().byOfficerFlow(officerId),
            db.visitDao().byOfficerFlow(officerId),
            db.tripDao().activeTripFlow(officerId),
            db.officerDao().allFlow(),
        ) { reports, myVisits, activeTrip, officers ->
            ReportsUiState(reports, myVisits, activeTrip, officers.firstOrNull { it.id == officerId }?.name ?: "")
        }
    }.collectAsState(initial = ReportsUiState())

    var showSubmitted by remember { mutableStateOf(false) }
    var showNewReportSheet by remember { mutableStateOf(false) }
    // set only for a legacy Monitoring Visit row with no visit_type yet (spec §1) -- "Start
    // report" on its row must ask which report instead of guessing.
    var pendingReportVisit by remember { mutableStateOf<Visit?>(null) }

    val activeTrip = state.activeTrip
    val visitById = state.myVisits.associateBy { it.id }
    val drafts = state.reports.filter { it.status == "draft" }
    val submitted = state.reports.filter { it.status == "submitted" }
    val today = LocalDate.now().toString() // local date: the utc one is yesterday before 06:00 in dhaka
    val reportedVisitIds = state.reports.map { it.visitId }.toSet()
    val noReportVisits = state.myVisits.filter { v ->
        v.id !in reportedVisitIds && visitEligibleForReport(v) &&
            ((activeTrip != null && v.tripId == activeTrip.id) || v.startDate == today)
    }

    fun startReport(visit: Visit, type: String) {
        scope.launch {
            val report = startReportForVisit(db, context, visit, officerId, state.officerName, type)
            onOpenReport(report.id)
        }
    }

    Scaffold(
        // nested in AppShell's scaffold, which already pads for the system bars
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showNewReportSheet = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("New report") },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            TwoTabRow(
                leftLabel = "In progress (${drafts.size})",
                rightLabel = "Submitted (${submitted.size})",
                leftSelected = !showSubmitted,
                onSelect = { showSubmitted = !it },
            )
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                val shown = if (showSubmitted) submitted else drafts
                if (shown.isEmpty() && (showSubmitted || noReportVisits.isEmpty())) {
                    item {
                        Text(
                            if (showSubmitted) "No submitted reports yet."
                            else "No reports in progress. Tap New report and pick the visit you are on, or one from the last 30 days.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(shown) { report ->
                    val visit = visitById[report.visitId]
                    ReportCard(report, visit, templates[report.type], onClick = { onOpenReport(report.id) })
                }
                if (!showSubmitted && noReportVisits.isNotEmpty()) {
                    item {
                        Text(
                            "VISITS TODAY WITH NO REPORT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    items(noReportVisits) { visit ->
                        NoReportVisitRow(
                            visit,
                            onStart = {
                                val forcedType = reportTypeForVisit(visit)
                                if (forcedType != null) startReport(visit, forcedType) else pendingReportVisit = visit
                            },
                        )
                    }
                }
            }
        }
    }

    if (showNewReportSheet) {
        NewReportSheet(
            visits = candidateVisits(state.myVisits.filter { visitEligibleForReport(it) }, activeTrip, today),
            templates = templates,
            onDismiss = { showNewReportSheet = false },
            onStart = { visit, type -> showNewReportSheet = false; startReport(visit, type) },
        )
    }

    pendingReportVisit?.let { visit ->
        ReportTypePickerDialog(
            institute = visit.institute,
            onPick = { type -> pendingReportVisit = null; startReport(visit, type) },
            onDismiss = { pendingReportVisit = null },
        )
    }
}

// legacy Monitoring Visit rows with no visit_type (spec §1) can't pick a template on their own --
// this small dialog is the officer's fallback, shared by ReportsScreen.kt's own two entry points
// (New-report sheet, "Start report" row) and HomeScreen.kt's ongoing-visit report line.
@Composable
fun ReportTypePickerDialog(institute: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which report?") },
        text = { Text("\"$institute\" was scheduled before the monitoring type existed. Pick which report to start.") },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = { onPick(REPORT_TYPE_SURPRISE) }) { Text("Surprise visit") }
                TextButton(onClick = { onPick(REPORT_TYPE_QA) }) { Text("QA visit") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// active tour visits + last 30 days + next 7 days, own. reports get written during AND after
// a visit, so recent past visits must be pickable. active tour first, then newest first.
private fun candidateVisits(myVisits: List<Visit>, activeTrip: Trip?, today: String): List<Visit> {
    val todayDate = runCatching { LocalDate.parse(today) }.getOrDefault(LocalDate.now())
    val monthBack = todayDate.minusDays(30).toString()
    val weekAhead = todayDate.plusDays(7).toString()
    fun onActiveTour(v: Visit) = activeTrip != null && v.tripId == activeTrip.id
    return myVisits
        .filter { v -> onActiveTour(v) || v.startDate in monthBack..weekAhead }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Visit> { onActiveTour(it) }.thenByDescending { it.startDate })
}

@Composable
private fun ReportCard(report: Report, visit: Visit?, template: ReportTemplate?, onClick: () -> Unit) {
    val data = remember(report.data) { ReportData.parse(report.data) }
    // template can only be null when this build's assets don't have this report's type yet
    // (qa-v1.json missing) -- the card still opens (registry loads it lazily where it's actually
    // needed), it just can't show a progress bar for something it can't compute.
    val progress = remember(data, template) { template?.let { computeProgress(it, data) } }
    val flaggedSections = progress?.sections?.values?.count { it.flagged } ?: 0

    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChipText(reportTypeLabel(report.type))
                if (flaggedSections > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.height(14.dp))
                        Text("$flaggedSections", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                Text(
                    if (report.dirty) "Saved on phone" else "Synced ${relativeTime(report.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            Text(visit?.institute ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${visit?.startDate ?: ""}${data.field("arrival_time").let { if (it.isNotBlank()) " · arrived $it" else "" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (progress != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LinearProgressIndicator(
                        progress = { if (progress.sectionsCounted == 0) 0f else progress.sectionsDone.toFloat() / progress.sectionsCounted },
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Text(
                        "${progress.sectionsDone} of ${progress.sectionsCounted} sections",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistChipText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(99))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun NoReportVisitRow(visit: Visit, onStart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(visit.institute, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(visit.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onStart, modifier = Modifier.height(40.dp)) { Text("Start report") }
        }
    }
}

// mockup: the visit is picked FIRST, then the "Report" section shows ONE row -- the type the
// visit's own monitoring type already decided, label = that template's own `short` (spec: "No
// 'Annex-3' text in UI", never hardcoded here). Only a legacy visit with no visit_type falls
// back to letting the officer choose between the two (radio rows, same as before this feature).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReportSheet(
    visits: List<Visit>,
    templates: Map<String, ReportTemplate>,
    onDismiss: () -> Unit,
    onStart: (Visit, String) -> Unit,
) {
    var selectedVisitId by remember(visits) { mutableStateOf(visits.firstOrNull()?.id) }
    val selectedVisit = visits.firstOrNull { it.id == selectedVisitId }
    val forcedType = selectedVisit?.let { reportTypeForVisit(it) }
    var chosenType by remember { mutableStateOf(forcedType) }
    LaunchedEffect(selectedVisitId) { chosenType = forcedType }

    // open fully: half-open hides the pinned Start button under the visit list
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("New report", style = MaterialTheme.typography.titleLarge)

            Text("VISIT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (visits.isEmpty()) {
                Text(
                    "No matching visits in the last 30 days or the next 7.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // the visit list scrolls on its own so Start report stays pinned below it
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                visits.forEach { visit ->
                    VisitOptionRow(
                        visit = visit,
                        selected = visit.id == selectedVisitId,
                        onClick = { selectedVisitId = visit.id },
                    )
                }
            }

            Text("REPORT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (selectedVisit == null) {
                Text("Pick a visit first.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (forcedType != null) {
                val template = templates[forcedType]
                TypeOptionRow(
                    label = template?.short ?: reportTypeLabel(forcedType),
                    subtitle = template?.let { "${templateSummary(it)} · set by the visit's monitoring type" } ?: "Set by the visit's monitoring type",
                    selected = true,
                    enabled = template != null,
                    onClick = {},
                )
            } else {
                listOf(REPORT_TYPE_SURPRISE, REPORT_TYPE_QA).forEach { type ->
                    val template = templates[type]
                    TypeOptionRow(
                        label = template?.short ?: reportTypeLabel(type),
                        subtitle = template?.let { templateSummary(it) } ?: "Not available in this app version",
                        selected = chosenType == type,
                        enabled = template != null,
                        onClick = { chosenType = type },
                    )
                }
            }

            Button(
                onClick = {
                    val visit = selectedVisit ?: return@Button
                    val type = chosenType ?: return@Button
                    onStart(visit, type)
                },
                enabled = selectedVisit != null && chosenType != null && templates.containsKey(chosenType),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Start") }
        }
    }
}

@Composable
private fun TypeOptionRow(label: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (enabled) {
                RadioButton(selected = selected, onClick = onClick)
            } else {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun VisitOptionRow(visit: Visit, selected: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RadioButton(selected = selected, onClick = onClick)
            Column {
                Text(visit.institute, style = MaterialTheme.typography.bodyLarge)
                Text("${visit.purpose} · ${visit.startDate}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
