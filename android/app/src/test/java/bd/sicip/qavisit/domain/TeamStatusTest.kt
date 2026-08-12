// Team screen status derivation: on-visit beats in-office.
package bd.sicip.qavisit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamStatusTest {

    @Test fun no_trip_is_in_office() {
        assertEquals(TeamStatus.InOffice, teamStatus(emptyList()))
    }

    @Test fun active_trip_is_on_visit() {
        val trips = listOf(TripFlag(status = "active", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        val status = teamStatus(trips)
        assertEquals(TeamStatus.OnVisit(since = "2026-07-08"), status)
    }

    @Test fun finished_trip_is_not_on_visit() {
        val trips = listOf(TripFlag(status = "finished", deleted = false, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips))
    }

    @Test fun deleted_active_trip_ignored() {
        val trips = listOf(TripFlag(status = "active", deleted = true, startedAt = "2026-07-08T09:00:00Z"))
        assertEquals(TeamStatus.InOffice, teamStatus(trips))
    }
}
