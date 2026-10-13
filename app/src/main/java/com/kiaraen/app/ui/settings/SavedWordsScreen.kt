package com.kiaraen.app.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.core.TtsPlayer
import com.kiaraen.app.data.local.WordEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * واژه‌های ذخیره‌شده — the bookmark list behind the settings row
 * (و «واژه‌های ذخیره‌شده · ۴ واژه»). Tap the bookmark again to unsave.
 */
@Composable
fun SavedWordsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()

    var words by remember { mutableStateOf<List<WordEntity>>(emptyList()) }
    LaunchedEffect(uid) {
        words = runCatching { progressDao.bookmarkedWords(uid) }.getOrDefault(emptyList())
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

            Spacer(Modifier.padding(top = 14.dp))
            DetailHeader(
                title = "واژه‌های ذخیره‌شده",
                subtitle = "${words.size} واژه",
                onBack = onBack,
            )
            Spacer(Modifier.height(10.dp))

            if (words.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(painterResource(R.drawable.ki_ui_bookmark), null, Modifier.size(72.dp).alpha(0.4f))
                        Spacer(Modifier.padding(top = 12.dp))
                        Text("هنوز واژه‌ای ذخیره نکردی", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Ink)
                        Text(
                            "توی فلش‌کارت‌ها و صفحهٔ موضوع، نشانک واژه‌ها رو بزن",
                            fontSize = 12.sp,
                            color = Muted,
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(words, key = { it.id }) { w ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(Cream, RoundedCornerShape(20.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(w.word, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Ink)
                                        Text(
                                            "  ${w.levelId}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = GoldDark,
                                        )
                                    }
                                    Text(
                                        w.meaningFa.orEmpty(),
                                        fontSize = 12.sp,
                                        color = Muted,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Image(
                                    painterResource(R.drawable.ki_g_speaker),
                                    null,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .alpha(0.85f)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            scope.launch {
                                                val rate = runCatching { container.appPrefs.speechRate.first() }
                                                    .getOrDefault("normal")
                                                TtsPlayer.speakWord(context, w.word, rate)
                                            }
                                        },
                                )
                                Spacer(Modifier.width(12.dp))
                                Image(
                                    painterResource(R.drawable.ki_ui_bookmark),
                                    null,
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                        ) {
                                            scope.launch {
                                                runCatching { progressDao.removeBookmark(uid, w.id) }
                                                words = words.filterNot { it.id == w.id }
                                            }
                                        },
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.padding(bottom = 24.dp)) }
                }
            }
    
        }
    }
}
