// autosave engine for one open report -- there is never a Save button (spec): every edit lands
// in Room on its own. Local Compose state (`data`) updates on every keystroke so a text field
// never lags; the Room write is immediate for discrete edits (checklist tap, choice field, card
// add/remove, flag toggle) and debounced ~400ms for free-typed text (text/longtext/number/date/
// time/phone fields, checklist remarks, card text fields) so a stream of keystrokes doesn't
// hammer the DB with one write per character.
//
// ONE ReportEditor per open report, shared by every screen that shows it (hub, every section,
// review) via ReportEditorRegistry below -- not one-per-screen. A report is edited across many
// screens in one sitting (hop between sections, land on Review, tap back into a section to fix
// something); if each screen built its own editor from whatever Room happened to hold at that
// screen's first composition, a still-in-flight debounced write from the screen just left could
// lose a race against a fresh editor's first save on the new screen, silently dropping whichever
// edit lost -- there would be two different in-memory copies of `data` with no way to reconcile
// them except last-write-wins on the WHOLE json blob. Sharing one editor makes that impossible:
// there is only ever one in-memory `data`, so "the latest edit" is unambiguous everywhere.
//
// the save coroutine runs on its own IO scope, NOT one tied to any screen's composition (same
// trick HomeViewModel.kt uses for its own fire-and-forget work) -- a screen-scoped
// rememberCoroutineScope() would get cancelled on navigation, which is exactly the shared-editor
// design above makes unnecessary to worry about, but screens still call flush() (below) before
// navigating away or submitting, so a process death in the following ~400ms can't drop the very
// last keystroke either.
package bd.sicip.qavisit.ui.reports

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.domain.report.ReportData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

private const val TEXT_DEBOUNCE_MS = 400L

class ReportEditor(initialReport: Report, private val db: AppDb) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var saveJob: Job? = null

    var report by mutableStateOf(initialReport)
        private set
    var data by mutableStateOf(ReportData.parse(initialReport.data))
        private set

    // submitted reports are read-only on both platforms (spec, "Lifecycle") -- the UI must not
    // offer editable controls once true, but this is also the last line of defense.
    val readOnly: Boolean get() = report.status == "submitted"

    // discrete edit: a checklist tap, a choice field, adding/removing a card row, ticking a
    // flag. each of these is a single, already-final decision -- no reason to wait.
    fun editNow(newData: ReportData) = commit(newData, debounceMs = 0L)

    // free-typed text: local state updates immediately (below), but the Room write waits for a
    // pause in typing so e.g. a 40-character remarks field doesn't write 40 times.
    fun editDebounced(newData: ReportData) = commit(newData, debounceMs = TEXT_DEBOUNCE_MS)

    private fun commit(newData: ReportData, debounceMs: Long) {
        if (readOnly) return
        data = newData
        saveJob?.cancel()
        saveJob = scope.launch {
            if (debounceMs > 0) delay(debounceMs)
            persist()
        }
    }

    // one write at a time, always of the LATEST data: a cancelled older write that is
    // already inside room can then never land after a newer one.
    private val writeLock = Mutex()

    private suspend fun persist() = writeLock.withLock {
        val updated = report.copy(data = data.toJsonString(), updatedAt = Instant.now().toString(), dirty = true)
        db.reportDao().upsert(updated)
        report = updated
    }

    // no debounced write waiting or running -- safe to swap this editor for a fresher row
    val isIdle: Boolean get() = saveJob?.isActive != true

    suspend fun flush() {
        if (readOnly) return
        saveJob?.cancel()
        persist()
    }

    suspend fun submit() {
        flush()
        writeLock.withLock {
            val now = Instant.now().toString()
            val updated = report.copy(status = "submitted", submittedAt = now, updatedAt = now, dirty = true)
            db.reportDao().upsert(updated)
            report = updated
        }
    }
}

class ReportEditorRegistry(private val db: AppDb) {
    private val editors = mutableMapOf<String, ReportEditor>()

    // reuse the open editor (one in-memory copy per report), but take the room row instead
    // when sync pulled a different version (web edit, submit elsewhere) and nothing is pending.
    fun forReport(report: Report): ReportEditor {
        val existing = editors[report.id]
        if (existing != null && (!existing.isIdle || existing.report.updatedAt == report.updatedAt)) return existing
        return ReportEditor(report, db).also { editors[report.id] = it }
    }
}

@Composable
fun rememberReportEditorRegistry(db: AppDb): ReportEditorRegistry = remember(db) { ReportEditorRegistry(db) }
