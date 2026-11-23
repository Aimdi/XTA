package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
actual fun ListScrollbar(state: LazyListState, modifier: Modifier) = Unit

@Composable
actual fun ColumnScrollbar(state: ScrollState, modifier: Modifier) = Unit
