// QA v2 editor: TMS header (linked institute, Link/Change/Unlink, Fill from TMS, data age) and the
// per-section "Fill from TMS" control. both need link + TMS login; without them the editor works as before.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import bd.sicip.qavisit.data.tms.TmsAuthState
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.prefilledValue
import bd.sicip.qavisit.domain.report.tmsFetchedAt
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.tmsTimeLabel
import bd.sicip.qavisit.domain.report.withoutTmsLink
import bd.sicip.qavisit.ui.common.LocalOpenTmsSettings
import kotlinx.coroutines.launch

@Composable
fun TmsReportBar(editor: ReportEditor, template: ReportTemplate) {
    val data = editor.data
    val link = data.tmsLink()
    val fetchedAt = data.tmsFetchedAt()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TMS: ${link?.name?.ifBlank { null } ?: "not linked"}",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (!editor.readOnly) {
                    TextButton(onClick = { editor.tmsFill.showPicker = true }) { Text(if (link == null) "Link" else "Change") }
                    if (link != null) TextButton(onClick = { editor.editNow(data.withoutTmsLink()) }) { Text("Unlink") }
                }
            }
            if (fetchedAt != null) {
                Text(
                    "TMS data from ${tmsTimeLabel(fetchedAt)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TmsFillControl(editor, template, origin = "header")
        }
    }

    if (editor.tmsFill.showPicker) {
        TmsPickerGate(editor, template)
    }
}

// picker needs a TMS login to read the catalog; otherwise point at Settings.
@Composable
private fun TmsPickerGate(editor: ReportEditor, template: ReportTemplate) {
    val auth = TmsServices.get(LocalContext.current).auth
    val authState by auth.state.collectAsState()
    val openSettings = LocalOpenTmsSettings.current
    val close = { editor.tmsFill.showPicker = false }
    if (authState is TmsAuthState.LoggedIn) {
        TmsInstitutePicker(
            association = template.prefilledValue(editor.data, "association"),
            instituteText = template.prefilledValue(editor.data, "institute"),
            onPick = { picked ->
                editor.linkTms(picked)
                close()
            },
            onDismiss = close,
        )
    } else {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = close,
            title = { Text("Log in to TMS") },
            text = { Text("Log in to TMS in Settings to pick an institute.") },
            confirmButton = { TextButton(onClick = { close(); openSettings() }) { Text("Open settings") } },
            dismissButton = { TextButton(onClick = close) { Text("Cancel") } },
        )
    }
}

// "Fill from TMS" button + what to do when it cannot run or fails. origin = which control shows failures.
@Composable
fun TmsFillControl(editor: ReportEditor, template: ReportTemplate, origin: String) {
    val services = TmsServices.get(LocalContext.current)
    val authState by services.auth.state.collectAsState()
    val openSettings = LocalOpenTmsSettings.current
    val scope = rememberCoroutineScope()
    val state = editor.tmsFill
    val linked = editor.data.tmsLink() != null
    val loggedIn = authState is TmsAuthState.LoggedIn
    if (editor.readOnly) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = { scope.launch { fillFromTms(editor, template, services, origin) } },
                enabled = linked && loggedIn && !state.busy,
                modifier = Modifier.height(44.dp),
            ) { Text("Fill from TMS") }
            if (state.busy && state.origin == origin) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        when {
            !linked -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Link a TMS institute first.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = { state.showPicker = true }) { Text("Link") }
            }
            !loggedIn -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Log in to TMS first.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f, fill = false))
                TextButton(onClick = openSettings) { Text("Open settings") }
            }
        }
        val failure = state.failure
        if (failure != null && state.origin == origin) {
            Text(failure.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row {
                if (failure.openSettings) TextButton(onClick = openSettings) { Text("Open settings") }
                if (failure.canUseCached) {
                    val savedAt = editor.data.tmsFetchedAt()?.let { tmsTimeLabel(it) } ?: ""
                    TextButton(onClick = { scope.launch { fillFromTms(editor, template, services, origin, useSaved = true) } }) {
                        Text("Use saved TMS data from $savedAt")
                    }
                }
            }
        }
    }
}
