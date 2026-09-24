// A2: one report's section list -- progress bar up top, a row per section (letter badge green
// when done, red when flagged, neutral otherwise; second line is "x of y answered" or an entry/
// tick count for a section with nothing to count, e.g. an all-cards section with zero rows
// filled in yet), bottom bar Preview PDF + Review. Overflow menu offers "Delete draft" (drafts
// only -- submitted reports are read-only, spec "Lifecycle").
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.SectionProgress
import bd.sicip.qavisit.domain.report.computeProgress
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportHub(
    reportId: String,
    db: AppDb,
    registry: ReportEditorRegistry,
    onOpenSection: (String) -> Unit,
    onReview: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val template = remember { surpriseTemplate(context) }

    // first thing that touches this report id creates its shared editor (see
    // ReportEditorRegistry's own comment) -- every section/review screen opened from here reuses
    // the SAME editor instance, so there is only ever one in-memory copy of this report's data.
    val report by remember(reportId) { db.reportDao().byIdFlow(reportId).filterNotNull() }.collectAsState(initial = null)
    var visit by remember { mutableStateOf<Visit?>(null) }
    var officerName by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var pdfBusy by remember { mutableStateOf(false) }

    LaunchedEffect(report?.visitId, report?.officerId) {
        val r = report ?: return@LaunchedEffect
        visit = db.visitDao().byId(r.visitId)
        officerName = db.officerDao().byId(r.officerId)?.name ?: ""
    }

    val current = report ?: return
    val editor = registry.forReport(current)
    // read from the editor, not from `current` -- the editor may already be ahead of the last
    // Room row this Flow delivered (a debounced write from a section screen still in flight).
    val data = editor.data
    val progress = remember(data) { computeProgress(template, data) }

    // insets already applied by AppShell's scaffold
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Column {
                        Text(reportTypeLabel(editor.report.type))
                        Text(visit?.institute ?: "", style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (editor.report.status == "draft") {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Delete draft") },
                                onClick = { menuOpen = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
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
                ) { Text(if (pdfBusy) "Preparing…" else "Preview PDF") }
                Button(onClick = onReview, colors = actionButtonColors(), modifier = Modifier.weight(1f).height(48.dp)) { Text("Review") }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinearProgressIndicator(
                        progress = { if (progress.sectionsCounted == 0) 0f else progress.sectionsDone.toFloat() / progress.sectionsCounted },
                        modifier = Modifier.weight(1f).height(6.dp),
                    )
                    Text(
                        "${progress.sectionsDone} / ${progress.sectionsCounted}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(template.sections) { section ->
                val sectionProgress = progress.sections[section.key]
                SectionRow(
                    section = section,
                    progress = sectionProgress,
                    subtitle = sectionSubtitle(section, sectionProgress, data, progress.flagsTicked.size),
                    onClick = { onOpenSection(section.key) },
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete draft?") },
            text = { Text("This removes the draft \"${reportTypeLabel(editor.report.type)}\" report for \"${visit?.institute ?: ""}\". This can't be undone from the app.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.reportDao().softDelete(editor.report.id, Instant.now().toString())
                            showDeleteConfirm = false
                            onDone()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionRow(section: ReportSection, progress: SectionProgress?, subtitle: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LetterBadge(section.letter, progress)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = MaterialTheme.typography.bodyLarge)
                    if (section.optional) OptionalTag()
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LetterBadge(letter: String, progress: SectionProgress?) {
    val (bg, fg) = when {
        progress?.flagged == true -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
        progress?.done == true -> Color(0xFF1C6B38) to Color.White
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier.size(30.dp).background(bg, RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(letter, color = fg, style = MaterialTheme.typography.labelLarge) }
}

// second line under a section's title. sections that DO count toward progress (total > 0) show
// a plain ratio; the few that don't (a flags-only block, or a cards block nobody has added a
// row to yet) fall back to an entry/tick count instead of "0 of 0" -- generic over any future
// block shape, never hardcodes a section's own copy.
private fun sectionSubtitle(section: ReportSection, progress: SectionProgress?, data: ReportData, flagsTickedCount: Int): String {
    if (progress != null && progress.total > 0) {
        return if (progress.flagged) "${progress.answered} of ${progress.total} · flagged" else "${progress.answered} of ${progress.total}"
    }
    if (section.blocks.any { it is ReportBlock.Flags }) {
        return if (flagsTickedCount > 0) "$flagsTickedCount ticked" else "None ticked"
    }
    val cardsBlocks = section.blocks.filterIsInstance<ReportBlock.Cards>()
    if (cardsBlocks.isNotEmpty()) {
        val n = cardsBlocks.sumOf { data.cards(it.key).size }
        return if (n == 0) "Not started" else "$n ${if (n == 1) "entry" else "entries"}"
    }
    return "Not started"
}
