// pure reads a TMS-enabled report needs: is it a TMS template, what did the officer enter as
// institute / association / visit date, how to label a snapshot time, and the failure wording.
package bd.sicip.qavisit.domain.report

import bd.sicip.qavisit.data.tms.TmsApiChangeException
import bd.sicip.qavisit.data.tms.TmsException
import bd.sicip.qavisit.data.tms.TmsTransientException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val TMS_BLOCK_KEYS = setOf(MOU_COURSES, CUMULATIVE, BATCHES)

fun ReportSection.hasTmsBlocks(): Boolean = blocks.any { it is ReportBlock.Cards && it.key in TMS_BLOCK_KEYS }

// value of the report field the template prefills from `prefill` (institute | association | visit_date).
fun ReportTemplate.prefilledValue(data: ReportData, prefill: String): String {
    val field = sections.flatMap { it.blocks }.filterIsInstance<ReportBlock.Fields>()
        .flatMap { it.fields }.firstOrNull { it.prefill == prefill } ?: return ""
    return data.field(field.key)
}

// visit date as stored (ISO yyyy-MM-dd); today when missing or unreadable.
fun ReportTemplate.visitDate(data: ReportData, today: LocalDate = LocalDate.now()): LocalDate =
    runCatching { LocalDate.parse(prefilledValue(data, "visit_date").trim()) }.getOrDefault(today)

// "29 Sep, 14:10" in the phone's zone.
fun tmsTimeLabel(iso: String, zone: ZoneId = ZoneId.systemDefault()): String {
    val instant = runCatching { Instant.parse(iso) }.getOrNull() ?: return iso
    return DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.ENGLISH).withZone(zone).format(instant)
}

// what the section shows when "Fill from TMS" fails.
data class TmsFillFailure(val message: String, val openSettings: Boolean = false, val canUseCached: Boolean = false)

fun tmsFillFailure(error: Throwable, hasCachedSnapshot: Boolean): TmsFillFailure = when (error) {
    is TmsApiChangeException ->
        TmsFillFailure("TMS data unavailable: TMS has changed; the admin is informed")
    is TmsTransientException ->
        TmsFillFailure("No connection to TMS. Try again when online.", canUseCached = hasCachedSnapshot)
    is TmsException ->
        TmsFillFailure(error.message ?: "TMS request failed", openSettings = true)
    else -> TmsFillFailure("TMS data unavailable: ${error.message ?: "unknown error"}")
}
