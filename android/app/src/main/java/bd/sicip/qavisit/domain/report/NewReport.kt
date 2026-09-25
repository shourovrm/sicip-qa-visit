// builds a brand-new report's data JSON: prefills fields from the visit, seeds cards[key] with
// `start` empty objects {} per cards block. Each seeded card gets a random _id (see
// ReportData.withCardAdded's default) so every card is uniquely addressable from creation.
//
// UI entry point: call newReport(template, visit, officerName, officerDesignation) from the
// "Start report" flow once the officer picked a type + visit; wrap the
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
                    // linked blocks (e.g. attendance) get their rows from normalize below, never
                    // from `start` seeding -- their own `start` is 0 in the template anyway, but
                    // this guard is the one place that rule actually lives in code.
                    if (block.linkFrom == null) {
                        repeat(block.start) { data = data.withCardAdded(block.key) }
                    }
                }

                // checklist/flags/criteria start with nothing ticked/marked -- no seeding needed.
                is ReportBlock.Checklist, is ReportBlock.Flags, is ReportBlock.Criteria -> Unit
            }
        }
    }

    // normalize runs after every edit and on open -- a brand-new report counts as an open, so a
    // template whose seeded source cards already carry linked field values shows its derived
    // cards immediately instead of after the first edit.
    return normalize(template, data)
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
