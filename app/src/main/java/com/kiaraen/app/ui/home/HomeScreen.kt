package com.kiaraen.app.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.data.local.CollectionEntity
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.common.rememberUserId
import com.kiaraen.app.ui.kiara.KiaraLogotype
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import java.time.LocalDate

/** --dark of the prototype (#363535): folder button + progress track. */
private val Dark = Color(0xFF363535)
/** rgba(74,55,6,.8) — «درس ۳ از ۱۲» on the folder. */
private val LessonInk = Color(0xCC4A3706)
private val PanelCream = Color(0xFFFAF9F6)
private val GoldGradient = Brush.verticalGradient(listOf(Color(0xFFFFCF35), Color(0xFFFFB700)))

/**
 * صفحه خانه — بازسازی یک‌به‌یک kiara-app.html (منبع: اسکرین #scr-home).
 * همهٔ اندازه‌ها از فضای native 853 ÷ ۲٫۱۸۸ به dp تبدیل شده‌اند:
 * لوگوتایپ + قرص شعلهٔ استریک، تیتر خوش‌آمد، چیپ‌های روز هفته (گذشته/امروز/آینده)،
 * کارت پوشهٔ طلایی با تب «ادامه یادگیری»، کتاب، نوار پیشرفت زغالی و دکمهٔ «ادامه بده»،
 * ردیف «وقت مرور رسیده» با پیل طلایی و دو کارت موضوعی با دایرهٔ شورون.
 * + ردیف «یادگرفته‌ها» (تعداد کل واژه‌های یادگرفته) و ردیف «مرور اشتباهات» (v0.7.0).
 *
 * ریسپانسیو: هیچ اندازهٔ مطلقی برای عرض وجود ندارد — همه‌چیز با وزن/نسبت رشد می‌کند
 * و روی صفحه‌های پهن محتوا در ستون ۵۲۰dp وسط‌چین می‌شود.
 */
@Composable
fun HomeScreen(
    onOpenQuiz: (String) -> Unit,
    onOpenReview: () -> Unit,
    onOpenLearn: (String, String) -> Unit,
    onOpenCollection: (Long) -> Unit,
    onSeeAllCollections: () -> Unit,
    onOpenMistakes: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val container = LocalAppContainer.current
    val uid = rememberUserId()
    val progressDao = container.database.progressDao()
    val contentDao = container.database.contentDao()

    val statsFlow = remember(uid) { progressDao.stats(uid) }
    val dueFlow = remember(uid) { progressDao.dueCount(uid, System.currentTimeMillis()) }
    val learnedFlow = remember(uid) { progressDao.learnedCount(uid) }
    val mistakesFlow = remember(uid) { progressDao.mistakeCount(uid) }
    val collectionsFlow = remember { contentDao.collections() }
    val stats by statsFlow.collectAsState(initial = null)
    val due by dueFlow.collectAsState(initial = 0)
    val learned by learnedFlow.collectAsState(initial = 0)
    val mistakes by mistakesFlow.collectAsState(initial = 0)
    val collections by collectionsFlow.collectAsState(initial = emptyList())

    val folder = collections.firstOrNull()
    val folderLearnedFlow = remember(folder?.id, uid) {
        folder?.id?.let { cid -> contentDao.learnedInCollection(cid, uid) }
    }
    val folderLearned by (folderLearnedFlow ?: kotlinx.coroutines.flow.flowOf(0))
        .collectAsState(initial = 0)

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

            // ── header: streak (right) + logotype (left) ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.ki_btn_fire),
                        null,
                        modifier = Modifier.height(48.dp),
                    )
                    Text(
                        faNum(stats?.currentStreak ?: 0),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if ((stats?.currentStreak ?: 0) > 0) Ink else Muted,
                        modifier = Modifier.padding(start = 7.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                KiaraLogotype(size = 35.sp)
            }

            Spacer(Modifier.height(24.dp))
            // ── greeting ──
            Text(
                "یه قدم جلوتر، هر روز",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "امروز برای خودت وقت بذار",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )

            Spacer(Modifier.height(22.dp))
            WeekChips()

            Spacer(Modifier.height(24.dp))
            folder?.let {
                FolderCard(
                    col = it,
                    learned = folderLearned,
                    onContinue = { onOpenLearn("collection:${it.id}", "flash") },
                )
            }

            Spacer(Modifier.height(22.dp))
            // separator (border-top 3px rgba(28,26,23,.06))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(Ink.copy(alpha = 0.06f)),
            )

            Spacer(Modifier.height(16.dp))
            // ── learned total («تعداد لغات کلی یادگرفته») ──
            StatRow(
                iconRes = R.drawable.ki_g_star,
                title = "یادگرفته‌ها",
                subtitle = "تا حالا ${faNum(learned)} واژه یاد گرفتی",
                badge = "${faNum(learned)} واژه",
                onClick = onOpenProgress,
            )

            Spacer(Modifier.height(14.dp))
            // ── review row ──
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painterResource(R.drawable.ki2_aacal),
                    null,
                    modifier = Modifier
                        .height(74.dp)
                        .padding(top = 2.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "وقت مرور رسیده",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Ink,
                    )
                    Text(
                        "${faNum(due)} واژه منتظره",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Muted,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Box(
                    Modifier
                        .shadow(10.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x66FFB700))
                        .background(GoldGradient, RoundedCornerShape(percent = 50))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onOpenReview() }
                        .padding(horizontal = 24.dp, vertical = 9.dp)
                        .testTag("home_review_cta"),
                ) {
                    Text("مرور", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
                }
            }

            Spacer(Modifier.height(14.dp))
            // ── mistakes review («مرور اشتباهات») ──
            StatRow(
                iconRes = R.drawable.ki_g_quiz,
                title = "مرور اشتباهات",
                subtitle = if (mistakes > 0) "${faNum(mistakes)} واژه اشتباه داشتی" else "هنوز اشتباهی ثبت نشده",
                badge = if (mistakes > 0) "مرور" else null,
                onClick = onOpenMistakes,
                onBadge = if (mistakes > 0) {
                    { onOpenQuiz("mistakes") }
                } else null,
            )

            Spacer(Modifier.height(30.dp))
            // ── pick a topic ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "چی دوست داری یاد بگیری؟",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "دیدن همه",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Muted,
                    modifier = Modifier.clickable { onSeeAllCollections() },
                )
            }

            Spacer(Modifier.height(16.dp))
            val explore = collections.drop(1).take(2)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                explore.forEachIndexed { i, col ->
                    Box(Modifier.weight(1f)) {
                        TopicCard(
                            col = col,
                            iconRes = if (i == 0) R.drawable.ki_globe else R.drawable.ki2_topic_chat,
                            bigIcon = i == 0,
                        ) { onOpenCollection(col.id) }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** ردیف آمار/دسترسی خانه — آیکون سه‌بعدی + عنوان/زیرنویس + پیل طلایی اختیاری. */
@Composable
private fun StatRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    badge: String?,
    onClick: () -> Unit,
    onBadge: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painterResource(iconRes),
            null,
            modifier = Modifier
                .height(56.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (badge != null) {
            Box(
                Modifier
                    .shadow(10.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x66FFB700))
                    .background(GoldGradient, RoundedCornerShape(percent = 50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { (onBadge ?: onClick)() }
                    .padding(horizontal = 24.dp, vertical = 9.dp),
            ) {
                Text(badge, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
            }
        }
    }
}

/** هفت چیپ روز هفته — حرف بالای چیپ سه‌بعدی (گذشته=کرم/نقطه، امروز=شعله، آینده=یخی). */
@Composable
private fun WeekChips() {
    val today = LocalDate.now()
    val todayIdx = (today.dayOfWeek.value + 1) % 7 // Sat=0 .. Fri=6
    val labels = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        labels.forEachIndexed { i, label ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Ink,
                )
                Spacer(Modifier.height(4.dp))
                val res = when {
                    i == todayIdx -> R.drawable.ki_btn_fireb
                    i < todayIdx -> R.drawable.ki_btn_ice
                    else -> R.drawable.ki2_chip_future
                }
                Image(
                    painterResource(res),
                    null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (i == todayIdx) 95f / 106f else 95f / 82f),
                )
            }
        }
    }
}

/** کارت پوشهٔ طلایی — یک‌به‌یک از طرح (تب، کتاب، متادیتا، نوار، دکمهٔ زغالی داخل پوشه). */
@Composable
private fun FolderCard(col: CollectionEntity, learned: Int, onContinue: () -> Unit) {
    val container = LocalAppContainer.current
    val sizeFlow = remember(col.id) { container.database.contentDao().collectionSize(col.id) }
    val size by sizeFlow.collectAsState(initial = 0)

    val total = size.coerceAtLeast(1)
    val progress = (learned.toFloat() / total).coerceIn(0f, 1f)
    val lessons = (total / 12).coerceAtLeast(1)
    val lesson = (progress * lessons).toInt().coerceIn(0, lessons).let { if (it == 0) 1 else it }

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(765f / 494f)
            .testTag("home_folder_card"),
    ) {
        Image(
            painterResource(R.drawable.ki2_folder_bg),
            null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        // floating books — left side, peeking above the folder pocket
        Image(
            painterResource(R.drawable.ki2_folder_books),
            null,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 11.dp)
                .fillMaxHeight(0.63f)
                .aspectRatio(342f / 396f),
        )
        // meta (right side): title, lesson, progress tube
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                col.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "درس ${faNum(lesson)} از ${faNum(lessons)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LessonInk,
                modifier = Modifier.padding(top = 10.dp),
            )
            // dark tube + gold segment (right-anchored — RTL fill)
            Box(
                Modifier
                    .padding(top = 13.dp)
                    .width(138.dp)
                    .height(10.dp)
                    .background(Dark, RoundedCornerShape(percent = 50)),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(progress.coerceAtLeast(0.08f))
                        .fillMaxHeight()
                        .padding(horizontal = 2.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFFFFD34D), Color(0xFFFFB700))),
                            RoundedCornerShape(percent = 50),
                        ),
                )
            }
        }
        // dark «ادامه بده» pill inside the folder (triangle at the right end)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 20.dp, end = 22.dp, bottom = 14.dp)
                .fillMaxWidth()
                .height(44.dp)
                .shadow(10.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x40281F0A))
                .background(Dark, RoundedCornerShape(percent = 50))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onContinue() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "ادامه بده",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
            androidx.compose.foundation.Canvas(
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 22.dp)
                    .size(width = 17.dp, height = 21.dp),
            ) {
                val w = 17.dp.toPx()
                val h = 21.dp.toPx()
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    lineTo(w, h / 2f)
                    lineTo(0f, h)
                    close()
                }
                drawPath(tri, color = com.kiaraen.app.ui.theme.Gold)
            }
        }
    }
}

/** کارت موضوعی — آیکون سه‌بعدی چپ، عنوان+تعداد راست، دایرهٔ شورون طلایی پایین-راست. */
@Composable
private fun TopicCard(col: CollectionEntity, iconRes: Int, bigIcon: Boolean, onClick: () -> Unit) {
    val container = LocalAppContainer.current
    val sizeFlow = remember(col.id) { container.database.contentDao().collectionSize(col.id) }
    val size by sizeFlow.collectAsState(initial = 0)

    Box(
        Modifier
            .fillMaxWidth()
            .height(102.dp)
            .shadow(
                14.dp,
                RoundedCornerShape(17.dp),
                ambientColor = Color(0x24786946),
                spotColor = Color(0x24786946),
            )
            .background(PanelCream, RoundedCornerShape(17.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
    ) {
        Image(
            painterResource(iconRes),
            null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = if (bigIcon) 7.dp else 15.dp, top = if (bigIcon) 5.dp else 13.dp)
                .height(if (bigIcon) 81.dp else 67.dp),
        )
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(end = 11.dp, top = 16.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                col.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${faNum(size)} واژه",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Muted,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        // gold chevron circle — bottom-right (approved position)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 15.dp, bottom = 15.dp)
                .size(19.dp)
                .shadow(6.dp, RoundedCornerShape(percent = 50), spotColor = Color(0x73FFB400))
                .background(GoldGradient, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            ChevronIcon(modifier = Modifier.size(9.dp), color = Color(0xFF2A2620))
        }
    }
}

/** ‹ chevron (RTL «next») — vector clone of the prototype's svg. */
@Composable
fun ChevronIcon(modifier: Modifier = Modifier, color: Color) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.width * 0.34f
        val p = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.72f, h * 0.18f)
            lineTo(w * 0.28f, h * 0.5f)
            lineTo(w * 0.72f, h * 0.82f)
        }
        drawPath(
            p,
            color = color,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round),
        )
    }
}
