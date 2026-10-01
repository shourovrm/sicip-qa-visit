// surprise v2 editors: a section's templated Remarks (built bullets + Edit), the Major findings
// picker (every written line ticked into one editable box) and the Recommendations "Draft from
// major findings" button. Rules live in domain/report/SectionRemarks.kt.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import bd.sicip.qavisit.domain.report.Field
import bd.sicip.qavisit.domain.report.RemarkLine
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.buildRemarkLines
import bd.sicip.qavisit.domain.report.findingsBox
import bd.sicip.qavisit.domain.report.reportLines
import bd.sicip.qavisit.domain.report.selectedFindingsText
import bd.sicip.qavisit.domain.report.tickedLines
import bd.sicip.qavisit.domain.report.withFindingsBox
import bd.sicip.qavisit.domain.report.withFindingsBoxOpened
import bd.sicip.qavisit.domain.report.withTicked
import bd.sicip.qavisit.domain.report.printedRemarkLines
import kotlinx.coroutines.launch

@Composable
private fun RemarkBullet(line: RemarkLine) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("•", color = if (line.neg) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

// manual remarks (K, L): one plain box the officer types into; each non-blank line prints as a bullet
@Composable
private fun ManualRemarksView(block: ReportBlock.Remarks, section: ReportSection, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    val text = data.remarksText(block.key)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.heading ?: "Remarks", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = text,
                onValueChange = { editor.editDebounced(editor.data.withRemarks(block.key, "", it)) },
                readOnly = readOnly,
                label = { Text("One point per line") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            ImproveWordingButton(
                text = text,
                label = section.title,
                readOnly = readOnly,
                onApply = { editor.editNow(editor.data.withRemarks(block.key, "", it)) },
            )
        }
    }
}

@Composable
fun RemarksBlockView(block: ReportBlock.Remarks, section: ReportSection, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    if (block.manual) {
        ManualRemarksView(block, section, data, readOnly, editor)
        return
    }
    val built = buildRemarkLines(template, section, block, data)
    val builtSource = built.joinToString("\n") { it.text }
    val lines = printedRemarkLines(template, section, block, data)
    val edited = data.remarksText(block.key)
    val stale = edited.isNotBlank() && data.remarksSource(block.key) != builtSource
    var editing by remember(block.key) { mutableStateOf(false) }
    var draft by remember(block.key) { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.heading ?: "Remarks", style = MaterialTheme.typography.titleSmall)
            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("One point per line") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                ImproveWordingButton(text = draft, label = section.title, readOnly = readOnly, onApply = { draft = it })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (edited.isNotBlank()) {
                        TextButton(onClick = { editor.editNow(editor.data.withRemarksCleared(block.key)); editing = false }) { Text("Reset") }
                    }
                    TextButton(onClick = { editing = false }) { Text("Cancel") }
                    Button(onClick = {
                        editor.editNow(editor.data.withRemarks(block.key, builtSource, draft.trim()))
                        editing = false
                    }) { Text("Save") }
                }
            } else {
                if (lines.isEmpty()) {
                    Text(
                        "Nothing yet. Answer the items above and their sentences appear here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    lines.forEach { RemarkBullet(it) }
                    if (!readOnly) {
                        TextButton(onClick = { draft = lines.joinToString("\n") { it.text }; editing = true }) { Text("Edit") }
                    }
                }
                if (stale) {
                    Text(
                        "Answers changed after your edit, so the remarks were rebuilt. Edit to write them again.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

// N Major findings (spec 2026-10-02 item 6): every written line of the report as a checklist in
// report order (unticking never moves or hides a line), "Add selected to Major findings" writes
// the ticked lines into ONE editable box (one finding per line), which is what prints.
@Composable
fun FindingsBlockView(block: ReportBlock.Findings, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    val lines = reportLines(template, data)
    val ticked = tickedLines(block, data, lines).toSet()
    val box = data.findingsBox(block).orEmpty()
    var confirmReplace by remember { mutableStateOf(false) }

    // an old picked list becomes the box text once, the first time this report shows N
    LaunchedEffect(Unit) {
        if (!readOnly) {
            val opened = withFindingsBoxOpened(block, editor.data)
            if (opened != editor.data) editor.editNow(opened)
        }
    }

    fun addSelected() {
        editor.editNow(editor.data.withFindingsBox(block, selectedFindingsText(block, editor.data, lines)))
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(block.heading ?: "Major findings", style = MaterialTheme.typography.titleSmall)
        block.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (lines.isEmpty()) {
            Text("Nothing written yet. Answer the sections above and their points appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Points from this report", style = MaterialTheme.typography.labelLarge)
            if (!readOnly) {
                Row {
                    TextButton(onClick = { editor.editNow(withTicked(block, editor.data, lines.map { it.text })) }) { Text("Select all") }
                    TextButton(onClick = { editor.editNow(withTicked(block, editor.data, emptyList())) }) { Text("Deselect all") }
                }
            }
            lines.forEach { line ->
                val checked = line.text in ticked
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = checked,
                        enabled = !readOnly,
                        onCheckedChange = { tick ->
                            val now = tickedLines(block, editor.data, lines)
                            val next = if (tick) now + line.text else now - line.text
                            editor.editNow(withTicked(block, editor.data, next))
                        },
                    )
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        if (line.neg) Text("ISSUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (!readOnly) {
                Button(
                    enabled = ticked.isNotEmpty(),
                    onClick = { if (box.isBlank()) addSelected() else confirmReplace = true },
                    modifier = Modifier.height(48.dp),
                ) { Text("Add selected to Major findings") }
            }
        }
        OutlinedTextField(
            value = box,
            onValueChange = { editor.editDebounced(editor.data.withFindingsBox(block, it)) },
            readOnly = readOnly,
            label = { Text(block.heading ?: "Major findings") },
            placeholder = { Text("One finding per line") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        ImproveWordingButton(
            text = box,
            label = block.heading ?: "Major findings",
            readOnly = readOnly,
            onApply = { editor.editNow(editor.data.withFindingsBox(block, it)) },
        )
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Replace Major findings?") },
            text = { Text("The box already has text. Replace it with the selected points?") },
            confirmButton = { TextButton(onClick = { addSelected(); confirmReplace = false }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancel") } },
        )
    }
}

// Recommendations, one "The institute should ..." per source line: surprise v2 drafts from the
// Major findings box lines, QA v2 from the s13 weaknesses. A filled box is only replaced after a
// preview.
@Composable
fun RecommendationsDraftButton(
    field: Field,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    label: String = "Draft from major findings",
    emptyHint: String = "Add points to Major findings above first",
    sourcesOf: (ReportData) -> List<String>,
) {
    if (readOnly) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { QaDraftAi(context) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Drafted<String>?>(null) }
    val undo = rememberDraftUndo<String>()
    val sources = sourcesOf(data)

    DraftButtonRow(
        label = label,
        enabled = sources.isNotEmpty(),
        loading = loading,
        hint = if (sources.isEmpty()) emptyHint else null,
    ) {
        loading = true
        notice = false
        scope.launch {
            val result = ai.recommendations(sourcesOf(editor.data))
            val text = result.value.joinToString("\n")
            loading = false
            if (editor.data.field(field.key).isBlank()) {
                editor.editNow(editor.data.withField(field.key, text))
                notice = result.fromMarks
            } else {
                preview = Drafted(text, result.fromMarks)
            }
        }
    }
    if (notice) DraftFallbackNotice()
    UseMyWordsButton(undo, data.field(field.key)) { editor.editNow(editor.data.withField(field.key, it)) }
    preview?.let { drafted ->
        ReplaceTextDialog("Recommendations", drafted.value, drafted.fromMarks, onUse = {
            undo.record(editor.data.field(field.key), drafted.value)
            editor.editNow(editor.data.withField(field.key, drafted.value))
            preview = null
        }, onDismiss = { preview = null })
    }
}
