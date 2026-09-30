// qa-v2 evidence for one criterion: numbered chips ("7.1b.a – Trainers list", × removes it from
// this criterion only) and a box that lists matching suggestions while typing -- this report's
// evidence, the app's own tables and the Word tables (domain/report/Evidence.kt). Add or a tapped
// suggestion goes through withEvidenceAdded, so a reused name keeps its first number.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.evidenceLabel
import bd.sicip.qavisit.domain.report.evidenceSuggestions
import bd.sicip.qavisit.domain.report.itemEvidence
import bd.sicip.qavisit.domain.report.withEvidenceAdded
import bd.sicip.qavisit.domain.report.withEvidenceRemoved

// more than this many matches is noise on a phone screen; typing narrows it
private const val MAX_SUGGESTIONS = 6

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EvidencePicker(
    template: ReportTemplate,
    data: ReportData,
    itemId: String,
    path: String,
    readOnly: Boolean,
    editor: ReportEditor,
) {
    var typed by remember(itemId) { mutableStateOf("") }
    val entries = itemEvidence(data, itemId)

    fun add(name: String) {
        editor.editNow(withEvidenceAdded(editor.data, path, itemId, name))
        typed = ""
    }

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Evidence seen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (entries.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                entries.forEach { entry ->
                    InputChip(
                        selected = false,
                        onClick = { if (!readOnly) editor.editNow(withEvidenceRemoved(editor.data, itemId, entry.id)) },
                        label = { Text(evidenceLabel(entry)) },
                        trailingIcon = if (readOnly) null else ({ Icon(Icons.Filled.Close, contentDescription = "Remove ${entry.name}") }),
                    )
                }
            }
        }
        if (!readOnly) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text("Add evidence (document, register, photo)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { add(typed) }, enabled = typed.isNotBlank()) { Text("Add") }
            }
            val suggestions = evidenceSuggestions(template, data, itemId, typed).take(MAX_SUGGESTIONS)
            suggestions.forEach { name ->
                Text(
                    "+ $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { add(name) }.padding(vertical = 6.dp),
                )
            }
        }
    }
}
