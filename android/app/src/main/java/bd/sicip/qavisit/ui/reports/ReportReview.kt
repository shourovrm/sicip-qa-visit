// S2: review before submitting -- answer-count summary tiles, flags ticked, and every still-
// blank checklist item (tapping one jumps straight to its section). A blank item never blocks
// submitting (spec: "done = total > 0 && answered == total" is informational only here); it
// just prints "Not answered" in the PDF. Submit is a plain AlertDialog confirm; once submitted
// the report is read-only everywhere (spec "Lifecycle") -- reopening this screen on an already-
// submitted report just hides the Submit button.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.data.sync.SyncNow
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.computeProgress
import bd.sicip.qavisit.ui.theme.LocalToneColors
import bd.sicip.qavisit.ui.theme.forToneId
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

// where an unanswered checklist item id came from -- lets a "Not answered" row jump straight to
// its section instead of making the officer hunt for it.
private data class ChecklistItemLocation(val section: ReportSection, val text: String)

private fun checklistItemLocations(template: ReportTemplate): Map<String, ChecklistItemLocation> {
    val map = mutableMapOf<String, ChecklistItemLocation>()
    template.sections.forEach { section ->
        section.blocks.filterIsInstance<ReportBlock.Checklist>().forEach { block ->
            block.items.forEach { item -> map[item.id] = ChecklistItemLocation(section, item.text) }
        }
    }
    return map
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportReview(
    reportId: String,
    db: AppDb,
    registry: ReportEditorRegistry,
    onOpenSection: (String) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val template = remember { surpriseTemplate(context) }
    val itemLocations = remember(template) { checklistItemLocations(template) }

    val report by remember(reportId) { db.reportDao().byIdFlow(reportId).filterNotNull() }.collectAsState(initial = null)
    var visit by remember { mutableStateOf<Visit?>(null) }
    var officerName by remember { mutableStateOf("") }
    var showSubmitConfirm by remember { mutableStateOf(false) }
    var pdfBusy by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    LaunchedEffect(report?.visitId, report?.officerId) {
        val r = report ?: return@LaunchedEffect
        visit = db.visitDao().byId(r.visitId)
        officerName = db.officerDao().byId(r.officerId)?.name ?: ""
    }

    val current = report ?: return
    // reuses the SAME editor any section screen already opened for this report id -- see
    // ReportEditorRegistry's comment. Submit (below) then flushes+writes the truly-latest data,
    // never a copy this screen's own first composition happened to see.
    val editor = registry.forReport(current)
    val progress = remember(editor.data) { computeProgress(template, editor.data) }

    // insets already applied by AppShell's scaffold
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text("Review")
                        Text(visit?.institute ?: "", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            // opaque bar: the list scrolls underneath it
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (pdfBusy) return@OutlinedButton
                        pdfBusy = true
                        scope.launch {
                            shareReportPdf(context, template, editor.report, officerName)
                            pdfBusy = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) { Text(if (pdfBusy) "Preparing…" else "PDF") }
                if (editor.report.status != "submitted") {
                    Button(
                        onClick = { showSubmitConfirm = true },
                        colors = actionButtonColors(),
                        modifier = Modifier.weight(1.4f).height(48.dp),
                    ) { Text("Submit") }
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(72.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(template.answers) { answer ->
                        val tones = LocalToneColors.current
                        Card(
                            colors = CardDefaults.cardColors(containerColor = tones.forToneId(answer.tone).copy(alpha = 0.16f)),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "${progress.answerCounts[answer.id] ?: 0}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = tones.forToneId(answer.tone),
                                )
                                Text(answer.label, style = MaterialTheme.typography.labelSmall, color = tones.forToneId(answer.tone))
                            }
                        }
                    }
                }
            }

            if (progress.flagsTicked.isNotEmpty()) {
                item { Text("Flags ticked", style = MaterialTheme.typography.labelLarge) }
                items(progress.flagsTicked) { flagId ->
                    val text = template.sections
                        .flatMap { it.blocks }.filterIsInstance<ReportBlock.Flags>()
                        .flatMap { it.items }.firstOrNull { it.id == flagId }?.text ?: flagId
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Flag, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            if (progress.unansweredChecklistItemIds.isNotEmpty()) {
                item {
                    Text(
                        "Not answered · ${progress.unansweredChecklistItemIds.size}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                items(progress.unansweredChecklistItemIds) { itemId ->
                    val location = itemLocations[itemId]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = location != null) { location?.let { onOpenSection(it.section.key) } }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${location?.section?.letter ?: ""}. ${location?.text ?: itemId}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = null,
                            modifier = Modifier.height(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    if (editor.report.status == "submitted") {
                        "This report is submitted and read-only. The PDF can be shared at any time."
                    } else {
                        "After submitting, the report becomes read-only. The admin can read it on the web, and the PDF can be shared at any time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showSubmitConfirm) {
        AlertDialog(
            onDismissRequest = { if (!submitting) showSubmitConfirm = false },
            title = { Text("Submit report?") },
            text = { Text("Once submitted, this report can no longer be edited or deleted from the app.") },
            confirmButton = {
                Button(
                    onClick = {
                        if (submitting) return@Button
                        submitting = true
                        scope.launch {
                            editor.submit()
                            SyncNow.enqueue(context) // push the submitted report promptly, don't wait for the periodic job
                            submitting = false
                            showSubmitConfirm = false
                            onBack()
                        }
                    },
                ) { Text(if (submitting) "Submitting…" else "Submit") }
            },
            dismissButton = { TextButton(onClick = { if (!submitting) showSubmitConfirm = false }) { Text("Cancel") } },
        )
    }
}
