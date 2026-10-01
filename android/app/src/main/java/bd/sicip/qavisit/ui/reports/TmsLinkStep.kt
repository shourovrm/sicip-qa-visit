// surprise report creation: link the TMS institute once (partner from the visit association,
// institute matched by name, same picker as QA v2), then section A gets one course card per
// batch running on the visit date. skippable; only offered while signed in to TMS.
package bd.sicip.qavisit.ui.reports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import bd.sicip.qavisit.data.tms.isSignedIn
import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.hasSuggestFields
import bd.sicip.qavisit.domain.report.prefilledValue
import bd.sicip.qavisit.domain.report.runningBatches
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.visitDate
import bd.sicip.qavisit.domain.report.withRunningCourses
import kotlinx.coroutines.launch

// reports this app run just created and not yet offered the link step (memory only: a report
// opened later, or on another phone, goes straight to the hub)
object NewReportLinkStep {
    private val pending = mutableSetOf<String>()

    fun markNew(reportId: String) = synchronized(pending) { pending.add(reportId) }

    fun take(reportId: String): Boolean = synchronized(pending) { pending.remove(reportId) }
}

private sealed interface StepState {
    data object Picking : StepState
    data object Loading : StepState
    data class Message(val text: String) : StepState
    data object Done : StepState
}

@Composable
fun SurpriseTmsLinkStep(editor: ReportEditor, template: ReportTemplate) {
    val services = TmsServices.get(LocalContext.current)
    val auth = services.auth
    val authState by auth.state.collectAsState()
    val signedIn = authState.isSignedIn
    val offered = remember(editor.report.id) { NewReportLinkStep.take(editor.report.id) }
    var state by remember(editor.report.id) { mutableStateOf<StepState>(StepState.Picking) }
    val scope = rememberCoroutineScope()

    val applies = offered && signedIn && !editor.readOnly && template.hasSuggestFields() && editor.data.tmsLink() == null
    if (!applies && state == StepState.Picking) return

    fun linkAndAddCourses(link: TmsLink) {
        editor.linkTms(link)
        state = StepState.Loading
        scope.launch {
            editor.suggestions.load(link)
            val catalog = editor.suggestions.sources.catalog
            state = if (catalog == null) {
                StepState.Message("Linked, but TMS courses could not be loaded. Add the courses in section A by hand.")
            } else {
                val running = runningBatches(catalog, template.visitDate(editor.data))
                editor.editNow(withRunningCourses(editor.data, running))
                if (running.isEmpty()) StepState.Message("Linked. TMS shows no batch running on the visit date.") else StepState.Done
            }
            // C attendance (enrolment, TMS 7-day mean) in the background; the hub row shows progress
            if (catalog != null) editor.launchForReport { fillFromTms(editor, template, services, origin = "hub") }
        }
    }

    when (val current = state) {
        StepState.Picking -> TmsInstitutePicker(
            association = template.prefilledValue(editor.data, "association"),
            instituteText = template.prefilledValue(editor.data, "institute"),
            onPick = ::linkAndAddCourses,
            onDismiss = { state = StepState.Done },
        )
        StepState.Loading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("TMS") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Adding the batches running on the visit date...")
                }
            },
            confirmButton = {},
        )
        is StepState.Message -> AlertDialog(
            onDismissRequest = { state = StepState.Done },
            title = { Text("TMS") },
            text = { Text(current.text) },
            confirmButton = { TextButton(onClick = { state = StepState.Done }) { Text("OK") } },
        )
        StepState.Done -> Unit
    }
}
