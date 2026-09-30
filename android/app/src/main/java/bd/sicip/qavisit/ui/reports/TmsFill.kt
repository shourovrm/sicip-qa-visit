// "Fill from TMS": fetch a snapshot, cache it in the report, prefill empty card fields.
// state lives on the report's editor so every screen of the report shows the same progress.
package bd.sicip.qavisit.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.fetchTmsSnapshot
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.TmsFillFailure
import bd.sicip.qavisit.domain.report.prefillFromTms
import bd.sicip.qavisit.domain.report.tmsFillFailure
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.tmsSnapshot
import bd.sicip.qavisit.domain.report.visitDate
import bd.sicip.qavisit.domain.report.withTmsSnapshot
import kotlinx.coroutines.CancellationException
import java.time.Instant

class TmsFillState {
    var busy by mutableStateOf(false)
    var showPicker by mutableStateOf(false)

    // failure is shown only by the control that started the fetch (origin), not by all of them
    var failure by mutableStateOf<TmsFillFailure?>(null)
    var origin by mutableStateOf("")
}

// useSaved = skip the network and prefill from the snapshot already in the report.
suspend fun fillFromTms(
    editor: ReportEditor,
    template: ReportTemplate,
    services: TmsServices,
    origin: String,
    useSaved: Boolean = false,
) {
    val state = editor.tmsFill
    if (state.busy) return
    state.busy = true
    state.origin = origin
    state.failure = null
    try {
        val snapshot = if (useSaved) {
            editor.data.tmsSnapshot() ?: return
        } else {
            val link = editor.data.tmsLink() ?: return
            val fetched = fetchTmsSnapshot(services.api, link, template.visitDate(editor.data), Instant.now().toString())
            editor.editNow(editor.data.withTmsSnapshot(fetched))
            fetched
        }
        editor.editNow(prefillFromTms(snapshot, editor.data).data)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        state.failure = tmsFillFailure(e, hasCachedSnapshot = editor.data.tmsSnapshot() != null)
    } finally {
        state.busy = false
    }
}
