package com.kiaraen.app.ui.levels

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted

/**
 * سطح‌بندی CEFR (مسیر یادگیری): سطح → دسته‌ها → آزمون پیش‌فرض.
 * [directLevel] = opened straight into a level (from the books shelf).
 */
@Composable
fun LevelsScreen(onOpenQuiz: (String) -> Unit, directLevel: String? = null) {
    val contentDao = LocalAppContainer.current.database.contentDao()

    var selectedLevel by rememberSaveable { mutableStateOf(directLevel) }
    LaunchedEffect(directLevel) { if (directLevel != null) selectedLevel = directLevel }
    val levelsFlow = remember { contentDao.levels() }
    val levels by levelsFlow.collectAsState(initial = emptyList())

    // system back closes the detail view instead of leaving the screen
    BackHandler(enabled = selectedLevel != null && directLevel == null) { selectedLevel = null }

    val levelId = selectedLevel
    if (levelId == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("سطح‌ها", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
                    Spacer(Modifier.weight(1f))
                    KiaraLogotype(size = 26.sp)
                }
            }
            items(levels, key = { it.id }) { lv ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(18.dp))
                        .clickable { selectedLevel = lv.id }
                        .padding(16.dp),
                ) {
                    Text(lv.title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    } else {
        val catsFlow = remember(levelId) { contentDao.categoriesOf(levelId) }
        val cats by catsFlow.collectAsState(initial = emptyList())
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "دسته‌های سطح $levelId",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    modifier = Modifier.weight(1f),
                )
                if (directLevel == null) {
                    Text(
                        "بازگشت ‹",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = Muted,
                        modifier = Modifier.clickable { selectedLevel = null },
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(cats, key = { it.id }) { cat ->
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                cat.title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Ink,
                            )
                        }
                        KiaraDivider()
                    }
                }
            }
            ClayButton(
                text = "آزمون سطح $levelId",
                onClick = { onOpenQuiz("level:$levelId") },
                modifier = Modifier.padding(vertical = 14.dp),
            )
        }
    }
}
