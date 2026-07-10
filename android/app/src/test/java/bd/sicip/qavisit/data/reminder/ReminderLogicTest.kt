package bd.sicip.qavisit.data.reminder

import bd.sicip.qavisit.data.db.Visit
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ReminderLogicTest {

    private val today = LocalDate.of(2026, 7, 10)

    private fun visit(
        id: String,
        startDate: String,
        purpose: String = "Inspection",
        institute: String = "ABC College",
        officerId: String = "me",
        status: String = "scheduled",
        deleted: Boolean = false,
    ) = Visit(
        id = id,
        officerId = officerId,
        institute = institute,
        association = "assoc",
        district = "Dhaka",
        purpose = purpose,
        startDate = startDate,
        endDate = startDate,
        status = status,
        deleted = deleted,
        createdAt = "2026-07-01T00:00:00Z",
        updatedAt = "2026-07-01T00:00:00Z",
    )

    @Test fun no_visits_no_lines() {
        assertEquals(emptyList<String>(), reminderLines(emptyList(), today))
    }

    @Test fun no_qualifying_visits_no_lines() {
        val visits = listOf(visit("1", startDate = "2026-07-20"))
        assertEquals(emptyList<String>(), reminderLines(visits, today))
    }

    @Test fun one_today_visit() {
        val visits = listOf(visit("1", startDate = "2026-07-10", purpose = "Audit", institute = "XYZ School"))
        assertEquals(listOf("Today: Audit — XYZ School"), reminderLines(visits, today))
    }

    @Test fun one_tomorrow_visit() {
        val visits = listOf(visit("1", startDate = "2026-07-11", purpose = "Audit", institute = "XYZ School"))
        assertEquals(listOf("Tomorrow: Audit — XYZ School"), reminderLines(visits, today))
    }

    @Test fun many_visits_today_and_tomorrow_are_ordered_today_first() {
        val visits = listOf(
            visit("1", startDate = "2026-07-11", purpose = "Tomorrow purpose", institute = "Inst B"),
            visit("2", startDate = "2026-07-10", purpose = "Today purpose", institute = "Inst A"),
        )
        assertEquals(
            listOf("Today: Today purpose — Inst A", "Tomorrow: Tomorrow purpose — Inst B"),
            reminderLines(visits, today),
        )
    }

    @Test fun ignores_done_status() {
        val visits = listOf(visit("1", startDate = "2026-07-10", status = "done"))
        assertEquals(emptyList<String>(), reminderLines(visits, today))
    }

    @Test fun ignores_deleted() {
        val visits = listOf(visit("1", startDate = "2026-07-10", deleted = true))
        assertEquals(emptyList<String>(), reminderLines(visits, today))
    }

    // caller is expected to have already scoped the query to the officer, but the pure fn
    // itself doesn't filter by officerId -- it re-guards status/deleted only. this documents
    // that scoping is the caller's job (ReminderWorker passes byOfficer(myId) results in).
    @Test fun does_not_filter_officer_itself() {
        val visits = listOf(visit("1", startDate = "2026-07-10", officerId = "someone-else"))
        assertEquals(listOf("Today: Inspection — ABC College"), reminderLines(visits, today))
    }
}
