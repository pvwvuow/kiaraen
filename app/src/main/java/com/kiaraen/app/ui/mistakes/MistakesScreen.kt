package com.kiaraen.app.ui.mistakes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.MistakeRow
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.home.ChevronIcon
import com.kiaraen.app.ui.kiara.ClayButton
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Danger
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Lalezar
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper

/**
 * اشتباهات — the ONE place every wrong answer lands (flashcard «یادآوری»,
 * review «فردا», quiz ✗). Newest first, with a per-word wrong counter.
 * The gold CTA opens the dedicated quiz; answering a word correctly inside a
 * COMPLETED quiz clears it from this list (same gate that grants «learned»).
 */
@Composable
fun MistakesScreen(
    onBack: () -> Unit,
    onOpenQuiz: (String) -> Unit,
) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()

    var rows by remember { mutableStateOf<List<MistakeRow>>(emptyList()) }
    LaunchedEffect(uid) {
        rows = runCatching { progressDao.mistakeWords(uid) }.getOrDefault(emptyList())
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Paper),
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
                title = "اشتباهات",
                subtitle = "همهٔ اشتباهاتت یک‌جا، تازه‌ترین اول",
                onBack = onBack,
            )

            if (rows.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Cream, RoundedCornerShape(28.dp))
                        .padding(28.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Image(painterResource(R.drawable.ki_m_check), null, Modifier.size(72.dp))
                        Spacer(Modifier.height(14.dp))
                        Text("اشتباهی نداری!", fontFamily = Lalezar, fontSize = 26.sp, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "هر وقت جایی واژه‌ای رو اشتباه بزنی، اینجا ثبت می‌شه تا دوباره مرورش کنی",
                            fontSize = 13.sp,
                            color = Muted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(20.dp))
                        ClayButton(text = "بازگشت", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    }
                }
            } else {
                Spacer(Modifier.height(12.dp))
                // the dedicated mistakes quiz — correct answers clear words from here
                ClayButton(
                    text = "مرور اشتباهات",
                    onClick = { onOpenQuiz("mistakes") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "تو آزمون، واژه‌هایی که درست جواب بدی از این لیست پاک می‌شن",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(rows, key = { it.wordId }) { row ->
                        MistakeCard(row)
                        Spacer(Modifier.height(10.dp))
                    }
                    item { Spacer(Modifier.height(20.dp)) }
                }
            }
        }
    }
}

/** ردیف اشتباه — واژه + معنی + شمارندهٔ اشتباه در یک کارت کرم. */
@Composable
private fun MistakeCard(row: MistakeRow) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(20.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    row.word,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    row.meaningFa.orEmpty(),
                    fontSize = 13.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // wrong counter badge — «۳ بار اشتباه»
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "×${faNum(row.wrongCount)}",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Danger,
                )
                Text(
                    "اشتباه",
                    fontSize = 10.sp,
                    color = Muted,
                )
            }
            Spacer(Modifier.width(12.dp))
            ChevronIcon(modifier = Modifier.size(12.dp), color = Muted)
        }
    }
}
