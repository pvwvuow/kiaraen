package com.kiaraen.app.ui.words

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.kiara.GoldPill
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.SectionHeader
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.kiara.showSoon
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldDark
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val DISCOVER_ICONS = listOf(
    R.drawable.ki_headphones,
    R.drawable.ki_target,
    R.drawable.ki_mouth,
    R.drawable.ki_question,
    R.drawable.ki_a_bookmark,
    R.drawable.ki_globe,
    R.drawable.ki_chat,
)

/**
 * تب «واژه‌ها» — دقیقاً مطابق طرح تأییدشده:
 * جست‌وجو، مجموعه‌های من (پوشه‌ها روی کاروسل)، کشف مجموعه‌ها، سطح‌ها.
 */
@Composable
fun WordsScreen(
    onOpenCollection: (Long) -> Unit,
    onOpenLevel: (String) -> Unit,
) {
    val context = LocalContext.current
    val container = com.kiaraen.app.ui.common.LocalAppContainer.current
    val contentDao = container.database.contentDao()

    var query by remember { mutableStateOf("") }
    val collections by remember { contentDao.collections() }.collectAsState(initial = emptyList())
    val levels by remember { contentDao.levels() }.collectAsState(initial = emptyList())
    val searchResults by remember(query) {
        if (query.isBlank()) flowOf(emptyList()) else contentDao.searchWords(query.trim())
    }.collectAsState(initial = emptyList())

    val myCollections = collections.take(4)
    val discover = collections.drop(4)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("واژه‌ها", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.weight(1f))
            KiaraLogotype(size = 28.sp)
        }

        Spacer(Modifier.height(14.dp))
        // search pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Cream, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                decorationBox = { inner ->
                    Box(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        if (query.isEmpty()) {
                            Text("جست‌وجوی مجموعه‌ها", fontSize = 13.sp, color = Muted)
                        }
                        inner()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(34.dp)
                    .background(Gold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("🔍", fontSize = 14.sp)
            }
        }

        if (query.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "نتیجه برای «$query»",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
            )
            Spacer(Modifier.height(8.dp))
            searchResults.take(30).forEach { w ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        w.word,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        w.meaningFa ?: "",
                        fontSize = 13.sp,
                        color = Muted,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        w.levelId,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = GoldDark,
                    )
                }
                KiaraDivider()
            }
            if (searchResults.isEmpty()) {
                Text("چیزی پیدا نشد", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(8.dp))
            }
            Spacer(Modifier.height(20.dp))
            return@Column
        }

        // ── مجموعه‌های من ────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("مجموعه‌های من", fontSize = 17.sp, fontWeight = FontWeight.Black, color = Ink)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(38.dp)
                    .background(Gold, RoundedCornerShape(12.dp))
                    .clickable { showSoon(context) },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Ink)
            }
        }

        Spacer(Modifier.height(12.dp))
        if (myCollections.isEmpty()) {
            Text(
                "هنوز مجموعه‌ای نداری؛ از «کشف مجموعه‌ها» شروع کن",
                fontSize = 13.sp,
                color = Muted,
            )
        } else {
            // carousel via horizontalScroll (HorizontalPager never settles under
            // Robolectric idling in the full suite — infinite remeasure)
            val hState = rememberScrollState()
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hState),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                myCollections.forEachIndexed { i, col ->
                    Box(Modifier.width(182.dp)) {
                        FolderCard(col, DISCOVER_ICONS[i % DISCOVER_ICONS.size]) {
                            onOpenCollection(col.id)
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "‹",
                    fontSize = 16.sp,
                    color = Muted,
                    modifier = Modifier
                        .clickable {
                            scope.launch { hState.animateScrollBy(-380f) }
                        }
                        .padding(horizontal = 10.dp),
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(22.dp)
                        .height(7.dp)
                        .background(Gold, RoundedCornerShape(4.dp)),
                )
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .width(7.dp)
                        .height(7.dp)
                        .background(Line, RoundedCornerShape(4.dp)),
                )
                Text(
                    "›",
                    fontSize = 16.sp,
                    color = Muted,
                    modifier = Modifier
                        .clickable {
                            scope.launch { hState.animateScrollBy(380f) }
                        }
                        .padding(horizontal = 10.dp),
                )
            }
        }

        // ── کشف مجموعه‌ها ────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "کشف مجموعه‌ها", actionText = "دیدن همه")
        discover.forEachIndexed { i, col ->
            DiscoverRow(col, DISCOVER_ICONS[i % DISCOVER_ICONS.size]) { onOpenCollection(col.id) }
        }

        // ── سطح‌ها ───────────────────────────────────────────────────────
        Spacer(Modifier.height(18.dp))
        SectionHeader(title = "سطح‌ها")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            levels.forEach { lv ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onOpenLevel(lv.id) }
                        .padding(4.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painterResource(R.drawable.ki_books),
                            null,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1.093f),
                        )
                        Text(
                            lv.id,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = Ink,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** پوشه طلایی با محتوا — کارت مجموعه «مجموعه‌های من». */
@Composable
private fun FolderCard(
    col: com.kiaraen.app.data.local.CollectionEntity,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val container = com.kiaraen.app.ui.common.LocalAppContainer.current
    val size by remember(col.id) { container.database.contentDao().collectionSize(col.id) }
        .collectAsState(initial = 0)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(621f / 426f)
            .clickable { onClick() },
    ) {
        Image(
            painterResource(R.drawable.ki_folder),
            null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 12.dp, top = 30.dp, end = 22.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    col.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    "${faNum(size)} واژه",
                    fontSize = 11.sp,
                    color = Ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Image(
                painterResource(iconRes),
                null,
                modifier = Modifier.size(54.dp),
            )
        }
    }
}

/** ردیف «کشف مجموعه‌ها»: آیکون راست، عنوان + تعداد، شورون چپ. */
@Composable
private fun DiscoverRow(
    col: com.kiaraen.app.data.local.CollectionEntity,
    iconRes: Int,
    onClick: () -> Unit,
) {
    val container = com.kiaraen.app.ui.common.LocalAppContainer.current
    val size by remember(col.id) { container.database.contentDao().collectionSize(col.id) }
        .collectAsState(initial = 0)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(iconRes),
                null,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    col.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    color = Ink,
                    maxLines = 1,
                )
                Text(
                    "${faNum(size)} واژه",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text("‹", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Ink)
        }
        KiaraDivider()
    }
}

