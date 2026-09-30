package bd.sicip.qavisit.domain.report

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RetractTest {
    private val submittedAt = "2026-09-27T10:00:00Z"

    @Test fun within_three_days_can_retract() {
        assertTrue(canRetract("submitted", submittedAt, Instant.parse("2026-09-30T09:59:59Z")))
    }

    @Test fun after_three_days_cannot_retract() {
        assertFalse(canRetract("submitted", submittedAt, Instant.parse("2026-09-30T10:00:01Z")))
    }

    @Test fun draft_cannot_retract() {
        assertFalse(canRetract("draft", null, Instant.parse("2026-09-28T00:00:00Z")))
    }

    @Test fun submitted_without_timestamp_cannot_retract() {
        assertFalse(canRetract("submitted", null, Instant.parse("2026-09-28T00:00:00Z")))
    }
}
