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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId

@Composable
fun ProgressScreen() {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val dao = container.database.progressDao()

    val stats by dao.stats(uid).collectAsState(initial = null)
    val learned by dao.learnedCount(uid).collectAsState(initial = 0)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("پیشرفت من", style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("واژه‌های یادگرفته")
                    Text("${stats?.totalLearned ?: learned}", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("تسلط کامل")
                    Text("${stats?.totalMastered ?: 0}", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("استریک فعلی")
                    Text("${stats?.currentStreak ?: 0} روز", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("رکورد استریک")
                    Text("${stats?.longestStreak ?: 0} روز", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("کل مرورها")
                    Text("${stats?.totalReviews ?: 0}", style = MaterialTheme.typography.titleMedium)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("دقت")
                    val r = stats?.totalReviews ?: 0
                    val c = stats?.totalCorrect ?: 0
                    Text(if (r == 0) "-" else "${(c * 100) / r}%", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Text(
            "این آمار به‌صورت آفلاین از داده‌های محلی ساخته می‌شود و با ورود به حساب، بین دستگاه‌های شما همگام می‌شود.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
