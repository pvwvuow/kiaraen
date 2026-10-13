package com.kiaraen.app.ui.awards

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.widthIn

/**
 * دستاوردها — the approved #awards screen: 3 shelves × 3 badges with labels;
 * locked badges render grayscale + faded (exactly the approved look), the
 * unlocked ones sit in color on the shelf. Conditions are REAL stats
 * (learned counts, streaks, quizzes, XP) from user_stats.
 */
private data class Badge(
    val res: Int,
    val label: String,
    val unlocked: (com.kiaraen.app.data.local.UserStatsEntity) -> Boolean,
)

@Composable
fun AwardsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()

    var stats by remember { mutableStateOf<com.kiaraen.app.data.local.UserStatsEntity?>(null) }
    LaunchedEffect(uid) {
        stats = runCatching { progressDao.statsOnce(uid) }.getOrNull()
            ?: com.kiaraen.app.data.local.UserStatsEntity(userId = uid)
    }

    val s = stats
    val badges = remember(s) {
        listOf(
            Badge(R.drawable.ki_trophy_gold, "اولین مجموعه") { it.totalLearned >= 1 },
            Badge(R.drawable.ki_books, "۱۰۰ واژه") { it.totalLearned >= 100 },
            Badge(R.drawable.ki_flame, "۷ روز پیوسته") { it.currentStreak >= 7 },
            Badge(R.drawable.ki_g_lv10, "سطح A2") { it.totalLearned >= 300 },
            Badge(R.drawable.ki_a_coin, "۱۰۰۰ سکه") { it.totalXp >= 1000 },
            Badge(R.drawable.ki_g_crown, "هفتهٔ کامل") { it.longestStreak >= 7 },
            Badge(R.drawable.ki_g_star, "شب امتحان") { it.totalReviews >= 100 },
            Badge(R.drawable.ki_a_chest, "اولین آزمون") { it.totalQuizAttempts >= 1 },
            Badge(R.drawable.ki_g_gift, "هفته‌ی kiara") { it.longestStreak >= 30 },
            Badge(R.drawable.ki_g_quiz, "آزمون‌باز") { it.totalQuizAttempts >= 10 },
            Badge(R.drawable.ki_g_heart, "بی‌نقص") { it.totalCorrect >= 200 },
            Badge(R.drawable.ki_a_eye, "خوانندهٔ فعال") { it.totalReviews >= 1000 },
        )
    }
    val unlockedCount = badges.count { s != null && it.unlocked(s) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {

            Spacer(Modifier.height(14.dp))
            DetailHeader(
                title = "دستاوردها",
                subtitle = "${faNum(unlockedCount)} از ${faNum(badges.size)} نشان",
                onBack = onBack,
            )

            Spacer(Modifier.height(10.dp))
            badges.chunked(3).forEach { row ->
                ShelfRow(
                    badges = row.map { Triple(it.res, it.label, s != null && it.unlocked(s)) },
                )
                Spacer(Modifier.height(20.dp))
            }

            Text(
                "نشان‌های بیشتر به‌زودی اضافه می‌شود",
                fontSize = 11.sp,
                color = Muted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 26.dp),
            )
    
        }
    }
}

/** یک قفسه: سه نشان بالای تخته + برچسب زیر آن (طرح تأییدشده). */
@Composable
private fun ShelfRow(badges: List<Triple<Int, String, Boolean>>) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp)
                .height(86.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom,
        ) {
            badges.forEach { (res, _, unlocked) ->
                Box(
                    Modifier
                        .width(96.dp)
                        .height(82.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Image(
                        painterResource(res),
                        null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.15f)
                            .graphicsLayer { if (!unlocked) alpha = 0.45f },
                        colorFilter = if (unlocked) {
                            null
                        } else {
                            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                        },
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        Image(
            painterResource(R.drawable.ki_pshelf),
            null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(700f / 106f),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            badges.forEach { (_, label, _) ->
                Text(
                    label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(96.dp),
                )
            }
        }
    }
}
