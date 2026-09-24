// builds a brand-new report's data JSON (spec: "New report: prefill fields from the visit;
// seed cards[key] with `start` empty objects {} per cards block").
//
// API for agent A2 (UI): call newReport(template, visit, officerName, officerDesignation) from
// the "Start report" flow (bottom-sheet S1) once the officer picked a type + visit; wrap the
// result straight into a fresh Report row: Report(id = uuid, officerId, visitId = visit.id,
// type, templateVersion = template.version, data = newReport(...).toJsonString(), status =
// "draft", createdAt = now, updatedAt = now, dirty = true). officerDesignation has no backing
// column on data/db/Officer.kt today -- pass null (prefill falls back to name alone) until a
// designation field exists, or thread through whatever string the UI has on hand (e.g. role).
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.db.Visit

fun newReport(
    template: ReportTemplate,
    visit: Visit,
    officerName: String,
    officerDesignation: String? = null,
): ReportData {
    var data = ReportData.EMPTY

    template.sections.forEach { section ->
        section.blocks.forEach { block ->
            when (block) {
                is ReportBlock.Fields -> {
                    block.fields.forEach { field ->
                        val value = prefillValue(field.prefill, visit, officerName, officerDesignation)
                        if (value != null) data = data.withField(field.key, value)
                    }
                }

                is ReportBlock.Cards -> {
                    repeat(block.start) { data = data.withCardAdded(block.key) }
                }

                // checklist/flags start with nothing ticked -- no seeding needed.
                is ReportBlock.Checklist, is ReportBlock.Flags -> Unit
            }
        }
    }

    return data
}

private fun prefillValue(
    prefill: String?,
    visit: Visit,
    officerName: String,
    officerDesignation: String?,
): String? = when (prefill) {
    "institute" -> visit.institute
    "association" -> visit.association
    "visit_date" -> visit.startDate
    "officers" -> if (officerDesignation.isNullOrBlank()) officerName else "$officerName ($officerDesignation)"
    else -> null
}
