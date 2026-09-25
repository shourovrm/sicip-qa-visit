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

const val REPORT_TYPE_SURPRISE = "surprise"
const val REPORT_TYPE_QA = "qa"

fun surpriseTemplate(context: Context): ReportTemplate = loadReportTemplate(context, "surprise-v1.json")
fun qaTemplate(context: Context): ReportTemplate = loadReportTemplate(context, "qa-v1.json")

// the one place a report `type` string picks its template -- every screen that opens a report
// (hub/section/review/PDF) calls this instead of hardcoding which loader to use, so adding a
// third report type later is a one-line change here, not a find-and-replace across the UI.
fun templateForType(context: Context, type: String): ReportTemplate = when (type) {
    REPORT_TYPE_QA -> qaTemplate(context)
    else -> surpriseTemplate(context)
}

// visits.visit_type -> report type (QA report spec §1). null means either a non-Monitoring-Visit
// purpose (no report at all, callers must not reach here) or an old Monitoring Visit row from
// before visit_type existed -- the officer must be asked which report to start (see
// ReportsScreen.kt's NewReportSheet and NoReportVisitRow).
fun reportTypeForVisit(visit: Visit): String? = when (visit.visitType) {
    "qa" -> REPORT_TYPE_QA
    "surprise" -> REPORT_TYPE_SURPRISE
    else -> null
}

// spec §1: "Only Monitoring Visit visits get reports" -- both templates' own `purposes` list is
// ["Monitoring Visit"] too, but call sites that only need this yes/no check (never touch a
// template instance) use this instead, so they don't have to load one just to ask it.
fun visitEligibleForReport(visit: Visit): Boolean = visit.purpose == "Monitoring Visit"

fun reportTypeLabel(type: String): String = when (type) {
    REPORT_TYPE_SURPRISE -> "Surprise visit"
    REPORT_TYPE_QA -> "QA visit"
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

// convenience wrapper for the two call sites (HomeScreen.kt's ongoing-visit report line,
// ReportsScreen.kt's New-report sheet + "Start report" row) that already know exactly which
// type to start -- resolves the type's template and hands off to findOrCreateReport in one call,
// so neither call site repeats "load the template, then find-or-create" itself.
suspend fun startReportForVisit(db: AppDb, context: Context, visit: Visit, officerId: String, officerName: String, type: String): Report {
    val template = templateForType(context, type)
    return findOrCreateReport(db, template, visit, officerId, officerName, type = type)
}
