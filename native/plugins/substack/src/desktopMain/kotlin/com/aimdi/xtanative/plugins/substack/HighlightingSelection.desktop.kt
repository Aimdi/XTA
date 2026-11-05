package com.aimdi.xtanative.plugins.substack

import androidx.compose.foundation.ContextMenuDataProvider
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** The desktop's selection: its right-click menu gains [label] while text is selected, and [speakLabel]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal actual fun HighlightingSelection(
    label: String,
    onHighlight: (String) -> Unit,
    speakLabel: String,
    onSpeak: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    val base = LocalTextContextMenu.current
    val highlight = rememberUpdatedState(onHighlight)
    val speak = rememberUpdatedState(onSpeak)
    val speaks = onSpeak != null
    val menu = remember(base, label, speakLabel, speaks) {
        object : TextContextMenu {
            @Composable
            override fun Area(textManager: TextContextMenu.TextManager, state: ContextMenuState, content: @Composable () -> Unit) {
                ContextMenuDataProvider({
                    val selected = textManager.selectedText.text
                    listOfNotNull(
                        ContextMenuItem(label) { highlight.value(selected) }.takeIf { selected.isNotBlank() },
                        ContextMenuItem(speakLabel) { speak.value?.invoke() }.takeIf { speaks },
                    )
                }) {
                    base.Area(textManager, state, content)
                }
            }
        }
    }
    CompositionLocalProvider(LocalTextContextMenu provides menu) {
        SelectionContainer { content() }
    }
}
