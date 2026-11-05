package com.aimdi.xtanative.ui.signin

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.signin_import_backup
import com.aimdi.xtanative.ui.generated.resources.signin_login
import com.aimdi.xtanative.ui.generated.resources.signin_not_logged_in
import com.aimdi.xtanative.ui.generated.resources.signin_why_needed
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's "You are not logged in" (main.dart's checkForAccounts): why X needs
 * an account, and Cancel, which leaves the app as it is (X as a guest, the
 * plugins), Import backup and Login. Each closes it.
 */
@Composable
fun NotLoggedInDialog(onImportBackup: () -> Unit, onLogin: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("⚠️ " + stringResource(Res.string.signin_not_logged_in)) },
        text = { Text(stringResource(Res.string.signin_why_needed)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
        confirmButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    onImportBackup()
                }) { Text(stringResource(Res.string.signin_import_backup)) }
                TextButton(onClick = {
                    onDismiss()
                    onLogin()
                }) { Text(stringResource(Res.string.signin_login)) }
            }
        },
    )
}
