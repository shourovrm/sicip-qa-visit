// under a QA v2 card field: "TMS: x" + Use when TMS differs, a small TMS marker when the value
// equals TMS, and the TMS present count next to tms_mismatch. editor only -- never printed.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.domain.report.BATCHES
import bd.sicip.qavisit.domain.report.tmsPresentToday
import bd.sicip.qavisit.domain.report.tmsSnapshot

@Composable
fun TmsFieldNote(editor: ReportEditor, cardsKey: String, cardIndex: Int, fieldKey: String) {
    val data = editor.data
    val hints = editor.tmsHints
    val suggestion = hints.suggestionFor(cardsKey, cardIndex, fieldKey)
    val noteStyle = MaterialTheme.typography.labelMedium
    val noteColor = MaterialTheme.colorScheme.onSurfaceVariant

    if (suggestion != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("TMS: ${suggestion.tmsValue}", style = noteStyle, color = noteColor)
            if (!editor.readOnly) {
                TextButton(onClick = { editor.editNow(data.withCardField(cardsKey, cardIndex, fieldKey, suggestion.tmsValue)) }) {
                    Text("Use")
                }
            }
        }
    } else if (hints.matchesTms(cardsKey, cardIndex, fieldKey)) {
        Text("TMS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(start = 4.dp))
    }

    if (cardsKey == BATCHES && fieldKey == "tms_mismatch") {
        val snapshot = data.tmsSnapshot() ?: return
        val present = tmsPresentToday(snapshot, data.cardField(cardsKey, cardIndex, "course"), data.cardField(cardsKey, cardIndex, "batch"))
        if (present != null) Text("TMS present on visit date: $present", style = noteStyle, color = noteColor)
    }
}
