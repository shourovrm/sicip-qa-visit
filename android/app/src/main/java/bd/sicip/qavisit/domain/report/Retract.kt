// submitted report -> back to draft: the officer may retract within RETRACT_DAYS of submitting;
// after that only an admin reopens it (web). client-side rule, same as "submitted = read-only".
package bd.sicip.qavisit.domain.report

import java.time.Duration
import java.time.Instant

const val RETRACT_DAYS = 3L

fun canRetract(status: String, submittedAt: String?, now: Instant): Boolean {
    if (status != "submitted" || submittedAt.isNullOrBlank()) return false
    val submitted = runCatching { Instant.parse(submittedAt) }.getOrNull() ?: return false
    return now.isBefore(submitted.plus(Duration.ofDays(RETRACT_DAYS)))
}
