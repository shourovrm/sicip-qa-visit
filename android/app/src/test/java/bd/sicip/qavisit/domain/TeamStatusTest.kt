// Team screen status derivation: on-visit beats on-leave beats in-office.
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TeamStatusTest {

    private val today = LocalDate.of(2026, 7, 10)

    @Test fun no_trip_no_leave_is_in_office() {
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), emptyList(), today))
    }

    @Test fun active_trip_is_on_visit() {
        val trips = listOf(TripFlag(status = "active", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        val status = teamStatus(trips, emptyList(), today)
        assertEquals(TeamStatus.OnVisit(since = "2026-07-08"), status)
    }

    @Test fun finished_trip_is_not_on_visit() {
        val trips = listOf(TripFlag(status = "finished", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips, emptyList(), today))
    }

    @Test fun deleted_active_trip_ignored() {
        val trips = listOf(TripFlag(status = "active", deleted = true, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips, emptyList(), today))
    }

    @Test fun leave_covering_today_is_on_leave() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "availed", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        val status = teamStatus(emptyList(), leaves, today)
        assertEquals(TeamStatus.OnLeave(type = "casual", until = "2026-07-12"), status)
    }

    @Test fun boundary_start_date_is_inclusive() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "scheduled", deleted = false, startDate = "2026-07-10", endDate = "2026-07-15"),
        )
        assertEquals(TeamStatus.OnLeave("casual", "2026-07-15"), teamStatus(emptyList(), leaves, today))
    }

    @Test fun boundary_end_date_is_inclusive() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "scheduled", deleted = false, startDate = "2026-07-01", endDate = "2026-07-10"),
        )
        assertEquals(TeamStatus.OnLeave("casual", "2026-07-10"), teamStatus(emptyList(), leaves, today))
    }

    @Test fun leave_outside_range_is_in_office() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "scheduled", deleted = false, startDate = "2026-07-11", endDate = "2026-07-15"),
        )
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), leaves, today))
    }

    @Test fun cancelled_leave_ignored() {
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "cancelled", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList(), leaves, today))
    }

    @Test fun on_visit_beats_on_leave_when_both() {
        val trips = listOf(TripFlag(status = "active", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        val leaves = listOf(
            LeaveFlag(type = "casual", status = "availed", deleted = false, startDate = "2026-07-09", endDate = "2026-07-12"),
        )
        assertEquals(TeamStatus.OnVisit(since = "2026-07-08"), teamStatus(trips, leaves, today))
    }
}
