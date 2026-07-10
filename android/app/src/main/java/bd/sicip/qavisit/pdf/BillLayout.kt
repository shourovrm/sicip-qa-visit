// pure pagination math for the bill PDF table. no android imports on purpose -- this file's
// only job is "how many rows fit before the bottom margin", which is plain arithmetic and
// belongs on the JVM unit test path, not the PdfDocument/Canvas smoke-test path (see BillPdf.kt).
package bd.sicip.qavisit.pdf

// how many rowHeight-tall rows fit between cursorY and bottomLimit. floors a partial row
// (a row that would spill past the margin waits for the next page) but always returns at
// least 1 -- otherwise a page whose header alone overruns the margin would paginate forever.
fun rowsPerPage(bottomLimit: Float, cursorY: Float, rowHeight: Float): Int =
    ((bottomLimit - cursorY) / rowHeight).toInt().coerceAtLeast(1)
