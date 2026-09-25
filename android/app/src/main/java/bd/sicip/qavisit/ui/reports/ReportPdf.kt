// share-the-PDF action shared by the hub's "Preview PDF" and the review screen's "PDF" button --
// one place so both go through the exact same render+share path (pdf/BillPrinter.kt's WebView
// print pipeline, same one the TA/DA bill uses; renderBillPdf works on any HTML string).
package bd.sicip.qavisit.ui.reports

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.pdf.ReportMeta
import bd.sicip.qavisit.pdf.buildQaReportHtml
import bd.sicip.qavisit.pdf.buildReportHtml
import bd.sicip.qavisit.pdf.renderBillPdf

// matches the authority declared in AndroidManifest.xml for BillScreen's own FileProvider use.
private const val FILE_PROVIDER_AUTHORITY = "bd.sicip.qavisit.fileprovider"

suspend fun shareReportPdf(context: Context, template: ReportTemplate, report: Report, officerName: String) {
    val data = ReportData.parse(report.data)
    // QA report spec §7: Annex-3's own layout, a different builder from the surprise report's --
    // picked by template id, never by report.type string (the two are kept equal in practice,
    // but the template is the actual source of which output shape applies).
    val html = if (template.id == "qa") {
        buildQaReportHtml(template, data)
    } else {
        val meta = ReportMeta(officerName = officerName, status = report.status, submittedAt = report.submittedAt)
        buildReportHtml(template, data, meta)
    }
    val file = renderBillPdf(context, html, filePrefix = pdfFilePrefix(template, data))
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    // open in the phone's pdf viewer (it has its own share button); share sheet only when
    // no viewer app is installed
    val view = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share visit report"))
    }
}

// "Surprise-visit-UCAST-2026-09-23" / "QA-visit-UCAST-2026-09-25" -- readable when the file
// lands in whatsapp/email. renderBillPdf appends a timestamp, so repeated exports never collide.
// "ti_name"/"date_from" (qa) vs "visit_date" (surprise) are each template's own field keys.
private fun pdfFilePrefix(template: ReportTemplate, data: ReportData): String {
    val institute = data.field("ti_name").replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(40)
    val date = if (template.id == "qa") data.field("date_from") else data.field("visit_date")
    val label = template.short?.replace(" ", "-") ?: "Surprise-visit"
    return listOf(label, institute, date).filter { it.isNotBlank() }.joinToString("-")
}
