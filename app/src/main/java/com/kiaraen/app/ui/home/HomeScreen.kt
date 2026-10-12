package com.kiaraen.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId

@Composable
fun HomeScreen(onOpenQuiz: (String) -> Unit, onOpenLevels: () -> Unit) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val dao = container.database.progressDao()
    val contentDao = container.database.contentDao()

    val stats by dao.stats(uid).collectAsState(initial = null)
    val due by dao.dueCount(uid, System.currentTimeMillis()).collectAsState(initial = 0)
    val levels by contentDao.levels().collectAsState(initial = emptyList())

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("سلام! 👋", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("استریک: ${stats?.currentStreak ?: 0}")
                        Text("XP: ${stats?.totalXp ?: 0}")
                        Text("آماده مرور: $due")
                    }
                }
            }
        }
        item {
            Button(onClick = { onOpenQuiz("review") }, modifier = Modifier.fillMaxWidth()) {
                Text("شروع مرور امروز ($due کارت)")
            }
        }
        item { Text("سطح‌ها", style = MaterialTheme.typography.titleMedium) }
        items(levels) { lv ->
            Card(onClick = onOpenLevels, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) { Text(lv.title) }
            }
        }
    }
}
