// which TMS banners show, in order. update banner (Home) always comes before these.
package bd.sicip.qavisit.ui.common

import bd.sicip.qavisit.data.tms.TmsAuthState

const val TMS_LINK_BROKEN_TEXT = "TMS link broken: TMS data can't be fetched right now. The admin is informed."

class TmsBannerState(val signInFailure: String?, val adminAlert: String?, val linkBroken: String?) {
    val any: Boolean get() = signInFailure != null || adminAlert != null || linkBroken != null
}

// sign-in failed first; then the admin's detailed alert, or for non-admins the short broken-link
// notice (only while logged in to TMS).
fun tmsBannerState(authState: TmsAuthState, isAdmin: Boolean, adminAlertText: String?, linkBroken: Boolean): TmsBannerState {
    val failure = (authState as? TmsAuthState.Failed)?.message
    return TmsBannerState(
        signInFailure = failure?.let { "TMS sign-in failed: $it" },
        adminAlert = if (isAdmin) adminAlertText else null,
        linkBroken = if (!isAdmin && authState is TmsAuthState.LoggedIn && linkBroken) TMS_LINK_BROKEN_TEXT else null,
    )
}
