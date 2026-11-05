package com.aimdi.xtanative.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.contract_address_copied
import org.jetbrains.compose.resources.stringResource

/**
 * Copies a contract address whole, the thing a reader pastes into a wallet
 * or an explorer, wherever it is shown cut (an X post's text, Stocks' token
 * pages), and [say]s so, as XTA's copyContractAddress does: a tap gives no
 * other sign.
 */
@Composable
fun rememberCopyContract(say: (String) -> Unit): (String) -> Unit {
    val copyText = rememberCopyText()
    // Behind the app's haptic feedback switch, in a sheet's own window too (XTA's selectionHaptic).
    val haptics = rememberSwitchedHaptics()
    val copied = stringResource(Res.string.contract_address_copied)
    return remember(copyText, haptics, copied, say) {
        { address ->
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            copyText(address)
            say(copied)
        }
    }
}
