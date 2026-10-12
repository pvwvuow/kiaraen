package com.kiaraen.app.ui.levels

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
import androidx.compose.ui.unit.dp
import com.kiaraen.app.ui.common.LocalAppContainer

/**
 * Level browser (فهرست): pick a level → ordered category list → default quiz.
 */
@Composable
fun LevelsScreen(onOpenQuiz: (String) -> Unit) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    var selectedLevel by remember { mutableStateOf<String?>(null) }
    val levels by contentDao.levels().collectAsState(initial = emptyList())

    if (selectedLevel == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("سطح‌ها", style = MaterialTheme.typography.titleLarge) }
            items(levels) { lv ->
                Card(onClick = { selectedLevel = lv.id }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(lv.title, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    } else {
        val levelId = selectedLevel!!
        val cats by contentDao.categoriesOf(levelId).collectAsState(initial = emptyList())
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("دسته‌های سطح $levelId", style = MaterialTheme.typography.titleLarge)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats) { cat ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat.title)
                        }
                    }
                }
            }
            Button(
                onClick = { onOpenQuiz("level:$levelId") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("آزمون پیش‌فرض سطح $levelId") }
        }
    }
}
