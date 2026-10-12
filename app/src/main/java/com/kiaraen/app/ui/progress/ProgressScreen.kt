package com.kiaraen.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId

@Composable
fun ProgressScreen() {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val dao = container.database.progressDao()

    val statsFlow = remember(uid) { dao.stats(uid) }
    val learnedFlow = remember(uid) { dao.learnedCount(uid) }
    val stats by statsFlow.collectAsState(initial = null)
    val learned by learnedFlow.collectAsState(initial = 0)

    val reviews = stats?.totalReviews ?: 0
    val correct = stats?.totalCorrect ?: 0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("پیشرفت من", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatRow("واژه‌های یادگرفته", "${maxOf(stats?.totalLearned ?: 0, learned)}")
                StatRow("تسلط کامل", "${stats?.totalMastered ?: 0}")
                StatRow("استریک فعلی", "${stats?.currentStreak ?: 0} روز")
                StatRow("رکورد استریک", "${stats?.longestStreak ?: 0} روز")
                StatRow("کل مرورها", "$reviews")
                StatRow("دقت", if (reviews == 0) "-" else "${(correct * 100) / reviews}%")
                StatRow("XP", "${stats?.totalXp ?: 0}")
            }
        }
        Text(
            "این آمار به‌صورت آفلاین از داده‌های محلی ساخته می‌شود و با ورود به حساب، بین دستگاه‌های شما همگام می‌شود.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
