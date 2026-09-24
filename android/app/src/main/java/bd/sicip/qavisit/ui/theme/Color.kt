// raw color tokens from DESIGN.md. no logic here, just values.
package bd.sicip.qavisit.ui.theme

import androidx.compose.ui.graphics.Color

// -- light --
val LightPrimary = Color(0xFF232063)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF3A377F)
val LightOnPrimaryContainer = Color(0xFFE8E8FB)
val LightTertiary = Color(0xFFE8690A)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightBackground = Color(0xFFEFF0F6)
val LightSurface = Color(0xFFFFFFFF)
val LightOutline = Color(0xFFE2E3EE)
val LightInk = Color(0xFF20223A) // onSurface / onBackground
val LightMuted = Color(0xFF5A5D79) // onSurfaceVariant

// -- dark (seed-derived per DESIGN.md; navy deepens, orange lifts) --
val DarkBackground = Color(0xFF14151F)
val DarkSurface = Color(0xFF1E2030)
val DarkPrimaryContainer = Color(0xFF312E6E) // "hero" card
val DarkPrimary = Color(0xFFC3C1F5)
val DarkOnPrimary = Color(0xFF2A2760)
val DarkOnPrimaryContainer = Color(0xFFE3E1FF)
val DarkTertiary = Color(0xFFF5883A)
val DarkOnTertiary = Color(0xFF3A1B00)
val DarkOutline = Color(0xFF44465C)
val DarkInk = Color(0xFFE4E2F0)
val DarkMuted = Color(0xFFB4B6CE)
val DarkNavBar = Color(0xFF1A1C2B) // bottom NavigationBar container, distinct from card surface

// -- status pills: bg/ink pairs, derived state only, no manual controls --
data class StatusPair(val bg: Color, val ink: Color)

object LightStatus {
    val onVisit = StatusPair(Color(0xFFE3E6FF), Color(0xFF2B2F7E))
    val office = StatusPair(Color(0xFFE6E7EE), Color(0xFF4C4F66))
    val success = StatusPair(Color(0xFFDDF2E2), Color(0xFF1C6B38))
}

// ponytail: DESIGN.md gives no explicit dark status colors, only light ones.
// derived by flipping bg/ink weight (light's ink -> dark's bg, tinted light -> dark's ink).
// revisit if design hands over exact dark pill values.
object DarkStatus {
    val onVisit = StatusPair(Color(0xFF2B2F7E), Color(0xFFC7CBFF))
    val office = StatusPair(Color(0xFF4C4F66), Color(0xFFD5D6E6))
    val success = StatusPair(Color(0xFF1C6B38), Color(0xFFB8F0C8))
}

// -- report answer/tone colors: checklist's 4 segmented buttons, a `choice` field's chosen
// option, and the PDF's answer labels (pdf/ReportHtml.kt + web/src/lib/reporthtml.js's
// TONE_COLOR MUST match these light values exactly -- three independent copies of the same
// four hex codes, by necessity: neither the android nor the web file can import from the other).
// tone ids come straight from shared/report-templates/surprise-v1.json's answers/options.
data class ToneColors(val yes: Color, val no: Color, val partial: Color, val na: Color)

object LightTone {
    val colors = ToneColors(
        yes = Color(0xFF1C6B38),
        no = Color(0xFFB3261E),
        partial = Color(0xFF8A4600),
        na = Color(0xFF4C4F66),
    )
}

// lifted the same way DarkTertiary lifts from LightTertiary -- brighter fill needed for
// contrast against the dark scaffold. a chosen answer button is a solid filled chip with white
// text in both themes (not a bg/ink status-pill pair), so these are plain solid colors.
object DarkTone {
    val colors = ToneColors(
        yes = Color(0xFF4CAF6E),
        no = Color(0xFFE5564A),
        partial = Color(0xFFC97A2E),
        na = Color(0xFF7A7DA0),
    )
}

// tone id ("yes"/"no"/"partial"/"na", from the template's answers/options) -> the current
// theme's color for it. an unrecognised id (future template addition) falls back to `na`'s
// muted color rather than crashing -- same "ignore, don't crash" spirit as the PDF's TONE_COLOR
// fallback.
fun ToneColors.forToneId(toneId: String): Color = when (toneId) {
    "yes" -> yes
    "no" -> no
    "partial" -> partial
    else -> na
}
