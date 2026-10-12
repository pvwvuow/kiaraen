package com.kiaraen.app.ui.collections

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiaraen.app.data.local.CollectionWithSize
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.ui.common.LocalAppContainer

/**
 * Collections (مجموعه‌ها): default shows the first 3, «نمایش همه» expands.
 * Inside a collection, words are grouped A/B/C (the level bands).
 */
@Composable
fun CollectionsScreen(onOpenQuiz: (String) -> Unit) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    var expanded by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }

    // one observed query for names + counts (was one Flow per card)
    val allFlow = remember { contentDao.collectionsWithSize() }
    val all by allFlow.collectAsState(initial = emptyList<CollectionWithSize>())
    val visible = if (expanded) all else all.take(3)

    // system back closes the detail view instead of leaving the tab
    BackHandler(enabled = selected != null) { selected = null }

    val colId = selected
    if (colId == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("مجموعه‌ها", style = MaterialTheme.typography.titleLarge) }
            items(visible, key = { it.collection.id }) { row ->
                Card(onClick = { selected = row.collection.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(row.collection.title, style = MaterialTheme.typography.titleMedium)
                        Text("${row.size} واژه", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (!expanded && all.size > 3) {
                item {
                    Button(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("نمایش همه (${all.size})")
                    }
                }
            }
        }
    } else {
        // the list may still be loading (or a content refresh replaced it)
        val col = all.firstOrNull { it.collection.id == colId }?.collection
        var words by remember(colId) { mutableStateOf<List<WordEntity>>(emptyList()) }
        LaunchedEffect(colId) {
            // JOIN query: an IN (:ids) list of a big collection can exceed
            // SQLite's 999-variable limit on Android 8-10 and crash
            words = runCatching { contentDao.wordsOfCollectionOnce(colId) }.getOrDefault(emptyList())
        }
        // grouped once per word list, not on every recomposition/scroll
        val byLevel = remember(words) { words.groupBy { it.levelId }.entries.sortedBy { it.key } }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(col?.title.orEmpty(), style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                byLevel.forEach { (lv, ws) ->
                    item(key = "h_$lv") {
                        Text(
                            "سطح $lv · ${ws.size} واژه",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(ws, key = { it.id }, contentType = { "word" }) { w ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            Text("${w.word} — ${w.meaningFa ?: ""}")
                        }
                    }
                }
            }
            Button(
                onClick = { onOpenQuiz("collection:$colId") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("آزمون این مجموعه") }
        }
    }
}
