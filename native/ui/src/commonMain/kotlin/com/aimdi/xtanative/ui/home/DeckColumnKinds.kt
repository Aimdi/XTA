package com.aimdi.xtanative.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.DeckAsks
import com.aimdi.xtanative.core.session.DeckColumn
import com.aimdi.xtanative.data.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.cancel
import com.aimdi.xtanative.ui.generated.resources.discover_search_x
import com.aimdi.xtanative.ui.generated.resources.groups_username
import com.aimdi.xtanative.ui.generated.resources.home_deck_add
import com.aimdi.xtanative.ui.generated.resources.home_deck_profile
import com.aimdi.xtanative.ui.search.DiscoverIcons
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the deck's columns are called: the groups and networks Home has, the
 * antennas and Saved's folders by id, and the searches followed. A column
 * whose antenna or folder is gone keeps its id for a name.
 */
internal class ColumnNames(
    val groups: List<FollowGroup>,
    val available: List<StripNetwork>,
    val antennas: Map<String, String>,
    val folders: Map<String, String>,
    val searches: List<String>,
) {
    /** The name of a column read from the device or by a search: its query, its account, its antenna's or folder's name. */
    fun of(column: DeckColumn): String = when (column) {
        is DeckColumn.Search -> column.query
        is DeckColumn.Profile -> "@" + column.screenName
        is DeckColumn.Antenna -> antennas[column.antennaId] ?: column.antennaId
        is DeckColumn.SavedFolder -> folders[column.folderId] ?: column.folderId
        else -> column.id
    }

    /** The searches followed, the antennas and the folders, each as a column Add column offers. */
    fun addable(): List<DeckColumn> =
        searches.map(DeckColumn::Search) + antennas.keys.map(DeckColumn::Antenna) + folders.keys.map(DeckColumn::SavedFolder)
}

/** The deck's names as they are now; Saved's folders are read from the device once asked. */
@Composable
internal fun rememberColumnNames(networks: HomeNetworks = LocalHomeNetworks.current): ColumnNames {
    val graph = LocalGraph.current
    val session by graph.sessions.state.collectAsStateWithLifecycle()
    val available by networks.available.collectAsStateWithLifecycle()
    val antennas by remember(graph) { graph.antennas.antennas }.collectAsStateWithLifecycle(emptyList())
    val library by graph.saved.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.saved.library() }
    return ColumnNames(
        groups = session?.groups.orEmpty(),
        available = available,
        antennas = antennas.associate { it.id to it.name },
        folders = library?.folders.orEmpty().sortedBy { it.position }.associate { it.id to it.name },
        searches = session?.searches.orEmpty().map { it.query },
    )
}

/** The marks of the columns read from the device or by a search. */
internal object DeckMarks {
    fun of(column: DeckColumn): ImageVector? = when (column) {
        is DeckColumn.Search -> Icons.Filled.Search
        is DeckColumn.Profile -> Icons.Filled.Person
        is DeckColumn.Antenna -> DiscoverIcons.Sensors
        is DeckColumn.SavedFolder -> HomeIcons.Bookmark
        else -> null
    }
}

/** A column Add column asks the reader to name: a search of X, or an account. */
internal enum class DeckAsk(val title: StringResource, val field: StringResource, val icon: ImageVector) {
    SEARCH(Res.string.discover_search_x, Res.string.discover_search_x, Icons.Filled.Search),
    PROFILE(Res.string.home_deck_profile, Res.string.groups_username, Icons.Filled.Person),
    ;

    /** The column [text] names, null while it names none. */
    fun column(text: String): DeckColumn? = when (this) {
        SEARCH -> DeckAsks.search(text)
        PROFILE -> DeckAsks.profile(text)
    }
}

/** One of Add column's rows: a column to add, or one to name first. */
internal sealed interface AddChoice {
    val key: String

    data class Column(val column: DeckColumn) : AddChoice {
        override val key: String get() = column.id
    }

    data class Ask(val ask: DeckAsk) : AddChoice {
        override val key: String get() = "ask:" + ask.name
    }
}

/** The search or account typed for a new column; Add column is offered once the text names one. */
@Composable
internal fun AskColumnDialog(ask: DeckAsk, onDismiss: () -> Unit, onAdd: (DeckColumn) -> Unit) {
    var text by remember { mutableStateOf("") }
    val column = ask.column(text)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(ask.title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(ask.field)) },
                singleLine = true,
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { column?.let(onAdd) }, enabled = column != null) { Text(stringResource(Res.string.home_deck_add)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}
