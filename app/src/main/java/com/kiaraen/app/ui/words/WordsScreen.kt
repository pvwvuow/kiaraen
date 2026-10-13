package com.kiaraen.app.ui.words

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.CollectionEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.home.ChevronIcon
import com.kiaraen.app.ui.kiara.KiaraDivider
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private val PanelCream = Color(0xFFFAF9F6)
private val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFFFCF35), Color(0xFFFFB400)))
private val DarkTop = Color(0xFF3B372F)
private val DarkBottom = Color(0xFF2B2822)

/**
 * تب «واژه‌ها» — بازسازی یک‌به‌یک kiara-app.html (#scr-words):
 * جست‌وجو، «مجموعه‌های من» با کاروسل پوشه‌های طلایی (صفحه‌ها + نقطه‌ها + کنترل‌ها)،
 * «کشف مجموعه‌ها» ردیفی، میان‌بُر تاریک ذخیره‌شده‌ها، «واژه‌ها برای سطح تو» با
 * پله‌های A1..C2 روی قفسه و میان‌بُر راهنما — همه با دادهٔ واقعی اپ.
 * ریسپانسیو: کاروسل عرض صفحه را به پهنای صفحه تقسیم می‌کند؛ روی تبلت ستون ۵۲۰dp.
 */
@Composable
fun WordsScreen(
    onOpenCollection: (Long) -> Unit,
    onOpenLevel: (String) -> Unit,
    onOpenColls: () -> Unit,
    onOpenSaved: () -> Unit = {},
    onOpenHelp: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val contentDao = container.database.contentDao()
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    val collections by remember { contentDao.collections() }.collectAsState(initial = emptyList())
    val levels by remember { contentDao.levels() }.collectAsState(initial = emptyList())
    val searchResults by remember(query) {
        if (query.isBlank()) flowOf(emptyList()) else contentDao.searchWords(query.trim())
    }.collectAsState(initial = emptyList())
    val marked by remember { container.appPrefs.markedCollections }.collectAsState(initial = emptySet())
    val bookmarkCount by remember(uid) { container.database.progressDao().bookmarkCount(uid) }
        .collectAsState(initial = 0)
    val stats by remember(uid) { container.database.progressDao().stats(uid) }
        .collectAsState(initial = null)

    val markedIds = marked.mapNotNull { it.toLongOrNull() }.toSet()
    val myCollections = collections.filter { it.id in markedIds }.ifEmpty { collections.take(4) }
    val myIds = myCollections.take(4).map { it.id }.toSet()
    val discover = collections.filterNot { it.id in myIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(18.dp))
            // ── header: «واژه‌ها» + kiara ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "واژه‌ها",
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                KiaraLogotype(size = 34.sp)
            }

            Spacer(Modifier.height(16.dp))
            // ── search field ──
            var focused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(PanelCream, RoundedCornerShape(17.dp))
                    .border(2.dp, if (focused) Color(0xFFDBA800) else Color(0xFFE4DFD4), RoundedCornerShape(17.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // search glyph — gold-brown stroke icon (approved #9B7A18)
                androidx.compose.foundation.Canvas(Modifier.size(21.dp)) {
                    val s = size.width
                    val sw = s * 0.085f
                    val paintStroke = androidx.compose.ui.graphics.drawscope.Stroke(sw, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    drawCircle(color = Color(0xFF9B7A18), radius = s * 0.30f, center = androidx.compose.ui.geometry.Offset(s * 0.42f, s * 0.42f), style = paintStroke)
                    drawLine(
                        color = Color(0xFF9B7A18),
                        start = androidx.compose.ui.geometry.Offset(s * 0.64f, s * 0.64f),
                        end = androidx.compose.ui.geometry.Offset(s * 0.92f, s * 0.92f),
                        strokeWidth = sw,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    )
                }
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = Color(0xFF292923),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    cursorBrush = Brush.verticalGradient(listOf(Gold, Gold)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    "جست‌وجوی مجموعه‌ها و واژه‌ها",
                                    fontSize = 14.sp,
                                    color = Color(0xFF73716D),
                                )
                            }
                            inner()
                        }
                    },
                    onTextLayout = { },
                )
            }
            // focus ring handled softly (no focus tracker needed for fidelity)

            if (query.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                val matchedCols = collections.filter { it.title.contains(query.trim()) }
                if (matchedCols.isNotEmpty()) {
                    Text(
                        "مجموعه‌ها",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                    )
                    Spacer(Modifier.height(6.dp))
                    matchedCols.take(6).forEach { col ->
                        val idx = collections.indexOfFirst { it.id == col.id }
                        DiscoverRow(col, topicCover(idx)) { onOpenCollection(col.id) }
                    }
                    Spacer(Modifier.height(10.dp))
                }
                Text(
                    "نتیجه برای «$query»",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                )
                Spacer(Modifier.height(8.dp))
                searchResults.take(30).forEach { w ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(w.word, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink)
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
                            color = Gold,
                        )
                    }
                    KiaraDivider()
                }
                if (searchResults.isEmpty() && matchedCols.isEmpty()) {
                    Text(
                        "چیزی پیدا نشد",
                        fontSize = 13.sp,
                        color = Muted,
                        modifier = Modifier.padding(8.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                return@Column
            }

            // ── مجموعه‌های من ──
            Spacer(Modifier.height(26.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "مجموعه‌های من",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                // gold + button (ساخت/انتخاب مجموعه از «همه مجموعه‌ها»)
                Box(
                    Modifier
                        .size(40.dp)
                        .shadow(11.dp, RoundedCornerShape(12.dp), spotColor = Color(0x6BFFB400))
                        .background(GoldGradient, RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenColls() }
                        .testTag("words_add_collection"),
                    contentAlignment = Alignment.Center,
                ) {
                    PlusIcon(Modifier.size(21.dp), Color(0xFF2A2620))
                }
            }

            Spacer(Modifier.height(14.dp))
            if (myCollections.isEmpty()) {
                Text(
                    "هنوز مجموعه‌ای نداری؛ از «کشف مجموعه‌ها» شروع کن",
                    fontSize = 13.sp,
                    color = Muted,
                )
            } else {
                FolderCarousel(myCollections) { onOpenCollection(it.id) }
            }

            // ── کشف مجموعه‌ها ──
            Spacer(Modifier.height(36.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "کشف مجموعه‌ها",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "دیدن همه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF77746F),
                    modifier = Modifier
                        .clickable { onOpenColls() }
                        .padding(vertical = 9.dp),
                )
            }
            Spacer(Modifier.height(6.dp))
            discover.forEach { col ->
                val idx = collections.indexOfFirst { it.id == col.id }
                DiscoverRow(col, topicCover(idx)) { onOpenCollection(col.id) }
            }

            // ── ذخیره‌شده‌ها (میان‌بُر تاریک) ──
            Spacer(Modifier.height(30.dp))
            DarkShortcut(
                iconRes = R.drawable.ki2_ic_bookmark,
                iconHeight = 39.dp,
                title = "واژه‌های ذخیره‌شده",
                subtitle = "${faNum(bookmarkCount)} واژه",
                onClick = onOpenSaved,
                modifier = Modifier.testTag("words_saved_shortcut"),
            )

            // ── واژه‌ها برای سطح تو ──
            Spacer(Modifier.height(56.dp))
            Text(
                "واژه‌ها برای سطح تو",
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
            )
            Text(
                "از اولین کلمه تا بیان حرفه‌ای",
                fontSize = 16.sp,
                color = Color(0xFF70695D),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
            )
            val learned = stats?.totalLearned ?: 0
            Box(
                Modifier
                    .padding(top = 16.dp)
                    .shadow(11.dp, RoundedCornerShape(21.dp), spotColor = Color(0x66FFB700))
                    .background(GoldGradient, RoundedCornerShape(21.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "سطح فعلی: ",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Ink,
                    )
                    Text(
                        currentCefr(learned),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            val tiers = listOf(
                Triple("پایه", "A1 / A2", listOf("A1", "A2")),
                Triple("میانی", "B1 / B2", listOf("B1", "B2")),
                Triple("پیشرفته", "C1 / C2", listOf("C1", "C2")),
            )
            Column(verticalArrangement = Arrangement.spacedBy(30.dp)) {
                tiers.forEach { (label, range, ids) ->
                    val tierIds = levels.filter { it.id in ids }.map { it.id }.ifEmpty { ids }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.width(64.dp)) {
                            Text(
                                label,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Ink,
                            )
                            Text(
                                range,
                                fontSize = 14.sp,
                                color = Color(0xFF70695D),
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                tierIds.forEach { id ->
                                    LevelBook(
                                        label = id,
                                        modifier = Modifier.weight(1f),
                                        onClick = { onOpenLevel(id) },
                                    )
                                }
                            }
                            Image(
                                painterResource(R.drawable.ki_pshelf),
                                null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp)
                                    .aspectRatio(700f / 55f),
                            )
                        }
                    }
                }
            }

            // ── راهنمای سطح (میان‌بُر تاریک) ──
            Spacer(Modifier.height(30.dp))
            DarkShortcut(
                iconRes = R.drawable.ki_question,
                iconHeight = 43.dp,
                title = "سطحت رو نمی‌دونی؟",
                subtitle = "راهنمای انتخاب سطح",
                onClick = onOpenHelp,
            )

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** کارت پوشهٔ طلایی کوچک — ردیف «مجموعه‌های من». */
@Composable
private fun FolderCard(col: CollectionEntity, iconRes: Int, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val size by remember(col.id) { container.database.contentDao().collectionSize(col.id) }
        .collectAsState(initial = 0)

    Box(
        Modifier
            .fillMaxWidth()
            .height(127.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        Image(
            painterResource(R.drawable.ki2_folder_bg),
            null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 17.dp, end = 17.dp, top = 30.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    col.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${faNum(size)} واژه",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF8B8375),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Image(
                painterResource(iconRes),
                null,
                modifier = Modifier.size(width = 47.dp, height = 69.dp),
            )
        }
    }
}

/**
 * کاروسل پوشه‌ها — صفحه‌های دوتایی با نوار قفسه، نقطه‌ها و کنترل‌های ‹ ›
 * (هندسهٔ دقیق HTML: دکمه‌های ۳۶dp، نقطهٔ فعال کپسول طلایی ۲۲×۶dp).
 * ریسپانسیو: هر صفحه = عرض کامل کانتینر، پوشه‌ها با وزن مساوی.
 */
@Composable
private fun FolderCarousel(collections: List<CollectionEntity>, onOpen: (CollectionEntity) -> Unit) {
    val pages = collections.chunked(2)
    val state = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val pageW = maxWidth
        val pageWpx = with(density) { pageW.toPx() }
        val page = (state.value / pageWpx).toInt().coerceIn(0, pages.lastIndex)

        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(state),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                pages.forEachIndexed { pi, pair ->
                    Row(
                        Modifier
                            .width(pageW)
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        pair.forEach { col ->
                            Box(Modifier.weight(1f)) {
                                FolderCard(
                                    col = col,
                                    iconRes = folderIconFor(col.title, collections.indexOfFirst { it.id == col.id }),
                                ) { onOpen(col) }
                            }
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Image(
                painterResource(R.drawable.ki_pshelf),
                null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp)
                    .aspectRatio(700f / 59f),
            )
            // controls: › (next, left) — dots — ‹ (prev, right) in RTL
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CarouselChevron(
                    mirror = false,
                    enabled = page < pages.lastIndex,
                ) { scope.launch { state.animateScrollTo((page + 1) * pageWpx.toInt()) } }
                Spacer(Modifier.width(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    pages.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == page) 22.dp else 6.dp, height = 6.dp)
                                .background(
                                    if (i == page) Color(0xFFDFA806) else Color(0xFFD6CFC2),
                                    RoundedCornerShape(5.dp),
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                CarouselChevron(
                    mirror = true,
                    enabled = page > 0,
                ) { scope.launch { state.animateScrollTo((page - 1) * pageWpx.toInt()) } }
            }
        }
    }
}

/** دکمهٔ شورون کاروسل — ‹/› برداری با حالت غیرفعال ۳۵٪ (مطابق CSS). [mirror] شورون را برمی‌گرداند. */
@Composable
private fun CarouselChevron(mirror: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val a = if (enabled) 1f else 0.35f
    Box(
        Modifier
            .size(width = 34.dp, height = 32.dp)
            .background(if (enabled) Color(0x14514A3E) else Color.Transparent, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(16.dp)) {
            ChevronIcon(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = a
                        scaleX = if (mirror) -1f else 1f
                    },
                color = Color(0xFF514A3E),
            )
        }
    }
}

/** ‹+› طلایی «مجموعه‌های من» — بردار هم‌ارز svg طرح (stroke 2.8، گرد). */
@Composable
fun PlusIcon(modifier: Modifier = Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(
            width = w * 0.13f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.5f),
            end = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.5f),
            strokeWidth = stroke.width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.2f),
            end = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.8f),
            strokeWidth = stroke.width,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
    }
}

/** میان‌بُر تاریک — ذخیره‌شده‌ها / راهنما (گرادیان زغالی + هایلایت داخلی). */
@Composable
private fun DarkShortcut(
    iconRes: Int,
    iconHeight: androidx.compose.ui.unit.Dp,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 74.dp)
            .shadow(14.dp, RoundedCornerShape(20.dp), spotColor = Color(0x2E28200C))
            .background(Brush.verticalGradient(listOf(DarkTop, DarkBottom)), RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(iconRes), null, modifier = Modifier.height(iconHeight))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFFDF7),
                lineHeight = 24.sp,
            )
            Text(
                subtitle,
                fontSize = 14.sp,
                color = Color(0xFFD2D0C8),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        ChevronIcon(Modifier.size(22.dp), Color(0xFFCFCBC2))
    }
}

/** کتاب سطح — تصویر کتاب طلایی + برچسب Baloo (A1..C2) روی قفسه. */
@Composable
private fun LevelBook(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(159.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.ki2_folder_books),
            null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Text(
            label,
            fontFamily = com.kiaraen.app.ui.theme.Baloo,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = (-1).sp,
            color = Color(0xFF292923),
            modifier = Modifier.padding(top = 7.dp),
        )
    }
}

/** ردیف «کشف مجموعه‌ها» — کاور راست، عنوان+توضیح، شورون چپ، خط جداکننده. */
@Composable
internal fun DiscoverRow(
    col: CollectionEntity,
    coverRes: Int,
    onClick: () -> Unit,
) {
    val container = LocalAppContainer.current
    val size by remember(col.id) { container.database.contentDao().collectionSize(col.id) }
        .collectAsState(initial = 0)

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 92.dp)
                .clickable { onClick() }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painterResource(coverRes),
                null,
                modifier = Modifier.size(width = 82.dp, height = 94.dp),
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    col.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                    lineHeight = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${faNum(size)} واژه برای ${shortPurpose(col.title)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFF70695D),
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ChevronIcon(Modifier.size(22.dp), Color(0xFF3E3A33))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE5DFD3)),
        )
    }
}

/** «۸۰ واژه برای دانلود خرید گزار» is awkward — a soft purpose line per title. */
internal fun shortPurpose(title: String): String = when {
    title.contains("سفر") -> "سفر کردن"
    title.contains("مکالمه") || title.contains("روزمره") -> "صحبت‌های هر روز"
    title.contains("خانه") -> "زندگی روزمره"
    title.contains("خرید") -> "بازار و فروشگاه"
    title.contains("سلامت") -> "مطب و داروخانه"
    title.contains("آموزش") -> "مدرسه و دانشگاه"
    title.contains("کار") -> "محیط کار"
    title.contains("غذا") -> "رستوران و کافه"
    else -> "گسترش دایره لغت"
}

/** آیکون پوشه‌ها بر اساس موضوع (سفر/مکالمه/کار/غذا) با پشتوانهٔ کتاب‌های موضوعی. */
internal fun folderIconFor(title: String, index: Int): Int = when {
    title.contains("سفر") -> R.drawable.ki_globe
    title.contains("مکالمه") || title.contains("روزمره") -> R.drawable.ki2_topic_chat
    title.contains("کار") -> R.drawable.ki_target
    title.contains("غذا") -> R.drawable.ki_mouth
    else -> topicCover(index)
}

/** سطح فعلی بر پایهٔ تعداد واژه‌های آموخته‌شده (نگاشت واقعی به CEFR). */
internal fun currentCefr(learned: Int): String = when {
    learned < 60 -> "A1"
    learned < 140 -> "A2"
    learned < 260 -> "B1"
    learned < 420 -> "B2"
    learned < 620 -> "C1"
    else -> "C2"
}

/** A1/B1/C1 sit on the golden book, A2/B2/C2 on the ivory one (approved map). */
internal fun isGoldLevel(id: String): Boolean = when (id) {
    "A1", "B1", "C1" -> true
    else -> false
}

/** 16 topic-book covers in collection order (wraps safely beyond 16). */
internal fun topicCover(index: Int): Int = when {
    index < 0 -> R.drawable.ki_topic_01
    else -> TOPIC_RES[index % TOPIC_RES.size]
}

/** Public alias — the «همه مجموعه‌ها» grid reuses the same mapping. */
fun topicCoverRes(index: Int): Int = topicCover(index)

private val TOPIC_RES = listOf(
    R.drawable.ki_topic_01, R.drawable.ki_topic_02, R.drawable.ki_topic_03, R.drawable.ki_topic_04,
    R.drawable.ki_topic_05, R.drawable.ki_topic_06, R.drawable.ki_topic_07, R.drawable.ki_topic_08,
    R.drawable.ki_topic_09, R.drawable.ki_topic_10, R.drawable.ki_topic_11, R.drawable.ki_topic_12,
    R.drawable.ki_topic_13, R.drawable.ki_topic_14, R.drawable.ki_topic_15, R.drawable.ki_topic_16,
)
