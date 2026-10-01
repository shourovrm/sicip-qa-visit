// surprise v2 editors: a section's templated Remarks (built bullets + Edit), the Major findings
// picker (every remarks line offered, AI pre-selects the major ones once) and the
// Recommendations "Draft from major findings" button. Rules live in domain/report/SectionRemarks.kt.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import bd.sicip.qavisit.domain.report.Finding
import bd.sicip.qavisit.domain.report.RemarkLine
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportSection
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.buildRemarkLines
import bd.sicip.qavisit.domain.report.findingCandidates
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

// one picked finding: tick to drop it, its own text box + Improve wording
@Composable
private fun PickedFindingCard(finding: Finding, index: Int, readOnly: Boolean, editor: ReportEditor) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Checkbox(
                    checked = true,
                    enabled = !readOnly,
                    onCheckedChange = { editor.editNow(editor.data.withFindings(editor.data.findings().filterIndexed { i, _ -> i != index })) },
                )
                OutlinedTextField(
                    value = finding.text,
                    onValueChange = { text -> editor.editDebounced(editor.data.withFindings(editor.data.findings().mapIndexed { i, f -> if (i == index) f.copy(text = text) else f })) },
                    readOnly = readOnly,
                    modifier = Modifier.weight(1f),
                )
            }
            ImproveWordingButton(
                text = finding.text,
                label = "Major findings",
                readOnly = readOnly,
                onApply = { text -> editor.editNow(editor.data.withFindings(editor.data.findings().mapIndexed { i, f -> if (i == index) f.copy(text = text) else f })) },
                modifier = Modifier.padding(start = 44.dp),
            )
        }
    }
}

@Composable
fun FindingsBlockView(block: ReportBlock.Findings, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { QaDraftAi(context) }
    val candidates = findingCandidates(template, data)
    val candidatesSource = candidates.joinToString("\n") { it.text }
    val picks = data.findings()
    var loading by remember { mutableStateOf(false) }
    var fromMarks by remember { mutableStateOf(false) }
    val undo = rememberDraftUndo<List<Finding>>()

    // AI suggestions replace the picks that came from the list; typed findings stay
    fun suggest() {
        loading = true
        scope.launch {
            val result = ai.majorFindings(candidates)
            val current = editor.data.findings()
            val kept = result.value.map { suggested -> current.find { it.src == suggested.src } ?: suggested }
            val typed = current.filter { it.src.isEmpty() }
            // the automatic first pre-select replaces nothing, so only a re-suggest is undoable
            if (current.isNotEmpty()) undo.record(current, kept + typed)
            editor.editNow(editor.data.withFindings(kept + typed).withFindingsAiSource(candidatesSource))
            fromMarks = result.fromMarks
            loading = false
        }
    }

    // pre-select once per candidate list, only while nothing is picked yet
    LaunchedEffect(candidatesSource) {
        if (!readOnly && picks.isEmpty() && candidates.isNotEmpty() && data.findingsAiSource() != candidatesSource) suggest()
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(block.heading ?: "Major findings", style = MaterialTheme.typography.titleSmall)
        block.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Choosing the major findings…", style = MaterialTheme.typography.labelMedium)
            }
        }
        if (fromMarks) Text("AI unavailable — every issue was selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (picks.isEmpty() && !loading) {
            Text("No finding selected yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        picks.forEachIndexed { index, finding -> PickedFindingCard(finding, index, readOnly, editor) }
        if (!readOnly) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editor.editNow(editor.data.withFindings(editor.data.findings() + Finding("", ""))) }, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add finding")
                }
                OutlinedButton(enabled = candidates.isNotEmpty() && !loading, onClick = { suggest() }, modifier = Modifier.height(48.dp)) {
                    Text("Suggest again")
                }
            }
            UseMyWordsButton(undo, picks) { editor.editNow(editor.data.withFindings(it)) }
        }

        val pickedSources = picks.map { it.src }.toSet()
        val others = candidates.filter { it.text !in pickedSources }
        if (others.isNotEmpty()) {
            Text("Other points from this report", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            others.forEach { line ->
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(
                        checked = false,
                        enabled = !readOnly,
                        onCheckedChange = { editor.editNow(editor.data.withFindings(editor.data.findings() + Finding(line.text, line.text))) },
                    )
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        if (line.neg) Text("ISSUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        Text(line.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

// Recommendations, one "The institute should ..." per source line: surprise v2 drafts from the
// picked major findings, QA v2 from the s13 weaknesses. A filled box is only replaced after a
// preview.
@Composable
fun RecommendationsDraftButton(
    field: Field,
    data: ReportData,
    readOnly: Boolean,
    editor: ReportEditor,
    label: String = "Draft from major findings",
    emptyHint: String = "Select major findings above first",
    sourcesOf: (ReportData) -> List<String> = { d -> d.findings().map { it.text }.filter { it.isNotBlank() } },
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
