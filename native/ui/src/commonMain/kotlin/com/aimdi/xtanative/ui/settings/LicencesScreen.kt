package com.aimdi.xtanative.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aimdi.xtanative.ui.appName
import com.aimdi.xtanative.ui.common.Hairline
import com.aimdi.xtanative.ui.common.LocalGraph
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.chrome_licences_count
import com.aimdi.xtanative.ui.generated.resources.chrome_licences_legalese
import com.aimdi.xtanative.ui.generated.resources.settings_licenses
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Settings › App info › Licences as XTA's licence page (showLicensePage):
 * the app's name, version and licence, then every library it ships with how
 * many licences each comes with; one opens with [onOpen] by its module.
 */
@Composable
fun LicencesScreen(onOpen: (module: String) -> Unit, onBack: () -> Unit) {
    val list = remember { LicenceList.shipped }
    val version = LocalGraph.current.version.ifEmpty { "dev" }
    SettingsPage(stringResource(Res.string.settings_licenses), onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            item(key = "about") { AboutHeader(version) }
            items(list.libraries, key = { it.module }) { library ->
                LibraryRow(library) { onOpen(library.module) }
            }
        }
    }
}

/** The app as XTA's page heads its list: its name, its version and its licence. */
@Composable
private fun AboutHeader(version: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp)) {
        Text(appName(), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(version, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        Text(
            stringResource(Res.string.chrome_licences_legalese),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}

@Composable
private fun LibraryRow(library: ShippedLibrary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(library.name, style = MaterialTheme.typography.bodyLarge)
        Text(
            pluralStringResource(Res.plurals.chrome_licences_count, library.texts.size, library.texts.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * One library's licences as XTA's page shows a package's: each text whole,
 * the next after a clover, under what the library is ([module], its version
 * and the licences its POM names).
 */
@Composable
fun LicenceScreen(module: String, onBack: () -> Unit) {
    val list = remember { LicenceList.shipped }
    val library = remember(module) { list.find(module) }
    val paragraphs = remember(library) { library?.let { LicenceText.paragraphs(list.textsOf(it)) }.orEmpty() }
    SettingsPage(library?.name ?: module, onBack) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            library?.let { item(key = "about") { LibraryHeader(it) } }
            items(paragraphs.size) { index ->
                when (val paragraph = paragraphs[index]) {
                    LicenceText.Paragraph.Between -> Text(
                        CLOVER,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    )
                    is LicenceText.Paragraph.Words -> Text(
                        paragraph.text,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryHeader(library: ShippedLibrary) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(listOf(library.module, library.version).filter { it.isNotEmpty() }.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (library.licences.isNotEmpty()) Text(library.licences.joinToString(", "), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        library.url?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp)) }
    }
    Hairline()
}

/** Flutter's licence page puts a four-leaf clover between one licence and the next. */
private const val CLOVER = "🍀"
