package bd.sicip.qavisit.data.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderSchedulerTest {

    @Test fun now_before_target_same_day() {
        val now = LocalDateTime.of(2026, 7, 10, 6, 0)
        assertEquals(Duration.ofHours(1).plusMinutes(30), initialDelayTo(7, 30, now))
    }

    @Test fun now_after_target_rolls_to_tomorrow() {
        val now = LocalDateTime.of(2026, 7, 10, 9, 0)
        assertEquals(Duration.ofHours(22).plusMinutes(30), initialDelayTo(7, 30, now))
    }

    @Test fun now_exactly_at_target_rolls_to_tomorrow() {
        val now = LocalDateTime.of(2026, 7, 10, 7, 30)
        assertEquals(Duration.ofHours(24), initialDelayTo(7, 30, now))
    }

    @Test fun midnight_wrap_late_night_now_targets_next_morning() {
        val now = LocalDateTime.of(2026, 7, 10, 23, 50)
        // 10min to midnight + 7h30m into the 11th
        assertEquals(Duration.ofHours(7).plusMinutes(40), initialDelayTo(7, 30, now))
    }

    @Test fun midnight_wrap_just_after_midnight_still_same_day_target() {
        val now = LocalDateTime.of(2026, 7, 11, 0, 5)
        assertEquals(Duration.ofHours(7).plusMinutes(25), initialDelayTo(7, 30, now))
    }
}
