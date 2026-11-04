package com.aimdi.xtanative.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.x.model.XTrendPlace
import com.aimdi.xtanative.core.x.model.XTrendPlaces
import com.aimdi.xtanative.ui.common.LoadingRow
import com.aimdi.xtanative.ui.common.ProblemView
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.discover_country
import org.jetbrains.compose.resources.stringResource

/**
 * XTA's place picker: the places X has trends for, by country; picking one
 * keeps it as a tab and shows its trends.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlacesSheet(vm: TrendsViewModel, places: XTrendPlaces, onDismiss: () -> Unit) {
    val picker by vm.places.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadPlaces() }
    var open by remember { mutableStateOf(emptySet<String>()) }
    val pick = { place: XTrendPlace ->
        vm.add(place)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        val problem = picker.problem
        when {
            picker.loading -> LoadingRow()
            problem != null -> ProblemView(problem, vm::loadPlaces)
            else -> LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                picker.rows.forEach { row ->
                    when (row) {
                        is PlaceRow.Place -> item(key = row.key) { PlaceItem(row.place, TrendPlacesLogic.isShown(places, row.place)) { pick(row.place) } }
                        is PlaceRow.Country -> {
                            val expanded = row.name in open
                            item(key = row.key) {
                                CountryItem(row.name, expanded) { open = if (expanded) open - row.name else open + row.name }
                            }
                            if (expanded) {
                                items(row.places, key = { row.key + "-" + it.woeid }) { place ->
                                    PlaceItem(place, TrendPlacesLogic.isShown(places, place), indent = true) { pick(place) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A country, opening onto its places. */
@Composable
private fun CountryItem(name: String, expanded: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Icon(if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
    }
}

/** A place to pick, marked when its trends are the ones shown; a country says so under its name. */
@Composable
private fun PlaceItem(place: XTrendPlace, shown: Boolean, indent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 16.dp else 0.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(if (shown) colors.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(place.name, color = if (shown) colors.onPrimary else colors.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (place.isCountry) {
            Text(
                stringResource(Res.string.discover_country),
                style = MaterialTheme.typography.bodySmall,
                color = if (shown) colors.onPrimary else colors.onSurfaceVariant,
            )
        }
    }
}
