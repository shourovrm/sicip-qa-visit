// share-the-PDF action shared by the hub's "Preview PDF" and the review screen's "PDF" button --
// one place so both go through the exact same render+share path (pdf/BillPrinter.kt's WebView
// print pipeline, same one the TA/DA bill uses; renderBillPdf works on any HTML string).
package bd.sicip.qavisit.ui.reports

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import bd.sicip.qavisit.data.db.Report
import bd.sicip.qavisit.domain.report.ReportData
import bd.sicip.qavisit.domain.report.ReportTemplate
import bd.sicip.qavisit.pdf.ReportMeta
import bd.sicip.qavisit.pdf.buildReportHtml
import bd.sicip.qavisit.pdf.renderBillPdf

// matches the authority declared in AndroidManifest.xml for BillScreen's own FileProvider use.
private const val FILE_PROVIDER_AUTHORITY = "bd.sicip.qavisit.fileprovider"

suspend fun shareReportPdf(context: Context, template: ReportTemplate, report: Report, officerName: String) {
    val meta = ReportMeta(officerName = officerName, status = report.status, submittedAt = report.submittedAt)
    val html = buildReportHtml(template, ReportData.parse(report.data), meta)
    val file = renderBillPdf(context, html, filePrefix = "surprise_report")
    val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share visit report"))
}
