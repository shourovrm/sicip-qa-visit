// "Fill from TMS": fetch a snapshot, cache it in the report, prefill empty card fields
// (QA 1.40-1.60, surprise C attendance).
// state lives on the report's editor so every screen of the report shows the same progress.
package bd.sicip.qavisit.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.TmsServices
import bd.sicip.qavisit.data.tms.TmsSnapshot
import bd.sicip.qavisit.data.tms.fetchTmsSnapshot
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.TmsFillFailure
import bd.sicip.qavisit.domain.report.prefillFromTms
import bd.sicip.qavisit.domain.report.prefillSurpriseFromTms
import bd.sicip.qavisit.domain.report.tmsFillFailure
import bd.sicip.qavisit.domain.report.tmsLink
import bd.sicip.qavisit.domain.report.tmsSnapshot
import bd.sicip.qavisit.domain.report.visitDate
import bd.sicip.qavisit.domain.report.withAddressIfEmpty
import bd.sicip.qavisit.domain.report.withTmsLink
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

// link the report to a TMS institute; its address goes into an empty "Address and contact"
fun ReportEditor.linkTms(link: TmsLink) {
    editNow(withAddressIfEmpty(data.withTmsLink(link), link.address))
}

// QA: 1.40/1.50/1.60 cards; surprise: C attendance. empty fields only, always
fun tmsPrefillFor(template: ReportTemplate): (TmsSnapshot, ReportData) -> ReportData =
    if (template.id == "qa") {
        { snapshot, data -> prefillFromTms(snapshot, data).data }
    } else {
        { snapshot, data -> prefillSurpriseFromTms(snapshot, data).data }
    }

// links saved before addresses were kept: look the institute up once
private suspend fun withLookedUpAddress(editor: ReportEditor, services: TmsServices, link: TmsLink): TmsLink {
    if (link.address.isNotBlank()) return link
    // the address is a nice-to-have: a failed lookup must not stop the fill
    val address = try {
        services.catalog.institutes(link.entityId, link.trancheId).firstOrNull { it.id == link.instituteId }?.address.orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ""
    }
    if (address.isBlank()) return link
    val withAddress = link.copy(address = address)
    editor.linkTms(withAddress)
    return withAddress
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
            val link = withLookedUpAddress(editor, services, editor.data.tmsLink() ?: return)
            val fetched = fetchTmsSnapshot(services.api, link, template.visitDate(editor.data), Instant.now().toString())
            editor.editNow(withAddressIfEmpty(editor.data.withTmsSnapshot(fetched), link.address))
            fetched
        }
        editor.editNow(tmsPrefillFor(template)(snapshot, editor.data))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        state.failure = tmsFillFailure(e, hasCachedSnapshot = editor.data.tmsSnapshot() != null)
    } finally {
        state.busy = false
    }
}
