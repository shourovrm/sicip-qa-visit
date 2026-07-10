// rows-per-page pagination math -- the only piece of BillPdf.kt that's plain arithmetic and
// doesn't touch PdfDocument/Canvas/Paint, so it's the only piece worth a JVM unit test here.
// the actual PDF drawing (letterhead, table, totals, footer) needs a real Canvas and is left
// to a manual/instrumented smoke check, not this suite.
package bd.sicip.qavisit.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class BillLayoutTest {

    @Test fun fits_as_many_whole_rows_as_the_space_allows() {
        assertEquals(40, rowsPerPage(800f, 200f, 15f))
    }

    @Test fun floors_a_partial_row() {
        assertEquals(3, rowsPerPage(100f, 50f, 16f)) // (100-50)/16 = 3.125 -> 3
    }

    @Test fun never_returns_less_than_one() {
        // header alone already overruns the margin -- still must place at least one row,
        // or the caller's "while (idx < rows.size)" loop never terminates.
        assertEquals(1, rowsPerPage(50f, 80f, 15f))
    }

    @Test fun exact_multiple_fits_precisely() {
        assertEquals(10, rowsPerPage(150f, 0f, 15f))
    }
}
