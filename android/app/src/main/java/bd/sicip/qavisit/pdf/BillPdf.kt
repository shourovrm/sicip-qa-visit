// TA/DA bill PDF -- draws the official government bill 1:1, layout measured straight off
// assets/tada-template.xlsx (xl/worksheets/sheet1.xml column widths + merged cells,
// xl/sharedStrings.xml for the exact label text, xl/drawings/drawing1.xml for logo spots).
// this is the one screen in the app allowed to look like a government form instead of the
// app's own material theme -- so no compose here, just android.graphics straight onto a page.
package bd.sicip.qavisit.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import bd.sicip.qavisit.domain.BillTotals
import bd.sicip.qavisit.domain.amountInWords
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// one itinerary row as it prints. night/food are per-leg display values (see
// domain/BillMath.legDefaults) -- day-grouped, so only the first leg of a calendar day
// carries a real number and the rest are 0/0.0 (rendered as "-", matching the template's
// merged cells without literally merging cells on the canvas -- see BillPdfTest notes below).
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

// A4 LANDSCAPE in points -- PdfDocument's native unit, 1/72in. the template's own
// xl/worksheets/sheet1.xml pageSetup says orientation="landscape" paperSize="9" (A4) --
// portrait was the root cause of the Fare/Time column squeeze (defects #4/#6): every
// column had ~46% less width to work with than the template gives it.
private const val PAGE_W = 842f
private const val PAGE_H = 595f
private const val MARGIN = 32f
private const val TABLE_W = PAGE_W - 2 * MARGIN

private const val ROW_H = 15f
private const val HEADER_ROW_H = 17f
// bottom-of-page space reserved for the totals block (4 rows) + submitted/recommended
// footer, so that block always lands on the same page as the itinerary's last row.
private const val RESERVED_FOOTER_H = 195f

// xlsx <cols> widths (character units) for A..L, kept proportional to the original.
private val COL_WEIGHTS = floatArrayOf(8.63f, 8.37f, 25.82f, 9.09f, 8.54f, 24.54f, 5.91f, 8.18f, 10.54f, 9.82f, 12.18f, 11.54f)
private val COL_X: FloatArray = run {
    val total = COL_WEIGHTS.sum()
    val xs = FloatArray(COL_WEIGHTS.size + 1)
    xs[0] = MARGIN
    var x = MARGIN
    COL_WEIGHTS.forEachIndexed { i, w -> x += w / total * TABLE_W; xs[i + 1] = x }
    xs
}
private fun colL(i: Int) = COL_X[i]
private fun colR(i: Int) = COL_X[i + 1]

private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.US)
private val DISPLAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

// caveman rule: draw fns stay small and single-purpose so the page layout reads top to
// bottom below -- header, name/date, itinerary label, table, totals, footer.

fun generateBillPdf(
    context: Context,
    officerName: String,
    billDate: String,
    trips: List<BillTrip>,
    totals: BillTotals,
): File {
    val seal = loadLogo(context, "logos/bd-govt-seal.jpg")
    val sicip = loadLogo(context, "logos/sicip-logo.jpg")
    val rows = flattenRows(trips)
    val bottomLimit = PAGE_H - MARGIN - RESERVED_FOOTER_H

    val doc = PdfDocument()
    var idx = 0
    var pageNum = 1
    do {
        val info = PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageNum).create()
        val page = doc.startPage(info)
        val canvas = page.canvas

        var y = MARGIN
        y = if (pageNum == 1) {
            y = drawLetterhead(canvas, seal, sicip, y)
            y = drawNameDate(canvas, officerName, billDate, y)
            drawItineraryLabel(canvas, y)
        } else {
            drawContinuationNote(canvas, y, pageNum)
        }

        val tableTop = y
        val headerMid = tableTop + HEADER_ROW_H
        val headerBottom = tableTop + 2 * HEADER_ROW_H
        val bands = mutableListOf<RowBand>(RowBand.Header(tableTop, headerMid, headerBottom))
        drawTableHeader(canvas, tableTop)
        y = headerBottom

        val fit = rowsPerPage(bottomLimit, y, ROW_H)
        val chunkEnd = minOf(idx + fit, rows.size)
        for (i in idx until chunkEnd) {
            val bottom = y + ROW_H
            when (val row = rows[i]) {
                is PdfRow.Purpose -> {
                    drawPurposeRow(canvas, row.text, y, bottom)
                    bands.add(RowBand.Purpose(y, bottom))
                }
                is PdfRow.Leg -> {
                    drawLegRow(canvas, row.leg, row.showDate, y, bottom)
                    bands.add(RowBand.Cells(y, bottom))
                }
            }
            y = bottom
        }
        drawGrid(canvas, bands)
        idx = chunkEnd

        val isLast = idx >= rows.size
        if (isLast) {
            y = drawTotalsBlock(canvas, trips, totals, y + 6f)
            drawFooterBlock(canvas, officerName, y)
        }

        doc.finishPage(page)
        pageNum++
    } while (idx < rows.size)

    val file = File(context.cacheDir, "tada_bill_${System.currentTimeMillis()}.pdf")
    FileOutputStream(file).use { doc.writeTo(it) }
    doc.close()
    return file
}

// -- flattening: one purpose band per trip, then its legs, day-grouped like the template --

private sealed class PdfRow {
    data class Purpose(val text: String) : PdfRow()
    data class Leg(val leg: BillLeg, val showDate: Boolean) : PdfRow()
}

private fun flattenRows(trips: List<BillTrip>): List<PdfRow> = buildList {
    trips.forEach { trip ->
        add(PdfRow.Purpose(trip.purposeLine))
        var lastDay: String? = null
        trip.legs.forEach { leg ->
            add(PdfRow.Leg(leg, showDate = leg.depDate != lastDay))
            lastDay = leg.depDate
        }
    }
}

// -- header block (logos + org lines + title), page 1 only --

private fun drawLetterhead(canvas: Canvas, seal: Bitmap?, sicip: Bitmap?, y0: Float): Float {
    val sealW = 50f; val sealH = 45f
    val sicipW = 84f; val sicipH = 41f
    seal?.let { canvas.drawBitmap(it, null, RectF(MARGIN, y0, MARGIN + sealW, y0 + sealH), null) }
    sicip?.let { canvas.drawBitmap(it, null, RectF(PAGE_W - MARGIN - sicipW, y0, PAGE_W - MARGIN, y0 + sicipH), null) }

    val midCx = (colL(2) + colR(9)) / 2
    var ty = y0 + 12f
    canvas.drawText("Government of the People’s Republic of Bangladesh", midCx, ty, textPaint(9f, bold = true, Paint.Align.CENTER))
    ty += 12f
    canvas.drawText("Skills for Industry Competitiveness and Innovation Program (SICIP)", midCx, ty, textPaint(7.5f, bold = false, Paint.Align.CENTER))
    ty += 11f
    canvas.drawText("Finance Division, Ministry of Finance", midCx, ty, textPaint(7.5f, bold = false, Paint.Align.CENTER))

    var y = y0 + maxOf(sealH, sicipH, 38f) + 8f
    canvas.drawText("TA/DA Bill", PAGE_W / 2, y + 10f, textPaint(13f, bold = true, Paint.Align.CENTER))
    y += 20f
    return y
}

// bug that produced defects #1/#2: textPaint()'s align param defaults to CENTER (right for
// the org header lines below, wrong here) -- every raw canvas.drawText call in this file
// must pass align = LEFT explicitly, or the label centers *on* colL(0) and half of it bleeds
// off the left edge of the page, jumbled together with whatever's centered next to it.
private fun drawNameDate(canvas: Canvas, officerName: String, billDate: String, y0: Float): Float {
    val y = y0 + 9f
    canvas.drawText("Name & Designation:", colL(0), y, textPaint(8f, bold = true, Paint.Align.LEFT))
    canvas.drawText("$officerName, Program Officer (QA)", colL(0) + 82f, y, textPaint(8f, bold = false, Paint.Align.LEFT))
    canvas.drawText("Date:", colL(9), y, textPaint(8f, bold = true, Paint.Align.LEFT))
    canvas.drawText(formatDisplayDate(billDate), colL(10), y, textPaint(8f, bold = false, Paint.Align.LEFT))
    return y0 + 16f
}

private fun drawItineraryLabel(canvas: Canvas, y0: Float): Float {
    canvas.drawText("Detailed Travel Itinerary:", colL(0), y0 + 9f, textPaint(8f, bold = true, Paint.Align.LEFT))
    return y0 + 13f
}

private fun drawContinuationNote(canvas: Canvas, y0: Float, page: Int): Float {
    canvas.drawText("TA/DA Bill (continued) — page $page", colL(0), y0 + 9f, textPaint(8f, bold = true, Paint.Align.LEFT))
    return y0 + 13f
}

// -- table header: Departure/Arrival group labels over Date/Time/Place sub-labels, and five
// single labels that visually span both header rows (Night/Food/Mode/Class/Fare/Remarks) --

private fun drawTableHeader(canvas: Canvas, y0: Float) {
    val h1 = y0 + HEADER_ROW_H
    val h2 = h1 + HEADER_ROW_H
    drawCellText(canvas, "Departure", colL(0), colR(2), y0, h1, bold = true)
    drawCellText(canvas, "Arrival", colL(3), colR(5), y0, h1, bold = true)
    listOf(
        6 to "No. of Night Stay", 7 to "No. of Day for Food Allowance", 8 to "Mode of Transport",
        9 to "Class", 10 to "Fare (Tk.)", 11 to "Remarks",
    ).forEach { (i, label) -> drawCellText(canvas, label, colL(i), colR(i), y0, h2, bold = true) }
    listOf(0 to "Date", 1 to "Time", 2 to "Place", 3 to "Date", 4 to "Time", 5 to "Place")
        .forEach { (i, label) -> drawCellText(canvas, label, colL(i), colR(i), h1, h2, bold = true) }
}

// -- one itinerary row: date/night/food blank on every leg after the first of its day,
// mirroring the template's merged G/H/A/D cells (see BillTrip/BillLeg doc comment) --

private fun drawPurposeRow(canvas: Canvas, text: String, top: Float, bottom: Float) {
    drawCellText(canvas, text, colL(0), colR(11), top, bottom, bold = true, align = Paint.Align.LEFT)
}

private fun drawLegRow(canvas: Canvas, leg: BillLeg, showDate: Boolean, top: Float, bottom: Float) {
    if (showDate) {
        drawCellText(canvas, formatDisplayDate(leg.depDate), colL(0), colR(0), top, bottom)
        drawCellText(canvas, formatDisplayDate(leg.arrDate), colL(3), colR(3), top, bottom)
        drawCellText(canvas, dashIfZero(leg.nightStay.toDouble()), colL(6), colR(6), top, bottom)
        drawCellText(canvas, dashIfZero(leg.foodDay), colL(7), colR(7), top, bottom)
    }
    drawCellText(canvas, formatDisplayTime(leg.depTime), colL(1), colR(1), top, bottom)
    drawCellText(canvas, leg.depPlace, colL(2), colR(2), top, bottom, align = Paint.Align.LEFT)
    drawCellText(canvas, formatDisplayTime(leg.arrTime), colL(4), colR(4), top, bottom)
    drawCellText(canvas, leg.arrPlace, colL(5), colR(5), top, bottom, align = Paint.Align.LEFT)
    drawCellText(canvas, leg.mode, colL(8), colR(8), top, bottom)
    drawCellText(canvas, leg.travelClass ?: "-", colL(9), colR(9), top, bottom)
    drawCellText(canvas, plainAmount(leg.fare), colL(10), colR(10), top, bottom)
    drawCellText(canvas, leg.remarks ?: "-", colL(11), colR(11), top, bottom, align = Paint.Align.LEFT)
}

// -- totals: itinerary column sums + TA, then accommodation/food/net rows --

private fun drawTotalsBlock(canvas: Canvas, trips: List<BillTrip>, totals: BillTotals, y0: Float): Float {
    var top = y0
    var bottom = top + ROW_H
    val nightsSum = trips.sumOf { it.nights }
    val foodSum = trips.sumOf { it.foodDays }
    drawCellText(canvas, "Total", colL(0), colR(5), top, bottom, bold = true, align = Paint.Align.LEFT)
    drawCellText(canvas, dashIfZero(nightsSum.toDouble()), colL(6), colR(6), top, bottom, bold = true)
    drawCellText(canvas, dashIfZero(foodSum), colL(7), colR(7), top, bottom, bold = true)
    drawCellText(canvas, "Total Travel Allowance", colL(8), colR(9), top, bottom, bold = true, align = Paint.Align.LEFT)
    drawCellText(canvas, plainAmount(totals.ta), colL(10), colR(10), top, bottom, bold = true)
    drawRowBorders(canvas, top, bottom)

    top = bottom; bottom = top + ROW_H
    drawCellText(canvas, "Total Accomodation Allowance (2,000 BDT Per Night Stay)", colL(0), colR(9), top, bottom, align = Paint.Align.LEFT)
    drawCellText(canvas, plainAmount(totals.accommodation), colL(10), colR(10), top, bottom)
    drawRowBorders(canvas, top, bottom)

    top = bottom; bottom = top + ROW_H
    drawCellText(canvas, "Total Food Allowance (1,500 BDT Per Day)", colL(0), colR(9), top, bottom, align = Paint.Align.LEFT)
    drawCellText(canvas, plainAmount(totals.food), colL(10), colR(10), top, bottom)
    drawRowBorders(canvas, top, bottom)

    top = bottom; bottom = top + ROW_H + 4f
    val words = "${amountInWords(totals.net.toLong())} Only"
    drawCellText(canvas, "Net claim bill TK. (In word) $words", colL(0), colR(9), top, bottom, bold = true, align = Paint.Align.LEFT)
    drawCellText(canvas, plainAmount(totals.net), colL(10), colR(10), top, bottom, bold = true)
    drawRowBorders(canvas, top, bottom)

    return bottom + 10f
}

// -- footer: submitted/recommended labels, signature gap, officer name + fixed designation.
// only Submitted By gets pre-filled -- Recommended By is a human supervisor's signature,
// left blank same as the template. --

// gaps measured off the template PDF (pdftotext -bbox-layout on the xlsx export): "Net
// claim..." row to "Submitted By:" is ~22pt, "Submitted By:" to the name line below it is
// ~61pt (room for an actual pen signature) -- not the cramped ~8pt/~42pt this used to be.
private fun drawFooterBlock(canvas: Canvas, officerName: String, y0: Float) {
    var y = y0 + 12f
    canvas.drawText("Submitted By:", colL(0), y, textPaint(8f, bold = true, Paint.Align.LEFT))
    canvas.drawText("Recommended By:", colL(9), y, textPaint(8f, bold = true, Paint.Align.LEFT))
    y += 60f
    canvas.drawText(officerName, colL(0), y, textPaint(8f, bold = false, Paint.Align.LEFT))
    y += 11f
    canvas.drawText("Program Officer (QA)", colL(0), y, textPaint(8f, bold = false, Paint.Align.LEFT))
    y += 10f
    canvas.drawText("SICIP", colL(0), y, textPaint(8f, bold = false, Paint.Align.LEFT))
}

// -- drawing primitives --

private fun textPaint(size: Float, bold: Boolean, align: Paint.Align = Paint.Align.CENTER): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        textSize = size
        color = Color.BLACK
        textAlign = align
    }

// word-wraps into as many lines as the column needs and centers the block vertically in
// [top, bottom] -- keeps narrow header columns (e.g. "No. of Night Stay") readable without
// a hand-tuned line break per label.
private fun drawCellText(
    canvas: Canvas,
    text: String,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    bold: Boolean = false,
    align: Paint.Align = Paint.Align.CENTER,
    size: Float = 7f,
) {
    val paint = textPaint(size, bold, align)
    val maxW = (right - left - 4f).coerceAtLeast(1f)
    val lines = wrapText(text, maxW, paint)
    val lineH = size + 2f
    var ty = (top + bottom) / 2 - (lines.size * lineH) / 2 + lineH - 2f
    val tx = when (align) {
        Paint.Align.LEFT -> left + 2f
        Paint.Align.RIGHT -> right - 2f
        else -> (left + right) / 2
    }
    lines.forEach { canvas.drawText(it, tx, ty, paint); ty += lineH }
}

private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
    if (paint.measureText(text) <= maxWidth) return listOf(text)
    val words = text.split(" ")
    val lines = mutableListOf<String>()
    var line = ""
    for (w in words) {
        val candidate = if (line.isEmpty()) w else "$line $w"
        if (paint.measureText(candidate) <= maxWidth) {
            line = candidate
        } else {
            if (line.isNotEmpty()) lines.add(line)
            line = w
        }
    }
    if (line.isNotEmpty()) lines.add(line)
    return lines
}

// one row-band of the table, tagged by what's actually drawn in it, so the grid can skip
// lines that would cut through a merged cell instead of bordering one:
//  - Header: two sub-rows: Departure/Arrival's Date|Time|Place split (mid line, cols 0..5
//    only) sits next to Night/Food/Mode/Class/Fare/Remarks, which span both sub-rows and
//    must NOT get that mid line (was defect #4's "line struck through the header").
//  - Purpose: one cell spanning every column -- no interior verticals (defect #3).
//  - Cells: a normal itinerary/totals row -- full column verticals as usual.
private sealed class RowBand(val top: Float, val bottom: Float) {
    class Header(top: Float, val mid: Float, bottom: Float) : RowBand(top, bottom)
    class Purpose(top: Float, bottom: Float) : RowBand(top, bottom)
    class Cells(top: Float, bottom: Float) : RowBand(top, bottom)
}

// cell-aware gridlines: horizontal rule under every band (partial under Header's mid line),
// vertical rules per column boundary but only spanning Header/Cells bands -- Purpose bands
// are skipped so a purpose row prints as one merged cell, borders only, never through text.
private fun drawGrid(canvas: Canvas, bands: List<RowBand>) {
    val top = bands.first().top; val bottom = bands.last().bottom
    val thin = Paint().apply { color = Color.BLACK; strokeWidth = 0.5f }

    canvas.drawLine(MARGIN, top, PAGE_W - MARGIN, top, thin)
    bands.forEach { band ->
        if (band is RowBand.Header) canvas.drawLine(colL(0), band.mid, colR(5), band.mid, thin)
        canvas.drawLine(MARGIN, band.bottom, PAGE_W - MARGIN, band.bottom, thin)
    }

    for (x in COL_X) {
        var segTop: Float? = null
        for (band in bands) {
            if (band is RowBand.Purpose) {
                if (segTop != null) canvas.drawLine(x, segTop, x, band.top, thin)
                segTop = null
            } else if (segTop == null) {
                segTop = band.top
            }
        }
        if (segTop != null) canvas.drawLine(x, segTop, x, bottom, thin)
    }

    val thick = Paint().apply { color = Color.BLACK; strokeWidth = 1.2f; style = Paint.Style.STROKE }
    canvas.drawRect(MARGIN, top, PAGE_W - MARGIN, bottom, thick)
}

private fun drawRowBorders(canvas: Canvas, top: Float, bottom: Float) {
    val thin = Paint().apply { color = Color.BLACK; strokeWidth = 0.5f }
    canvas.drawLine(MARGIN, top, PAGE_W - MARGIN, top, thin)
    canvas.drawLine(MARGIN, bottom, PAGE_W - MARGIN, bottom, thin)
    canvas.drawLine(MARGIN, top, MARGIN, bottom, thin)
    canvas.drawLine(colR(9), top, colR(9), bottom, thin)
    canvas.drawLine(PAGE_W - MARGIN, top, PAGE_W - MARGIN, bottom, thin)
}

private fun loadLogo(context: Context, assetPath: String): Bitmap? =
    runCatching { context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) } }.getOrNull()

// -- formatting --

private fun formatDisplayDate(iso: String): String =
    runCatching { LocalDate.parse(iso).format(DISPLAY_DATE) }.getOrDefault(iso)

private fun formatDisplayTime(iso: String): String =
    runCatching { LocalTime.parse(iso).format(DISPLAY_TIME) }.getOrDefault(iso)

// drops a bare ".00" like TripMath.formatFare, minus the currency glyph (the column header
// already says "Fare (Tk.)", so the template shows bare numbers).
private fun plainAmount(v: Double): String {
    val s = String.format(Locale.US, "%,.2f", v)
    return if (s.endsWith(".00")) s.dropLast(3) else s
}

private fun dashIfZero(v: Double): String = if (v == 0.0) "-" else plainAmount(v)
