// one report's suggestion data, in memory only (never written to the report or logged): the
// linked TMS institute's courses/batches, its trainers, trainee names per batch (fetched when a
// trainee field needs them) and the shared equipment list. also shares new equipment names after a save.
// lives in ReportEditorRegistry per report id, so a fresher editor for the same report keeps it.
package bd.sicip.qavisit.ui.reports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import bd.sicip.qavisit.data.suggest.SharedSuggestions
import bd.sicip.qavisit.data.tms.TmsApi
import bd.sicip.qavisit.data.tms.TmsBatchRef
import bd.sicip.qavisit.data.tms.TmsLink
import bd.sicip.qavisit.data.tms.fetchTmsCourseCatalog
import bd.sicip.qavisit.data.tms.fetchTmsTrainees
import bd.sicip.qavisit.data.tms.fetchTmsTrainers
import bd.sicip.qavisit.domain.report.LabCourse
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.SuggestionSources
import bd.sicip.qavisit.domain.report.sharedSuggestionValues
import bd.sicip.qavisit.domain.report.suggestionKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val EQUIPMENT_LIST = "equipment"
private const val SHARE_DEBOUNCE_MS = 5_000L

class ReportSuggestions(
    private val shared: SharedSuggestions?,
    private val tmsApi: TmsApi?,
    labCourses: List<LabCourse> = emptyList(),
) {
    var sources by mutableStateOf(SuggestionSources(labCourses = labCourses))
        private set

    // for the hub's TMS status row: a catalog fetch is running / the last one failed
    var loading by mutableStateOf(false)
        private set
    var loadFailed by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var catalogInstituteId: Long? = null
    private var equipmentLoaded = false
    private val traineeBatchesRequested = mutableSetOf<Long>()
    private val sharedEquipmentKeys = mutableSetOf<String>()
    private var shareJob: Job? = null
    private val loadLock = Mutex() // hub open and the creation link step may both ask at once

    // editor open: fetch whatever is still missing. not signed in / offline -> stays empty.
    suspend fun load(link: TmsLink?) = loadLock.withLock {
        if (!equipmentLoaded && shared != null) {
            val equipment = shared.fetch(EQUIPMENT_LIST)
            equipmentLoaded = equipment.isNotEmpty()
            sources = sources.copy(equipment = equipment)
        }
        if (link == null || tmsApi == null || catalogInstituteId == link.instituteId) return@withLock
        loading = true
        try {
            loadFailed = !quietly {
                val catalog = fetchTmsCourseCatalog(tmsApi, link)
                catalogInstituteId = link.instituteId
                sources = sources.copy(catalog = catalog, traineesByBatch = emptyMap())
                traineeBatchesRequested.clear()
            }
            // trainers are optional on top of the catalog: a failure leaves the list empty
            if (!loadFailed) quietly { sources = sources.copy(trainers = fetchTmsTrainers(tmsApi, link)) }
        } finally {
            loading = false
        }
    }

    // "Refresh": forget the cached catalog + trainee lists so the next load fetches them again
    fun forgetTmsData() {
        catalogInstituteId = null
        sources = sources.copy(catalog = null, traineesByBatch = emptyMap(), trainers = emptyList())
        traineeBatchesRequested.clear()
    }

    // a trainee field resolved to this batch: fetch its names once
    suspend fun loadTrainees(link: TmsLink?, batch: TmsBatchRef) {
        if (link == null || tmsApi == null || !traineeBatchesRequested.add(batch.id)) return
        quietly {
            val trainees = fetchTmsTrainees(tmsApi, link, batch)
            sources = sources.copy(traineesByBatch = sources.traineesByBatch + (batch.id to trainees))
        }
        if (batch.id !in sources.traineesByBatch) traineeBatchesRequested.remove(batch.id) // failed: retry later
    }

    // names the report already held when opened were shared by an earlier save (or the
    // migration backfill): only names added from now on count as new
    fun rememberAlreadyShared(template: ReportTemplate, data: ReportData) {
        sharedEquipmentKeys += sharedSuggestionValues(template, data, EQUIPMENT_LIST).map(::suggestionKey)
    }

    // after a local save: debounced, fire-and-forget; only names this report has not shared yet,
    // so re-saving the same report does not inflate the server's use counts
    fun afterSave(template: ReportTemplate, data: ReportData) {
        if (shared == null) return
        shareJob?.cancel()
        shareJob = scope.launch {
            delay(SHARE_DEBOUNCE_MS)
            val fresh = sharedSuggestionValues(template, data, EQUIPMENT_LIST).filter { suggestionKey(it) !in sharedEquipmentKeys }
            if (fresh.isNotEmpty() && shared.add(EQUIPMENT_LIST, fresh)) {
                sharedEquipmentKeys += fresh.map(::suggestionKey)
            }
        }
    }

    // false when the block failed (suggestions are optional, so nothing is thrown)
    private suspend fun quietly(block: suspend () -> Unit): Boolean {
        try {
            block()
            return true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // suggestions are optional: any TMS failure just leaves the lists as they were
            return false
        }
    }
}
