// displayStatus: derived "availed" display, never a write to the stored status column.
package bd.sicip.qavisit.ui.leaves

import bd.sicip.qavisit.data.db.Leave
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.parse("2026-07-10")

private fun leave(status: String, startDate: String, endDate: String = startDate) = Leave(
    id = "l1",
    officerId = "o1",
    type = "Casual",
    startDate = startDate,
    endDate = endDate,
    status = status,
    updatedAt = "2026-07-01T00:00:00.000Z",
)

class LeavesScreenTest {
    @Test fun scheduled_leave_starting_in_the_future_stays_scheduled() {
        assertEquals("scheduled", displayStatus(leave("scheduled", "2026-07-11"), TODAY))
    }

    @Test fun scheduled_leave_starting_today_shows_availed() {
        assertEquals("availed", displayStatus(leave("scheduled", "2026-07-10"), TODAY))
    }

    @Test fun scheduled_leave_that_started_in_the_past_shows_availed() {
        assertEquals("availed", displayStatus(leave("scheduled", "2026-07-01"), TODAY))
    }

    @Test fun cancelled_leave_stays_cancelled_even_if_it_already_started() {
        assertEquals("cancelled", displayStatus(leave("cancelled", "2026-07-01"), TODAY))
    }

    @Test fun already_availed_leave_passes_through_unchanged() {
        assertEquals("availed", displayStatus(leave("availed", "2026-07-01"), TODAY))
    }
}
