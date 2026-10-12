package com.kiaraen.app.ui.collections

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kiaraen.app.ui.common.LocalAppContainer

/**
 * Collections (مجموعه‌ها): default shows the first 3, «نمایش همه» expands.
 * Inside a collection, words are grouped A/B/C (the level bands).
 */
@Composable
fun CollectionsScreen(onOpenQuiz: (String) -> Unit) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    var expanded by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Long?>(null) }

    val all by contentDao.collections().collectAsState(initial = emptyList())
    val visible = if (expanded) all else all.take(3)

    if (selected == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("مجموعه‌ها", style = MaterialTheme.typography.titleLarge) }
            items(visible) { col ->
                Card(onClick = { selected = col.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(col.title, style = MaterialTheme.typography.titleMedium)
                        val size by contentDao.collectionSize(col.id).collectAsState(initial = 0)
                        Text("$size واژه", style = MaterialTheme.typography.bodySmall)
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
        val colId = selected!!
        val col = all.first { it.id == colId }
        val words by remember(colId) {
            kotlinx.coroutines.flow.flow {
                emit(contentDao.wordsByIds(contentDao.wordIdsOfCollection(colId)))
            }
        }.collectAsState(initial = emptyList())

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text(col.title, style = MaterialTheme.typography.titleLarge)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val byLevel = words.groupBy { it.levelId }
                byLevel.entries.sortedBy { it.key }.forEach { (lv, ws) ->
                    item {
                        Text(
                            "سطح $lv · ${ws.size} واژه",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    items(ws) { w ->
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
