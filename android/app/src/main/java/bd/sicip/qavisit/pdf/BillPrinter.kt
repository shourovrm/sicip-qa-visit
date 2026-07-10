// renders BillHtml's output to a PDF file by driving WebView's own print pipeline (the same
// Chromium print-to-PDF engine "Share > Print" uses) instead of hand-rolled Canvas drawing --
// pagination, table header repeat-on-page-break, and text shaping all come free from the
// browser layout engine. must run on the main thread: WebView only works there.
package bd.sicip.qavisit.pdf

import android.content.Context
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.billLayoutCallback
import android.print.billWriteCallback
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val PAGE_ATTRIBUTES = PrintAttributes.Builder()
    .setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape())
    .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
    .setMinMargins(PrintAttributes.Margins.NO_MARGINS) // margins come from the HTML's @page rule
    .build()

// renders [html] to a fresh PDF file in cacheDir and returns it. suspend wrapper around
// WebView's callback-based print adapter; the WebView itself is thrown away once the PDF
// bytes are written -- nothing here is kept alive past this call.
suspend fun renderBillPdf(context: Context, html: String): File = withContext(Dispatchers.Main) {
    val webView = WebView(context)
    try {
        loadHtml(webView, html)
        val adapter = webView.createPrintDocumentAdapter("tada_bill")
        val file = File(context.cacheDir, "tada_bill_${System.currentTimeMillis()}.pdf")
        layoutAndWrite(adapter, file)
        file
    } finally {
        webView.destroy()
    }
}

private suspend fun loadHtml(webView: WebView, html: String): Unit = suspendCancellableCoroutine { cont ->
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            if (cont.isActive) cont.resume(Unit)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
}

private suspend fun layoutAndWrite(adapter: PrintDocumentAdapter, file: File) {
    suspendCancellableCoroutine<Unit> { cont ->
        adapter.onLayout(
            null,
            PAGE_ATTRIBUTES,
            CancellationSignal(),
            billLayoutCallback(
                onFinished = { _, _ ->
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
                    adapter.onWrite(
                        arrayOf(PageRange.ALL_PAGES),
                        pfd,
                        CancellationSignal(),
                        billWriteCallback(
                            onFinished = {
                                pfd.close()
                                if (cont.isActive) cont.resume(Unit)
                            },
                            onFailed = { error ->
                                pfd.close()
                                if (cont.isActive) cont.resumeWithException(IllegalStateException("bill pdf write failed: $error"))
                            },
                        ),
                    )
                },
                onFailed = { error ->
                    if (cont.isActive) cont.resumeWithException(IllegalStateException("bill pdf layout failed: $error"))
                },
            ),
            null,
        )
    }
}
