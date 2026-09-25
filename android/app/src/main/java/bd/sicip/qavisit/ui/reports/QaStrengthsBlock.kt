// QA s13 editor (spec 2026-09-26 §4): one card per component pair -- heading, Strengths box,
// Weaknesses box (stacked on the phone), "Draft from remarks" with a preview before anything is
// written. "Draft all empty" on top fills every pair whose boxes are both blank, one call at a
// time, writing directly (nothing to lose).
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.domain.report.ComponentPair
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.StrengthsDraft
import bd.sicip.qavisit.domain.report.componentNotes
import bd.sicip.qavisit.domain.report.hasNotes
import kotlinx.coroutines.launch

private fun ReportData.withDraft(pair: ComponentPair, draft: StrengthsDraft): ReportData =
    withField(pair.strength, draft.strengths.joinToString("\n"))
        .withField(pair.weakness, draft.weaknesses.joinToString("\n"))

private fun pairIsEmpty(data: ReportData, pair: ComponentPair): Boolean =
    data.field(pair.strength).isBlank() && data.field(pair.weakness).isBlank()

@Composable
fun StrengthsPairsBlockView(block: ReportBlock.Fields, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { QaDraftAi(context) }
    var runningAll by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }

    val emptyPairsWithNotes = block.pairs.filter { pairIsEmpty(data, it) && hasNotes(componentNotes(template, data, it.source)) }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !runningAll && emptyPairsWithNotes.isNotEmpty(),
                    onClick = {
                        summary = null
                        runningAll = true
                        scope.launch {
                            var drafted = 0
                            var fromMarks = 0
                            // sequential on purpose: one Worker call at a time (quota, spec §4)
                            emptyPairsWithNotes.forEach { pair ->
                                val result = ai.component(componentNotes(template, editor.data, pair.source))
                                // officer may have typed meanwhile -- never overwrite that
                                if (pairIsEmpty(editor.data, pair)) {
                                    editor.editNow(editor.data.withDraft(pair, result.value))
                                    drafted++
                                    if (result.fromMarks) fromMarks++
                                }
                            }
                            runningAll = false
                            val noun = if (drafted == 1) "component" else "components"
                            summary = if (fromMarks > 0) "Drafted $drafted $noun ($fromMarks from marks only)" else "Drafted $drafted $noun"
                        }
                    },
                    modifier = Modifier.height(48.dp),
                ) { Text("Draft all empty") }
                if (runningAll) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            summary?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        block.pairs.forEach { pair ->
            PairCard(block, pair, template, data, readOnly, editor, ai, busy = runningAll)
        }
    }
}

@Composable
private fun PairCard(
    block: ReportBlock.Fields,
    pair: ComponentPair,
    template: ReportTemplate,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    ai: QaDraftAi,
    busy: Boolean,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Drafted<StrengthsDraft>?>(null) }
    val notes = componentNotes(template, data, pair.source)
    val sourceBadge = template.sections.firstOrNull { it.key == pair.source }?.badge ?: pair.source

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(pair.component, style = MaterialTheme.typography.titleSmall)
            listOf(pair.strength to "Strengths", pair.weakness to "Weaknesses").forEach { (key, label) ->
                val field = block.fields.firstOrNull { it.key == key } ?: return@forEach
                FieldEditor(
                    field = field.copy(label = label),
                    value = data.field(key),
                    readOnly = readOnly,
                    onImmediate = { v -> editor.editNow(editor.data.withField(key, v)) },
                    onDebounced = { v -> editor.editDebounced(editor.data.withField(key, v)) },
                )
            }
            if (!readOnly) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        enabled = hasNotes(notes) && !loading && !busy,
                        onClick = {
                            loading = true
                            scope.launch {
                                preview = ai.component(componentNotes(template, editor.data, pair.source))
                                loading = false
                            }
                        },
                        modifier = Modifier.height(48.dp),
                    ) { Text("Draft from remarks") }
                    if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (!hasNotes(notes)) {
                    Text("Nothing marked in section $sourceBadge yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    preview?.let { drafted ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(pair.component) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (drafted.fromMarks) DraftFallbackNotice()
                    DraftList("STRENGTHS", drafted.value.strengths)
                    DraftList("WEAKNESSES", drafted.value.weaknesses)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editor.editNow(editor.data.withDraft(pair, drafted.value))
                    preview = null
                }) { Text("Use draft") }
            },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
        )
    }
}
