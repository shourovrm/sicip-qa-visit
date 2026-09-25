// QA conclusions draft buttons (spec 2026-09-26 §4): s14 findings "Draft from weaknesses", s15
// recommendations prefill + "Fill from improvement plan", s16 plan "Draft from weaknesses".
// Non-empty text is never overwritten without a preview/confirm.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.allWeaknesses
import bd.sicip.qavisit.domain.report.recommendationsFromPlan
import kotlinx.coroutines.launch

@Composable
fun DraftFallbackNotice() {
    Text(DRAFT_FALLBACK_NOTICE, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun DraftList(heading: String, lines: List<String>) {
    Text(heading, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(
        if (lines.isEmpty()) "—" else lines.joinToString("\n") { "• $it" },
        style = MaterialTheme.typography.bodyMedium,
    )
}

// "13" -- badge of the section holding the strengths/weaknesses pairs, for the disabled hint
private fun weaknessesSectionBadge(template: ReportTemplate): String =
    template.sections.firstOrNull { section -> section.blocks.any { it is ReportBlock.Fields && it.pairs.isNotEmpty() } }?.badge ?: ""

@Composable
private fun DraftButtonRow(label: String, enabled: Boolean, loading: Boolean, hint: String?, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(enabled = enabled && !loading, onClick = onClick, modifier = Modifier.height(48.dp)) { Text(label) }
            if (loading) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        hint?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// replace-this-text confirm: shows the draft, "Use draft" writes it
@Composable
private fun ReplaceTextDialog(title: String, draft: String, fromMarks: Boolean, onUse: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (fromMarks) DraftFallbackNotice()
                Text("Replaces what is in the box now.", style = MaterialTheme.typography.labelMedium)
                Text(draft, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onUse) { Text("Use draft") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Keep mine") } },
    )
}

// s14: findings drafted from every s13 weakness line
@Composable
fun FindingsDraftButton(field: Field, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    if (readOnly) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { QaDraftAi(context) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Drafted<String>?>(null) }
    val weaknesses = allWeaknesses(template, data)

    DraftButtonRow(
        label = "Draft from weaknesses",
        enabled = weaknesses.isNotEmpty(),
        loading = loading,
        hint = if (weaknesses.isEmpty()) "Add weaknesses in section ${weaknessesSectionBadge(template)} first" else null,
    ) {
        loading = true
        notice = false
        scope.launch {
            val result = ai.findings(allWeaknesses(template, editor.data))
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
    preview?.let { drafted ->
        ReplaceTextDialog("Major findings", drafted.value, drafted.fromMarks, onUse = {
            editor.editNow(editor.data.withField(field.key, drafted.value))
            notice = drafted.fromMarks
            preview = null
        }, onDismiss = { preview = null })
    }
}

// s15: field.draftFrom names the plan cards block ("plan"). Blank box on open -> prefilled
// (a normal edit); the button refills on demand, confirming before replacing typed text.
@Composable
fun RecommendationsFromPlan(field: Field, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    if (readOnly) return
    val planKey = field.draftFrom ?: return
    var confirm by remember { mutableStateOf(false) }
    val fromPlan = recommendationsFromPlan(data.cards(planKey))

    LaunchedEffect(field.key) {
        val current = editor.data
        val prefill = recommendationsFromPlan(current.cards(planKey))
        if (current.field(field.key).isBlank() && prefill.isNotEmpty()) editor.editNow(current.withField(field.key, prefill))
    }

    DraftButtonRow(
        label = "Fill from improvement plan",
        enabled = fromPlan.isNotEmpty(),
        loading = false,
        hint = if (fromPlan.isEmpty()) "Add actions in the improvement plan first" else null,
    ) {
        if (editor.data.field(field.key).isBlank()) editor.editNow(editor.data.withField(field.key, fromPlan)) else confirm = true
    }
    if (confirm) {
        ReplaceTextDialog("Recommendations", fromPlan, fromMarks = false, onUse = {
            editor.editNow(editor.data.withField(field.key, fromPlan))
            confirm = false
        }, onDismiss = { confirm = false })
    }
}

// s16: plan cards rebuilt from the s13 weaknesses; existing cards -> confirm first
@Composable
fun PlanDraftButton(block: ReportBlock.Cards, template: ReportTemplate, data: ReportData, readOnly: Boolean, editor: ReportEditor) {
    if (readOnly) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ai = remember { QaDraftAi(context) }
    var loading by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    val weaknesses = allWeaknesses(template, data)

    fun rebuild() {
        loading = true
        notice = false
        scope.launch {
            val result = ai.plan(allWeaknesses(template, editor.data), editor.data.cards(block.key))
            editor.editNow(editor.data.withCardsReplaced(block.key, result.value))
            notice = result.fromMarks
            loading = false
        }
    }

    DraftButtonRow(
        label = "Draft from weaknesses",
        enabled = weaknesses.isNotEmpty(),
        loading = loading,
        hint = if (weaknesses.isEmpty()) "Add weaknesses in section ${weaknessesSectionBadge(template)} first" else null,
    ) {
        if (data.cards(block.key).isEmpty()) rebuild() else confirm = true
    }
    if (notice) DraftFallbackNotice()
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Rebuild plan from ${weaknesses.size} weaknesses?") },
            text = { Text("Responsible and timeline are kept for matching weaknesses.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    rebuild()
                }) { Text("Rebuild") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}
