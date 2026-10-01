// surprise v2 N "Major findings" box: same cases as web/src/lib/findingsbox.test.js
package bd.sicip.qavisit.domain.report

import org.junit.Assert.assertEquals
import org.junit.Test

class FindingsBoxTest {
    private val block = ReportBlock.Findings(key = "findings", boxKey = "findingsText", tickedKey = "findingsTicked")
    private val candidates = listOf(RemarkLine("A ok.", false), RemarkLine("B bad.", true), RemarkLine("C ok.", false))

    @Test
    fun `prints the box lines, falling back to the old picked list`() {
        assertEquals(listOf("One", "Two"), findingLines(block, ReportData.EMPTY.withFindingsBox(block, " One \n\nTwo ")))
        val emptyBox = ReportData.EMPTY.withFindings(listOf(Finding("", "old"))).withFindingsBox(block, "")
        assertEquals(emptyList<String>(), findingLines(block, emptyBox))
        val old = ReportData.EMPTY.withFindings(listOf(Finding("", "old"), Finding("", " ")))
        assertEquals(listOf("old"), findingLines(block, old))
        assertEquals(emptyList<String>(), findingLines(block, ReportData.EMPTY))
    }

    @Test
    fun `an old picked list becomes the box on open and the old data stays`() {
        val old = ReportData.EMPTY.withFindings(listOf(Finding("x", "First"), Finding("", "Second")))
        val opened = withFindingsBoxOpened(block, old)
        assertEquals("First\nSecond", opened.findingsBox(block))
        assertEquals(2, opened.findings().size)
        assertEquals(opened, withFindingsBoxOpened(block, opened)) // already a box
        assertEquals(null, withFindingsBoxOpened(block, ReportData.EMPTY).findingsBox(block))
    }

    @Test
    fun `ticking keeps every line in place, select all and none`() {
        var data = withTicked(block, ReportData.EMPTY, listOf("C ok."))
        assertEquals(listOf("C ok."), tickedLines(block, data, candidates))
        data = withTicked(block, data, candidates.map { it.text })
        assertEquals(listOf("A ok.", "B bad.", "C ok."), tickedLines(block, data, candidates))
        data = withTicked(block, data, emptyList())
        assertEquals(emptyList<String>(), tickedLines(block, data, candidates))
    }

    @Test
    fun `a ticked line no longer written does not count`() {
        val data = withTicked(block, ReportData.EMPTY, listOf("gone", "B bad."))
        assertEquals(listOf("B bad."), tickedLines(block, data, candidates))
    }

    @Test
    fun `selected lines go into the box in report order, one per line`() {
        val data = withTicked(block, ReportData.EMPTY, listOf("C ok.", "A ok."))
        assertEquals("A ok.\nC ok.", selectedFindingsText(block, data, candidates))
    }
}
