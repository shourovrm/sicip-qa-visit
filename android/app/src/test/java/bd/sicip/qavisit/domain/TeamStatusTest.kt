// Team screen status derivation: on-visit beats on-leave beats in-office.
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamStatusTest {

    @Test fun no_trip_no_leave_is_in_office() {
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), emptyList()))
    }

    @Test fun active_trip_is_on_visit() {
        val trips = listOf(TripFlag(status = "active", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        val status = teamStatus(trips, emptyList())
        assertEquals(TeamStatus.OnVisit(since = "2026-07-08"), status)
    }

    @Test fun finished_trip_is_not_on_visit() {
        val trips = listOf(TripFlag(status = "finished", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips, emptyList()))
    }

    @Test fun deleted_active_trip_ignored() {
        val trips = listOf(TripFlag(status = "active", deleted = true, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips, emptyList()))
    }

    @Test fun scheduled_leave_today_is_in_office() {
        // scheduled (not yet started) leave, even one covering today, no longer counts.
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "scheduled", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), leaves))
    }

    @Test fun started_leave_is_on_leave() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "started", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        val status = teamStatus(emptyList(), leaves)
        assertEquals(TeamStatus.OnLeave(type = "casual", until = "2026-07-12"), status)
    }

    @Test fun deleted_started_leave_ignored() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "started", deleted = true, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), leaves))
    }

    @Test fun cancelled_leave_ignored() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "cancelled", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), leaves))
    }

    @Test fun on_visit_beats_on_leave_when_both() {
        val trips = listOf(TripFlag(status = "active", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "started", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.OnVisit(since = "2026-07-08"), teamStatus(trips, leaves))
    }
}
