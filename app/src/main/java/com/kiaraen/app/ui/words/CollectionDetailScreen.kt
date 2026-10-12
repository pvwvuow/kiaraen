package com.kiaraen.app.ui.words

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted

/**
 * جزئیات مجموعه — واژه‌ها گروه‌بندی‌شده بر اساس سطح (مثل نسخه تصویب‌شده) +
 * دکمه خمیری «آزمون این مجموعه». JOIN query keeps big collections safe on
 * Android 8-10 (SQLite 999-variable limit).
 */
@Composable
fun CollectionDetailScreen(
    collectionId: Long,
    onBack: () -> Unit,
    onOpenQuiz: (String) -> Unit,
) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    BackHandler { onBack() }

    val all by remember { contentDao.collections() }.collectAsState(initial = emptyList())
    val col = all.firstOrNull { it.id == collectionId }
    var words by remember(collectionId) { mutableStateOf<List<WordEntity>>(emptyList()) }
    LaunchedEffect(collectionId) {
        words = runCatching { contentDao.wordsOfCollectionOnce(collectionId) }
            .getOrDefault(emptyList())
    }

    Column(
        Modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                col?.title ?: "...",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                "بازگشت ‹",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = Muted,
                modifier = Modifier.clickable { onBack() },
            )
        }
        Text(
            "${faNum(words.size)} واژه در این مجموعه",
            fontSize = 12.sp,
            color = Muted,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val byLevel = words.groupBy { it.levelId }
            byLevel.entries.sortedBy { it.key }.forEach { (lv, ws) ->
                item(key = "h_$lv") {
                    Text(
                        "سطح $lv · ${faNum(ws.size)} واژه",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Ink,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                    )
                }
                items(ws, key = { it.id }) { w ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                w.word,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Ink,
                            )
                            w.pos?.let {
                                Text(
                                    "  $it",
                                    fontSize = 11.sp,
                                    color = Muted,
                                )
                            }
                        }
                        Text(
                            w.meaningFa ?: "",
                            fontSize = 13.sp,
                            color = Muted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    KiaraDivider()
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        ClayButton(
            text = "آزمون این مجموعه",
            onClick = { onOpenQuiz("collection:$collectionId") },
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 14.dp),
        )
    }
}
