package com.aimdi.xtanative.ui.signin

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.core.x.XAccount

/** The desktop has no web view: the session's cookies come from the reader's browser, as on XTA's desktop. */
@Composable
internal actual fun XSignInPage(onBack: () -> Unit, onSignedIn: (XAccount) -> Unit) = CookieSignInPage(onBack, onSignedIn)

internal actual val signInOffersImport: Boolean = false

/** The desktop keeps no web view cookies to forget. */
internal actual fun forgetWebSessions() = Unit
