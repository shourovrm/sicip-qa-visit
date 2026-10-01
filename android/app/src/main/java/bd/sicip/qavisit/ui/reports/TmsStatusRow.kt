// report hub: always-visible TMS link status (surprise + QA). existing reports (made before the
// link step, or on another phone) can be linked here; linking or refreshing reloads the suggestion
// lists and fills from TMS (surprise C attendance, QA 1.40-1.60). section A courses are filled only
// while empty, so typed courses are never overwritten.
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
import bd.sicip.qavisit.domain.report.hasSuggestFields
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
    val services = TmsServices.get(LocalContext.current)
    val scope = rememberCoroutineScope()
    var pickerOpen by remember { mutableStateOf(false) }
    val link = editor.data.tmsLink()
    val suggestions = editor.suggestions
    val fill = editor.tmsFill
    val isQa = template.id == "qa"

    // refresh = the officer asked for fresh TMS data: TMS numbers may overwrite earlier ones
    fun reloadTmsData(forLink: TmsLink, refresh: Boolean) {
        scope.launch {
            if (template.hasSuggestFields()) {
                suggestions.forgetTmsData()
                suggestions.load(forLink)
                val catalog = suggestions.sources.catalog
                if (catalog != null && !isQa) {
                    val running = runningBatches(catalog, template.visitDate(editor.data))
                    if (running.isNotEmpty()) editor.editNow(withRunningCoursesIfEmpty(editor.data, running))
                }
            }
            editor.launchForReport {
                fillFromTms(editor, template, services, origin = "hub", prefill = tmsPrefillFor(template, refresh))
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (link != null) "Linked to ${link.name.ifBlank { "TMS institute" }}" else "Not linked to TMS",
                style = MaterialTheme.typography.titleSmall,
            )
            val failure = fill.failure?.takeIf { fill.origin == "hub" }
            val detail = when {
                suggestions.loading -> "Loading TMS courses and batches..."
                fill.busy -> "Loading TMS enrolment and attendance..."
                link != null && signedIn && (suggestions.loadFailed || failure != null) -> "Could not load TMS data. Tap Refresh to try again."
                link != null && signedIn && isQa -> "Course tables 1.40 to 1.60 fill from TMS. Refresh to update them."
                link != null && signedIn -> "Course, batch, trainee and attendance data come from TMS."
                link != null -> "Sign in to TMS in Profile to get TMS data."
                signedIn -> "Link the institute to fill and suggest data from TMS."
                else -> "Sign in to TMS in Profile, then link the institute."
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!editor.readOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!signedIn) TextButton(onClick = openProfile) { Text("Open Profile") }
                    if (signedIn && link == null) TextButton(onClick = { pickerOpen = true }) { Text("Link institute") }
                    if (signedIn && link != null) {
                        TextButton(onClick = { pickerOpen = true }) { Text("Change") }
                        TextButton(onClick = { reloadTmsData(link, refresh = true) }, enabled = !suggestions.loading && !fill.busy) { Text("Refresh") }
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
                editor.linkTms(picked)
                reloadTmsData(picked, refresh = false)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}
