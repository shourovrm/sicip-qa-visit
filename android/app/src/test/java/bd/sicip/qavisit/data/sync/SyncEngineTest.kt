// pure decision logic from SyncEngine.kt -- no Room/network/Android involved, so no fakes needed.
package bd.sicip.qavisit.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {
    @Test
    fun `dirty local row blocks the incoming remote row`() {
        assertFalse(shouldApplyRemote(localDirty = true))
    }

    @Test
    fun `clean local row lets the incoming remote row apply`() {
        assertTrue(shouldApplyRemote(localDirty = false))
    }

    @Test
    fun `watermark advances to the newest candidate`() {
        val result = advanceWatermark(
            current = "2024-01-01T00:00:00Z",
            updatedAts = listOf("2024-01-02T00:00:00Z", "2024-01-03T00:00:00Z"),
        )
        assertEquals("2024-01-03T00:00:00Z", result)
    }

    @Test
    fun `watermark stays put when current is already newest`() {
        val result = advanceWatermark(
            current = "2024-01-05T00:00:00Z",
            updatedAts = listOf("2024-01-01T00:00:00Z", "2024-01-02T00:00:00Z"),
        )
        assertEquals("2024-01-05T00:00:00Z", result)
    }

    @Test
    fun `watermark handles equal timestamps`() {
        val result = advanceWatermark(
            current = "2024-01-01T00:00:00Z",
            updatedAts = listOf("2024-01-01T00:00:00Z"),
        )
        assertEquals("2024-01-01T00:00:00Z", result)
    }

    @Test
    fun `watermark with no candidates keeps current`() {
        val result = advanceWatermark(current = "2024-01-01T00:00:00Z", updatedAts = emptyList())
        assertEquals("2024-01-01T00:00:00Z", result)
    }
}
