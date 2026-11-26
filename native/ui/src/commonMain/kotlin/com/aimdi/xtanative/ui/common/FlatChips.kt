package com.aimdi.xtanative.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableChipColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.theme.edgeColor

/**
 * A filter chip's colours on the app's flat pages: no fill either way, its
 * label and icons grey until it is selected, then in the accent.
 */
@Composable
internal fun flatChipColors(): SelectableChipColors {
    val scheme = MaterialTheme.colorScheme
    return FilterChipDefaults.filterChipColors(
        containerColor = Color.Transparent,
        labelColor = scheme.onSurfaceVariant,
        iconColor = scheme.onSurfaceVariant,
        selectedContainerColor = Color.Transparent,
        selectedLabelColor = scheme.primary,
        selectedLeadingIconColor = scheme.primary,
        selectedTrailingIconColor = scheme.primary,
    )
}

/** Its outline: a hairline that reads on the page ([edgeColor]), of the accent while it is [selected]. */
@Composable
internal fun flatChipBorder(selected: Boolean, enabled: Boolean = true): BorderStroke = FilterChipDefaults.filterChipBorder(
    enabled = enabled,
    selected = selected,
    borderColor = edgeColor(),
    selectedBorderColor = MaterialTheme.colorScheme.primary,
    borderWidth = 1.dp,
    selectedBorderWidth = 1.dp,
)

/**
 * Material's FilterChip as the app draws every row of filters: an outlined
 * word on the page ([flatChipColors], [flatChipBorder]), never a filled pill.
 */
@Composable
internal fun FlatFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) = FilterChip(
    selected = selected,
    onClick = onClick,
    label = label,
    modifier = modifier,
    enabled = enabled,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    colors = flatChipColors(),
    border = flatChipBorder(selected, enabled),
)
