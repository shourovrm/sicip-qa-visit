// shared "get me into a report" entry point -- the Reports tab's per-visit "Start report" row,
// its "New report" sheet, and Home's ongoing-visit report line all go through
// findOrCreateReport instead of inserting a Report row directly, so "one report per (visit,
// type) among non-deleted rows" (spec, Contract) can't drift out of sync between call sites.
package bd.sicip.qavisit.ui.reports

import android.content.Context
import bd.sicip.qavisit.data.db.AppDb
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.data.db.Visit
import bd.sicip.qavisit.domain.report.ReportBlock
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.domain.report.loadReportTemplate
import bd.sicip.qavisit.domain.report.newReport
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.util.UUID

// the only type shipped so far -- Monitoring/QA stays disabled in the UI (spec: "coming in a
// later version") until its own template exists.
const val REPORT_TYPE_SURPRISE = "surprise"

fun surpriseTemplate(context: Context): ReportTemplate = loadReportTemplate(context, "surprise-v1.json")

fun reportTypeLabel(type: String): String = when (type) {
    REPORT_TYPE_SURPRISE -> "Surprise visit"
    else -> type.replaceFirstChar { it.uppercase() }
}

// "N sections · N items" for the New-report sheet's type option -- NEVER hardcode these counts
// (spec CHANGE SET 3: "13 sections · 38 items must be derived from the template"), the template
// keeps changing shape (checklist item count alone moved 38 -> 31 -> 31 across CHANGE SETS 2-3).
fun templateSummary(template: ReportTemplate): String {
    val itemCount = template.sections
        .flatMap { it.blocks }
        .filterIsInstance<ReportBlock.Checklist>()
        .sumOf { it.items.size }
    return "${template.sections.size} sections · $itemCount items"
}

// officer's designation has no backing column yet (data/db/Officer.kt) -- always null for now,
// so the "officers" prefill falls back to name alone (see domain/report/NewReport.kt).
suspend fun findOrCreateReport(
    db: AppDb,
    template: ReportTemplate,
    visit: Visit,
    officerId: String,
    officerName: String,
    type: String = REPORT_TYPE_SURPRISE,
): Report {
    val existing = db.reportDao().byVisitFlow(visit.id).first().firstOrNull { it.type == type }
    if (existing != null) return existing

    val now = Instant.now().toString()
    val data = newReport(template, visit, officerName, officerDesignation = null)
    val report = Report(
        id = UUID.randomUUID().toString(),
        officerId = officerId,
        visitId = visit.id,
        type = type,
        templateVersion = template.version,
        data = data.toJsonString(),
        status = "draft",
        submittedAt = null,
        createdAt = now,
        updatedAt = now,
        deleted = false,
        dirty = true,
    )
    db.reportDao().upsert(report)
    return report
}
