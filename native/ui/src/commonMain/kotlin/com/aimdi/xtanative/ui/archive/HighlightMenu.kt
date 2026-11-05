package com.aimdi.xtanative.ui.archive

import androidx.compose.runtime.Composable

/**
 * The menu a selection in [content]'s text opens, with [label] (Highlight and
 * note) after the platform's own entries, Copy and Select all among them, as
 * XTA's archive adds it to the selection's toolbar; [onHighlight] runs it, and
 * a null one leaves the platform's entries alone.
 */
@Composable
internal expect fun HighlightMenu(label: String, onHighlight: (() -> Unit)?, content: @Composable () -> Unit)
