// type scale. system font (Roboto) is Typography()'s default already, so just tighten a hair.
package bd.sicip.qavisit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

private val base = Typography()

val SicipTypography = base.copy(
    headlineLarge = base.headlineLarge.copy(letterSpacing = (-0.02).em),
    headlineMedium = base.headlineMedium.copy(letterSpacing = (-0.02).em),
    titleLarge = base.titleLarge.copy(letterSpacing = (-0.01).em),
    // section labels: bold + tracked, per DESIGN.md "small caps-ish tracking"
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
)
