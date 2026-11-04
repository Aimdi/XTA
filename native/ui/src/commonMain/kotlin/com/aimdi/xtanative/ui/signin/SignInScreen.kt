package com.aimdi.xtanative.ui.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.settings_no
import com.aimdi.xtanative.ui.generated.resources.settings_save
import com.aimdi.xtanative.ui.generated.resources.settings_yes
import com.aimdi.xtanative.ui.generated.resources.signin_add_account
import com.aimdi.xtanative.ui.generated.resources.signin_cookie_help
import com.aimdi.xtanative.ui.generated.resources.signin_cookies
import com.aimdi.xtanative.ui.generated.resources.signin_import_text
import com.aimdi.xtanative.ui.generated.resources.signin_import_title
import com.aimdi.xtanative.ui.generated.resources.signin_open_in_browser
import com.aimdi.xtanative.ui.generated.resources.signin_username
import com.aimdi.xtanative.ui.links.LocalLinks
import com.aimdi.xtanative.ui.settings.SettingsPage
import com.aimdi.xtanative.ui.subscriptions.GroupIcons
import org.jetbrains.compose.resources.stringResource

/**
 * The page that adds an X account (XTA's xLoginScreen): X's own login in
 * Android's web view, or the session's cookies on the desktop, which has no
 * web view. The account is kept as the session keeps every account; after
 * the web view's sign-in XTA offers to import the account's follows.
 */
@Composable
fun SignInScreen(onBack: () -> Unit, onImportFollowing: () -> Unit) {
    val graph = LocalGraph.current
    var signedIn by rememberSaveable { mutableStateOf<String?>(null) }
    val name = signedIn
    if (name != null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        ImportOffer(name, onNo = onBack, onYes = onImportFollowing)
        return
    }
    XSignInPage(onBack) { account ->
        graph.keep { graph.sessions.update { XSignIn.signedIn(it, account) } }
        if (signInOffersImport) signedIn = account.screenName.orEmpty() else onBack()
    }
}

/** XTA's question once the web view has signed in: import the follows of [screenName] as subscriptions here? */
@Composable
private fun ImportOffer(screenName: String, onNo: () -> Unit, onYes: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text(stringResource(Res.string.signin_import_title)) },
        text = { Text(stringResource(Res.string.signin_import_text, screenName)) },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(Res.string.settings_yes)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(Res.string.settings_no)) } },
    )
}

/**
 * The platform's way to sign in: X's login page in Android's web view, or
 * [CookieSignInPage] on the desktop. [onSignedIn] gets the account once X
 * has signed the reader in.
 */
@Composable
internal expect fun XSignInPage(onBack: () -> Unit, onSignedIn: (XAccount) -> Unit)

/** Whether the sign-in offers to import the account's follows: XTA's web view does, its desktop page does not. */
internal expect val signInOffersImport: Boolean

/**
 * Signs X out of the web view, as XTA clears its cookies once an account is
 * removed: the next sign-in then shows X's login form instead of handing
 * back the session the reader just took off this device.
 */
internal expect fun forgetWebSessions()

/**
 * The desktop's sign-in (XTA's XCookieLogin): how to copy the session's two
 * cookies from a browser, the handle, the cookies, and Save once both are
 * there. The cookies are never kept in the screen's saved state.
 */
@Composable
internal fun CookieSignInPage(onBack: () -> Unit, onSignedIn: (XAccount) -> Unit) {
    val links = LocalLinks.current
    var typed by rememberSaveable { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    val cookies = XSignIn.pastedCookies(pasted)
    val screenName = XSignIn.screenNameOf(typed)
    SettingsPage(stringResource(Res.string.signin_add_account), onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text(stringResource(Res.string.signin_cookie_help), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { links?.openOutside(XSignIn.BROWSER_LOGIN) }) {
                Icon(GroupIcons.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.signin_open_in_browser))
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = typed,
                onValueChange = { typed = it },
                label = { Text(stringResource(Res.string.signin_username)) },
                prefix = { Text("@") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = pasted,
                onValueChange = { pasted = it },
                label = { Text(stringResource(Res.string.signin_cookies)) },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { cookies?.let { XSignIn.account(it, screenName) }?.let(onSignedIn) },
                enabled = screenName.isNotEmpty() && cookies != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.settings_save)) }
        }
    }
}
