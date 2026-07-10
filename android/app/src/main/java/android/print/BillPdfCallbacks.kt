// bd.sicip.qavisit.pdf can't subclass PrintDocumentAdapter.LayoutResultCallback/
// WriteResultCallback directly -- both have a no-arg constructor that's package-private in the
// public android.jar stub (only the system print spooler is meant to construct them; confirmed
// via `javap -p` against android-33/34/35 android.jar -- same across every SDK level checked).
// this file lives in package android.print on purpose: Java/Kotlin package-private access is a
// compile-time check against the *declared* package name, and our own subclasses' implicit
// super() call is legal from here. see pdf/BillPrinter.kt for the only caller.
package android.print

// wraps our own callback into the system's abstract type so BillPrinter.kt (package
// bd.sicip.qavisit.pdf, which cannot construct these directly) can drive
// PrintDocumentAdapter.onLayout()/onWrite() by hand instead of going through the system print
// dialog.
fun billLayoutCallback(
    onFinished: (PrintDocumentInfo, Boolean) -> Unit,
    onFailed: (CharSequence?) -> Unit,
): PrintDocumentAdapter.LayoutResultCallback = object : PrintDocumentAdapter.LayoutResultCallback() {
    override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) = onFinished(info, changed)
    override fun onLayoutFailed(error: CharSequence?) = onFailed(error)
}

fun billWriteCallback(
    onFinished: (Array<out PageRange>) -> Unit,
    onFailed: (CharSequence?) -> Unit,
): PrintDocumentAdapter.WriteResultCallback = object : PrintDocumentAdapter.WriteResultCallback() {
    override fun onWriteFinished(pages: Array<out PageRange>) = onFinished(pages)
    override fun onWriteFailed(error: CharSequence?) = onFailed(error)
}
