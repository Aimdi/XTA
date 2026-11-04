package com.aimdi.xtanative.ui.saved

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.LocalPosts
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.back
import com.aimdi.xtanative.ui.generated.resources.saved_are_you_sure
import com.aimdi.xtanative.ui.generated.resources.saved_delete
import com.aimdi.xtanative.ui.generated.resources.saved_empty_notes
import com.aimdi.xtanative.ui.generated.resources.saved_note_delete_confirm
import com.aimdi.xtanative.ui.generated.resources.saved_note_thread
import com.aimdi.xtanative.ui.post.PostActions
import org.jetbrains.compose.resources.stringResource

/**
 * A note and the replies written under it (local_note_thread.dart), each
 * reply stepped in under the note it answers. It stays on this device:
 * opening it reads nothing from any network.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteThreadScreen(rootId: String, actions: PostActions, onBack: () -> Unit) {
    val graph = LocalGraph.current
    val notes by graph.localPosts.state.collectAsStateWithLifecycle()
    LaunchedEffect(graph) { graph.localPosts.notes() }
    var editing by remember { mutableStateOf<NoteTarget.Note?>(null) }
    var deleting by remember { mutableStateOf<LocalPost?>(null) }
    val thread = notes?.let { LocalPosts.thread(it, rootId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.saved_note_thread)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            when {
                thread == null -> Unit
                thread.isEmpty() -> Message(stringResource(Res.string.saved_empty_notes))
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)) {
                    items(thread, key = { it.post.id }) { (post, depth) ->
                        Box(Modifier.padding(start = NoteRules.indent(depth).dp)) {
                            NoteTile(
                                note = post,
                                actions = actions,
                                replies = LocalPosts.directReplyCount(notes.orEmpty(), post.id),
                                onEdit = { editing = NoteTarget.Note(editing = post) },
                                onDelete = { deleting = post },
                                onReply = { editing = NoteTarget.Note(replyTo = post) },
                            )
                        }
                    }
                }
            }
        }
    }
    editing?.let { target -> NoteEditorDialog(target, onDismiss = { editing = null }) }
    deleting?.let { post ->
        ConfirmDialog(
            title = stringResource(Res.string.saved_are_you_sure),
            text = stringResource(Res.string.saved_note_delete_confirm),
            confirm = stringResource(Res.string.saved_delete),
            onDismiss = { deleting = null },
        ) {
            deleting = null
            graph.keep { graph.localPosts.delete(post.id) }
            if (post.id == rootId) onBack()
        }
    }
}
