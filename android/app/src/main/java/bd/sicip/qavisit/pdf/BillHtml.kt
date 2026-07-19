// TA/DA bill PDF -- HTML/CSS template rendered through WebView's print pipeline (see
// pdf/BillPrinter.kt). Structure/column widths/labels measured straight off
// assets/tada-template.xlsx (xl/worksheets/sheet1.xml column widths + merged cells,
// xl/sharedStrings.xml for the exact label text, xl/drawings/drawing1.xml for logo spots).
// pure string building, no android imports -- so this file's HTML output is a plain JVM unit
// test target (see test/.../pdf/BillHtmlTest.kt), the actual print-to-PDF is BillPrinter's job.
package bd.sicip.qavisit.pdf

import bd.sicip.qavisit.data.seed.TICKET_REMARK
import bd.sicip.qavisit.domain.BillTotals
import bd.sicip.qavisit.domain.amountInWords
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// one itinerary row as it prints. nightStay/foodDay per-leg fields still feed BillMath edit
// defaults, but the printed night/food cells are TRIP-level (BillTrip.nights/foodDays) merged
// over every leg row of the trip via rowspan.
data class BillLeg(
    val depDate: String,
    val depTime: String,
    val depPlace: String,
    val arrDate: String,
    val arrTime: String,
    val arrPlace: String,
    val mode: String,
    val travelClass: String?,
    val fare: Double,
    val remarks: String?,
    val nightStay: Int,
    val foodDay: Double,
)

// one finished trip batched into the bill: a "Purpose: ..." band, its itinerary rows, and
// the trip-level night/food totals (BillMath.Trip.resolvedNights/resolvedFood, overrides and
// all) that feed the "Total" row's night/food-day counts.
data class BillTrip(
    val purposeLine: String,
    val legs: List<BillLeg>,
    val nights: Int,
    val foodDays: Double,
)

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.US)
private val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
private val PURPOSE_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

// the purpose band's date: the office order's ref_date when the visit has one (the date the
// order was actually issued), only falling back to the visit's own start_date when no ref_date
// was recorded -- never the other way around. shared by ui/bill/BillScreen.kt (which assembles
// the rest of the purpose line) so this one-line rule has a single, JVM-testable home.
fun purposeDate(startDate: String, refDate: String?): String =
    formatDisplayDate(refDate ?: startDate, PURPOSE_DATE)

// xlsx <cols> widths (character units) for A..L, kept proportional to the original -- same
// weights the canvas layout used, now expressed as CSS column percentages.
private val COL_WEIGHTS = doubleArrayOf(8.63, 8.37, 25.82, 9.09, 8.54, 24.54, 5.91, 8.18, 10.54, 9.82, 12.18, 11.54)

// same idea, A..I widths straight off assets/local-tada-template.xlsx (no Night/Food/Class cols).
private val LOCAL_COL_WEIGHTS = doubleArrayOf(8.63, 8.37, 25.82, 9.09, 8.54, 24.54, 10.54, 12.18, 11.54)

fun buildBillHtml(
    officerName: String,
    billDate: String,
    trips: List<BillTrip>,
    totals: BillTotals,
): String = buildString {
    append("<!doctype html><html><head><meta charset=\"utf-8\">")
    append("<style>").append(CSS).append("</style></head><body>")
    append(letterheadHtml())
    append(nameDateHtml(officerName, billDate))
    append("<div class=\"band\">Detailed Travel Itinerary:</div>")
    append("<table class=\"itinerary\">")
    append(tableHeadHtml())
    append("<tbody>")
    trips.forEach { append(tripRowsHtml(it)) }
    append(totalsRowsHtml(trips, totals))
    append("</tbody></table>")
    append(footerHtml(officerName))
    append("</body></html>")
}

// local-travel variant: same letterhead/name-date, 9 cols (drops Night/Food/Class), one-row
// total, no "Recommended By" -- see assets/local-tada-template.xlsx.
fun buildLocalBillHtml(officerName: String, billDate: String, trips: List<BillTrip>): String = buildString {
    val filtered = localBillTrips(trips)
    val fareSum = filtered.sumOf { t -> t.legs.sumOf { it.fare } }
    append("<!doctype html><html><head><meta charset=\"utf-8\">")
    append("<style>").append(CSS).append("</style></head><body>")
    append(letterheadHtml())
    append(nameDateHtml(officerName, billDate))
    append("<div class=\"band\">Detailed Local Travel Itinerary:</div>")
    append("<table class=\"itinerary\">")
    append(localTableHeadHtml())
    append("<tbody>")
    filtered.forEach { append(localTripRowsHtml(it)) }
    append(localTotalsRowHtml(fareSum))
    append("</tbody></table>")
    append(footerHtml(officerName, includeRecommended = false))
    append("</body></html>")
}

// row filter for the local bill: ticket-attached legs and zero-fare legs never belonged on a
// cash claim itinerary in the first place -- drop them (N/A-mode alone no longer excludes a
// leg, only fare = 0 does), and drop a trip's whole purpose band if nothing survives (pure so
// it's unit-testable on its own).
fun localBillTrips(trips: List<BillTrip>): List<BillTrip> = trips.mapNotNull { trip ->
    val legs = trip.legs.filter { it.fare > 0.0 && it.remarks?.contains(TICKET_REMARK) != true }
    if (legs.isEmpty()) null else trip.copy(legs = legs)
}

private fun letterheadHtml(): String = """
    <div class="letterhead">
      <img class="seal" src="file:///android_asset/logos/bd-govt-seal.jpg">
      <div class="org-lines">
        <div class="org-line1">Government of the People&rsquo;s Republic of Bangladesh</div>
        <div class="org-line2">Skills for Industry Competitiveness and Innovation Program (SICIP)</div>
        <div class="org-line2">Finance Division, Ministry of Finance</div>
      </div>
      <img class="sicip" src="file:///android_asset/logos/sicip-logo.jpg">
    </div>
    <div class="band title">TA/DA Bill</div>
""".trimIndent()

private fun nameDateHtml(officerName: String, billDate: String): String = """
    <div class="name-date">
      <div><b>Name &amp; Designation:</b> ${esc(officerName)}, Program Officer (QA)</div>
      <div><b>Date:</b> ${formatDisplayDate(billDate, HEADER_DATE)}</div>
    </div>
""".trimIndent()

private fun colGroupWidths(weights: DoubleArray): String =
    weights.joinToString("") { w -> "<col style=\"width:${"%.4f".format(Locale.US, w / weights.sum() * 100)}%\">" }

private fun tableHeadHtml(): String = """
    <colgroup>${colGroupWidths(COL_WEIGHTS)}</colgroup>
    <thead>
      <tr>
        <th colspan="3">Departure</th>
        <th colspan="3">Arrival</th>
        <th rowspan="2">No. of Night Stay</th>
        <th rowspan="2">No. of Day for Food Allowance</th>
        <th rowspan="2">Mode of Transport</th>
        <th rowspan="2">Class</th>
        <th rowspan="2">Fare (Tk.)</th>
        <th rowspan="2">Remarks</th>
      </tr>
      <tr>
        <th>Date</th><th>Time</th><th>Place</th>
        <th>Date</th><th>Time</th><th>Place</th>
      </tr>
    </thead>
""".trimIndent()

private fun localTableHeadHtml(): String = """
    <colgroup>${colGroupWidths(LOCAL_COL_WEIGHTS)}</colgroup>
    <thead>
      <tr>
        <th colspan="3">Departure</th>
        <th colspan="3">Arrival</th>
        <th rowspan="2">Mode of Transport</th>
        <th rowspan="2">Fare (Tk.)</th>
        <th rowspan="2">Remarks</th>
      </tr>
      <tr>
        <th>Date</th><th>Time</th><th>Place</th>
        <th>Date</th><th>Time</th><th>Place</th>
      </tr>
    </thead>
""".trimIndent()

// -- one trip: a full-width purpose band, then its legs day-grouped so the date cells rowspan
// over every leg that shares the same calendar (departure) day; night/food cells rowspan the
// whole trip and carry the trip-level values. --

// groups leg indices into same-departure-day runs, shared by both the full and local row
// builders (only the per-leg cells they emit differ).
private fun daySpans(legs: List<BillLeg>): List<IntRange> {
    val spans = mutableListOf<IntRange>()
    var i = 0
    while (i < legs.size) {
        var j = i
        while (j + 1 < legs.size && legs[j + 1].depDate == legs[i].depDate) j++
        spans.add(i..j)
        i = j + 1
    }
    return spans
}

// N/A means no mode claimed at all -- print the same dash the (null) class column already uses.
private fun modeCell(mode: String): String = if (mode == "N/A") "-" else esc(mode)

private fun tripRowsHtml(trip: BillTrip): String = buildString {
    append("<tr class=\"purpose\"><td colspan=\"12\">${esc(trip.purposeLine)}</td></tr>")
    for (span in daySpans(trip.legs)) {
        val first = trip.legs[span.first]
        for (k in span) {
            val leg = trip.legs[k]
            append("<tr>")
            if (k == span.first) {
                append(td(formatDisplayDate(first.depDate, DISPLAY_DATE), rowspan = span.count()))
            }
            append(td(formatDisplayTime(leg.depTime), cls = "time"))
            append(td(esc(leg.depPlace), cls = "place"))
            if (k == span.first) {
                append(td(formatDisplayDate(first.arrDate, DISPLAY_DATE), rowspan = span.count()))
            }
            append(td(formatDisplayTime(leg.arrTime), cls = "time"))
            append(td(esc(leg.arrPlace), cls = "place"))
            // night/food merge over the WHOLE trip (not per day) and show the trip-level
            // selected values -- per-leg counts never printed, they confused reviewers.
            if (k == 0) {
                append(td(dashIfZero(trip.nights.toDouble()), rowspan = trip.legs.size))
                append(td(dashIfZero(trip.foodDays), rowspan = trip.legs.size))
            }
            append(td(modeCell(leg.mode)))
            append(td(esc(leg.travelClass ?: "-")))
            append(td(dashIfZero(leg.fare), cls = "money"))
            append(td(esc(leg.remarks ?: "")))
            append("</tr>")
        }
    }
}

// local variant: same day-grouping, only the two date columns rowspan (no night/food/class).
private fun localTripRowsHtml(trip: BillTrip): String = buildString {
    append("<tr class=\"purpose\"><td colspan=\"9\">${esc(trip.purposeLine)}</td></tr>")
    for (span in daySpans(trip.legs)) {
        val first = trip.legs[span.first]
        for (k in span) {
            val leg = trip.legs[k]
            append("<tr>")
            if (k == span.first) {
                append(td(formatDisplayDate(first.depDate, DISPLAY_DATE), rowspan = span.count()))
            }
            append(td(formatDisplayTime(leg.depTime), cls = "time"))
            append(td(esc(leg.depPlace), cls = "place"))
            if (k == span.first) {
                append(td(formatDisplayDate(first.arrDate, DISPLAY_DATE), rowspan = span.count()))
            }
            append(td(formatDisplayTime(leg.arrTime), cls = "time"))
            append(td(esc(leg.arrPlace), cls = "place"))
            append(td(modeCell(leg.mode)))
            append(td(dashIfZero(leg.fare), cls = "money"))
            append(td(esc(leg.remarks ?: "")))
            append("</tr>")
        }
    }
}

private fun totalsRowsHtml(trips: List<BillTrip>, totals: BillTotals): String {
    val nightsSum = trips.sumOf { it.nights }
    val foodSum = trips.sumOf { it.foodDays }
    val words = "${amountInWords(totals.net.toLong())} Only"
    return """
      <tr class="totals">
        <td colspan="6"><b>Total</b></td>
        <td><b>${dashIfZero(nightsSum.toDouble())}</b></td>
        <td><b>${dashIfZero(foodSum)}</b></td>
        <td colspan="2"><b>Total Travel Allowance</b></td>
        <td class="money"><b>${dashIfZero(totals.ta)}</b></td>
        <td></td>
      </tr>
      <tr>
        <td colspan="10">Total Accomodation Allowance (2,000 BDT Per Night Stay)</td>
        <td class="money">${dashIfZero(totals.accommodation)}</td>
        <td></td>
      </tr>
      <tr>
        <td colspan="10">Total Food Allowance (1,500 BDT Per Day)</td>
        <td class="money">${dashIfZero(totals.food)}</td>
        <td></td>
      </tr>
      <tr class="net">
        <td colspan="10"><b>Net claim bill TK. (In word) ${esc(words)}</b></td>
        <td class="money"><b>${dashIfZero(totals.net)}</b></td>
        <td></td>
      </tr>
    """.trimIndent()
}

// local bill's one-and-only totals row: "Total" spans the 7 itinerary columns, then the fare
// sum, then an empty remarks cell (see assets/local-tada-template.xlsx row 24).
private fun localTotalsRowHtml(fareSum: Double): String = """
    <tr class="totals">
      <td colspan="7"><b>Total</b></td>
      <td class="money"><b>${dashIfZero(fareSum)}</b></td>
      <td></td>
    </tr>
""".trimIndent()

// includeRecommended=false drops the second signature column for the local bill (no "Recommended By").
private fun footerHtml(officerName: String, includeRecommended: Boolean = true): String = buildString {
    append("""
        <div class="footer">
          <div class="sig-col">
            <div><b>Submitted By:</b></div>
            <div class="sig-gap"></div>
            <div class="sig-line"></div>
            <div>${esc(officerName)}</div>
            <div>Program Officer (QA)</div>
            <div>SICIP</div>
          </div>
    """.trimIndent())
    if (includeRecommended) {
        append("""
            <div class="sig-col">
              <div><b>Recommended By:</b></div>
              <div class="sig-gap"></div>
              <div class="sig-line"></div>
            </div>
        """.trimIndent())
    }
    append("</div>")
}

// -- table cell + formatting helpers --

private fun td(text: String, rowspan: Int = 1, cls: String? = null): String {
    val attrs = buildString {
        if (rowspan > 1) append(" rowspan=\"$rowspan\"")
        if (cls != null) append(" class=\"$cls\"")
    }
    return "<td$attrs>$text</td>"
}

private fun formatDisplayDate(iso: String, fmt: DateTimeFormatter): String =
    runCatching { LocalDate.parse(iso).format(fmt) }.getOrDefault(iso)

private fun formatDisplayTime(iso: String): String =
    runCatching { LocalTime.parse(iso).format(DISPLAY_TIME) }.getOrDefault(iso)

// drops a bare ".00" like TripMath.formatFare, minus the currency glyph (the column header
// already says "Fare (Tk.)", so the template shows bare numbers) -- thousands separated.
private fun plainAmount(v: Double): String {
    val s = String.format(Locale.US, "%,.2f", v)
    return if (s.endsWith(".00")) s.dropLast(3) else s
}

private fun dashIfZero(v: Double): String = if (v == 0.0) "-" else plainAmount(v)

private fun esc(s: String): String = s
    .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

// -- print CSS: system font stack (WebView's text stack handles unicode fallback correctly,
// unlike the old canvas Paint) -- swap in bundled Noto Sans .ttf assets only if glyphs break.
// border hierarchy: 1pt table outline + section borders, 0.4pt inner grid.
private val CSS = """
    @page { size: A4 landscape; margin: 12mm; }
    * { box-sizing: border-box; }
    body { font-family: 'Noto Sans', Roboto, sans-serif; font-size: 8pt; color: #000; margin: 0; }
    .letterhead { display: flex; align-items: flex-start; justify-content: space-between; }
    .letterhead .seal { width: 45pt; height: 40pt; object-fit: contain; }
    .letterhead .sicip { width: 75pt; height: 37pt; object-fit: contain; }
    .org-lines { flex: 1; text-align: center; }
    .org-line1 { font-size: 10pt; font-weight: bold; margin-bottom: 2pt; }
    .org-line2 { font-size: 8.5pt; margin-bottom: 1pt; }
    .band { background: #E4E4E4; padding: 3pt 6pt; font-weight: bold; }
    .band.title { text-align: center; font-size: 15pt; margin-top: 6pt; }
    .name-date { display: flex; justify-content: space-between; padding: 8pt 2pt 6pt; font-size: 9pt; }
    table.itinerary { width: 100%; border-collapse: collapse; table-layout: fixed; border: 1pt solid #000; }
    table.itinerary th, table.itinerary td {
      border: 0.4pt solid #000; padding: 2.5pt 3pt; font-size: 7.5pt; text-align: center;
      vertical-align: middle; overflow-wrap: break-word;
    }
    table.itinerary thead { display: table-header-group; }
    table.itinerary thead th { font-weight: bold; background: #fff; }
    table.itinerary td.place { text-align: left; white-space: normal; }
    table.itinerary td.money { text-align: right; font-variant-numeric: tabular-nums; white-space: nowrap; }
    table.itinerary td.time { white-space: nowrap; }
    table.itinerary tr.purpose td { text-align: left; font-weight: bold; background: #E4E4E4; }
    table.itinerary tr.totals td { background: #E4E4E4; }
    table.itinerary tr.net td { padding-top: 4pt; padding-bottom: 4pt; }
    .footer { display: flex; justify-content: space-between; margin-top: 14pt; font-size: 9pt; }
    .footer .sig-col { width: 45%; }
    .footer .sig-gap { height: 55pt; }
    .footer .sig-line { width: 70%; border-bottom: 1pt dotted #000; margin-bottom: 6pt; }
""".trimIndent()
