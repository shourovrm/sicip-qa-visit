// A3: one section per screen, generic renderer over the section's blocks (ui/reports/ReportBlocks.kt
// does the per-block work). Top bar shows "<letter>. <title>" + "<answered> of <total> answered";
// bottom bar is prev-letter back + "Next: <letter>. <short>", or "Review" on the last section.
// Autosave only -- there is no Save button anywhere on this screen (see ui/reports/ReportEditing.kt).
// Every way off this screen (prev/next/Review, the app-bar back arrow, or the system Back
// gesture/button) flushes the shared ReportEditor first so a remark typed in the last ~400ms is
// on disk before navigating, shrinking the window a process death could drop it in.
package bd.sicip.qavisit.ui.reports

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.domain.report.computeProgress
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportSectionScreen(
    reportId: String,
    sectionKey: String,
    db: AppDb,
    registry: ReportEditorRegistry,
    context: android.content.Context,
    onOpenSection: (String) -> Unit,
    onReview: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val template = remember { surpriseTemplate(context) }
    val reportFlow = remember(reportId) { db.reportDao().byIdFlow(reportId).filterNotNull() }
    val report by reportFlow.collectAsState(initial = null)
    val current = report ?: return

    // reuses the SAME editor the hub (and any other section already visited) is using for this
    // report id -- see ReportEditorRegistry's comment for why that matters.
    val editor = registry.forReport(current)
    val section = template.sections.firstOrNull { it.key == sectionKey } ?: return
    val sectionIndex = template.sections.indexOf(section)
    val prevSection = template.sections.getOrNull(sectionIndex - 1)
    val nextSection = template.sections.getOrNull(sectionIndex + 1)

    val progress = remember(editor.data) { computeProgress(template, editor.data) }
    val sectionProgress = progress.sections[section.key]

    // flush-then-navigate: cancels any pending debounced write and persists immediately, so the
    // Room row is fully caught up before the next screen (or the system back stack) takes over.
    fun leave(next: () -> Unit) {
        scope.launch {
            editor.flush()
            next()
        }
    }

    BackHandler(onBack = { leave(onBack) })

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("${section.letter}. ${section.title}")
                        if (sectionProgress != null && sectionProgress.total > 0) {
                            Text(
                                "${sectionProgress.answered} of ${sectionProgress.total} answered",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leave(onBack) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (prevSection != null) {
                    OutlinedButton(onClick = { leave { onOpenSection(prevSection.key) } }, modifier = Modifier.height(48.dp)) {
                        Text(prevSection.letter)
                    }
                }
                Button(
                    onClick = { leave { if (nextSection != null) onOpenSection(nextSection.key) else onReview() } },
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text(if (nextSection != null) "Next: ${nextSection.letter}. ${nextSection.short}" else "Review")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp, innerPadding.calculateTopPadding() + 8.dp, 16.dp, innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            section.note?.let { note ->
                item {
                    Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(section.blocks) { block ->
                ReportBlockView(block, template.answers, editor.data, editor.readOnly, editor)
            }
        }
    }
}
