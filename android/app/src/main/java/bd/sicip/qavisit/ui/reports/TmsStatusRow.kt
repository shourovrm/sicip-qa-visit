// report hub: always-visible TMS link status for surprise reports. existing reports (made before the
// link step, or on another phone) can be linked here; linking or refreshing reloads the suggestion
// lists. section A courses are filled only while empty, so typed courses are never overwritten.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.isSignedIn
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.prefilledValue
import bd.sicip.qavisit.domain.report.runningBatches
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.visitDate
import bd.sicip.qavisit.domain.report.withRunningCoursesIfEmpty
import bd.sicip.qavisit.domain.report.withTmsLink
import bd.sicip.qavisit.ui.common.LocalOpenTmsSettings
import kotlinx.coroutines.launch

@Composable
fun rememberTmsSignedIn(): Boolean {
    val auth = TmsServices.get(LocalContext.current).auth
    val authState by auth.state.collectAsState()
    return authState.isSignedIn
}

@Composable
fun TmsStatusRow(editor: ReportEditor, template: ReportTemplate) {
    val signedIn = rememberTmsSignedIn()
    val openProfile = LocalOpenTmsSettings.current
    val scope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }
    val link = editor.data.tmsLink()
    val suggestions = editor.suggestions

    fun reloadSuggestions(forLink: TmsLink) {
        scope.launch {
            suggestions.forgetTmsData()
            suggestions.load(forLink)
            val catalog = suggestions.sources.catalog ?: return@launch
            val running = runningBatches(catalog, template.visitDate(editor.data))
            if (running.isNotEmpty()) editor.editNow(withRunningCoursesIfEmpty(editor.data, running))
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (link != null) "Linked to ${link.name.ifBlank { "TMS institute" }}" else "Not linked to TMS",
                style = MaterialTheme.typography.titleSmall,
            )
            val detail = when {
                suggestions.loading -> "Loading TMS courses and batches..."
                link != null && signedIn && suggestions.loadFailed -> "Could not load TMS data. Tap Refresh to try again."
                link != null && signedIn -> "Course, batch and trainee suggestions come from TMS."
                link != null -> "Sign in to TMS in Profile to get suggestions."
                signedIn -> "Link the institute to get course, batch and trainee suggestions."
                else -> "Sign in to TMS in Profile, then link the institute."
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!editor.readOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!signedIn) TextButton(onClick = openProfile) { Text("Open Profile") }
                    if (signedIn && link == null) TextButton(onClick = { pickerOpen = true }) { Text("Link institute") }
                    if (signedIn && link != null) {
                        TextButton(onClick = { pickerOpen = true }) { Text("Change") }
                        TextButton(onClick = { reloadSuggestions(link) }, enabled = !suggestions.loading) { Text("Refresh") }
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        TmsInstitutePicker(
            association = template.prefilledValue(editor.data, "association"),
            instituteText = template.prefilledValue(editor.data, "institute"),
            onPick = { picked ->
                pickerOpen = false
                editor.editNow(editor.data.withTmsLink(picked))
                reloadSuggestions(picked)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}
