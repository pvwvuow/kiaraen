package com.kiaraen.app.ui.kiara

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.theme.CapsuleGradient
import com.kiaraen.app.ui.theme.CardCream
import com.kiaraen.app.ui.theme.Charcoal
import com.kiaraen.app.ui.theme.CharcoalShade
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldShade
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.IceCream
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.TrackBrown

/**
 * v0.4.0 additions to the approved design system — everything here mirrors a
 * concrete element of the HTML prototype (kiara-app.html previews).
 */

/** Dark charcoal clay button — the «ادامه بده» on the home folder card. */
@Composable
fun CharcoalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 54.dp,
    fontSize: TextUnit = 16.sp,
    leading: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(CharcoalShade, shape)
                .align(Alignment.BottomCenter),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height - 5.dp)
                .background(Charcoal, shape)
                .clickable { onClick() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.invoke(this)
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                color = Gold,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
            trailing?.invoke(this)
        }
    }
}

/** Outline pill button — «بازگشت» / «آزمایش یادآور» secondary actions. */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 50.dp,
    fontSize: TextUnit = 15.sp,
    textColor: Color = Ink,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .height(height)
            .background(Cream, RoundedCornerShape(percent = 50))
            .clickable { onClick() }
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke(this)
            Spacer(Modifier.width(6.dp))
            Text(text, color = textColor, fontSize = fontSize, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * The approved progress capsule — a clean CSS-style gradient tube
 * (Task 20 recipe: #FEF7CC → #FDCE23 → #E7A902 + inset highlight),
 * never seams or stretches. [fraction] is RTL-aware (fills right→left).
 */
@Composable
fun CapsuleBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    track: Color = Color(0xFFEFEAE0),
) {
    val f = fraction.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .background(track, RoundedCornerShape(percent = 50)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(if (f <= 0.01f) 0f else f)
                .fillMaxHeight()
                .background(CapsuleGradient, RoundedCornerShape(percent = 50)),
        )
    }
}

/** Gold rounded-square button with the ‹ chevron — back affordance of detail screens. */
@Composable
fun BackSquare(onBack: () -> Unit, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    Box(
        modifier
            .size(size)
            .background(GoldShade, RoundedCornerShape(15.dp))
            .padding(bottom = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .background(Gold, RoundedCornerShape(15.dp))
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink)
        }
    }
}

/**
 * Standard header of every detail screen:
 * [title] big black + optional [subtitle] muted on the right (RTL),
 * gold back square on the left edge — exactly the approved sticky header.
 */
@Composable
fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                )
            }
        }
        BackSquare(onBack = onBack)
    }
}

/** Gold switch — «یادآور روزانه» / تنظیمات toggles. */
@Composable
fun KiaraSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val trackW = 58.dp
    val trackH = 34.dp
    val knob = 26.dp
    Box(
        modifier
            .width(trackW)
            .height(trackH)
            .background(if (on) Gold else IceCream, RoundedCornerShape(percent = 50))
            .clickable { onChange(!on) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .size(knob)
                .graphicsLayer { translationX = if (on) trackW.toPx() - knob.toPx() - 8.dp.toPx() else 0f }
                .background(Color.White, CircleShape),
        )
    }
}

/** Segmented pill — «سرعت تلفظ» / time chips; [selected] item sits on gold. */
@Composable
fun SegmentedPill(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(IceCream, RoundedCornerShape(percent = 50))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (i == selected) Gold else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    fontSize = 13.sp,
                    fontWeight = if (i == selected) FontWeight.Black else FontWeight.SemiBold,
                    color = if (i == selected) Ink else Muted,
                )
            }
        }
    }
}

/**
 * Warm ring — the «بوردر نارنجی چرخان» around today's chip / the streak flame.
 * Compose-safe rendering: the sweep gradient border is static (a *rotated*
 * rounded rect wobbles — the exact bug the HTML prototype hit); a soft
 * alpha shimmer keeps it alive instead.
 */
@Composable
fun SpinningGoldRing(
    modifier: Modifier = Modifier,
    corner: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val shimmer by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "shimmer",
    )
    val ring = Brush.sweepGradient(
        0.00f to Gold,
        0.45f to Color(0xFFFF9D2E),
        0.75f to Color(0xFFFFE68A),
        1.00f to Gold,
    )
    Box(modifier.graphicsLayer { alpha = shimmer }) {
        Box(
            Modifier
                .matchParentSize()
                .background(ring, RoundedCornerShape(corner)),
        )
        Box(
            Modifier
                .padding(2.5.dp)
                .matchParentSize()
                .background(CardCream, RoundedCornerShape(corner - 2.dp)),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

/** Accordion row (FAQ) — chevron rotates, content expands smoothly. */
@Composable
fun AccordionRow(
    question: String,
    answer: String,
    modifier: Modifier = Modifier,
) {
    val open = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val isOpen = open.value
    Column(
        modifier
            .fillMaxWidth()
            .background(Cream, RoundedCornerShape(18.dp))
            .clickable { open.value = !open.value }
            .animateContentSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                question,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (isOpen) "‹" else "›",
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                color = Muted,
            )
        }
        if (isOpen) {
            Text(
                answer,
                fontSize = 13.sp,
                lineHeight = 22.sp,
                color = Muted,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

/** Icon-in-disc used by word rows (bookmark / speaker). */
@Composable
fun IconButtonGhost(
    res: Int,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    tintActive: Boolean = false,
) {
    Box(
        modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Image(
            painterResource(res),
            contentDesc,
            modifier = Modifier.size(size * 0.62f)
                .graphicsLayer { alpha = if (tintActive) 1f else 0.9f },
        )
    }
}

/** شمارنده قلب‌ها — the lives row of learn/quiz sessions. */
@Composable
fun HeartsRow(lives: Int, total: Int = 3, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            androidx.compose.foundation.Image(
                painterResource(R.drawable.ki_g_heart),
                null,
                modifier = Modifier
                    .size(30.dp)
                    .graphicsLayer { alpha = if (i < lives) 1f else 0.28f },
            )
        }
    }
}

/** Progress dots for carousel/stack positions (active = gold capsule). */
@Composable
fun ProgressDots(count: Int, active: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            Box(
                Modifier
                    .width(if (i == active) 22.dp else 7.dp)
                    .height(7.dp)
                    .background(if (i == active) Gold else Line, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** TrackBrown tube with gold fill — the folder-card progress (kept from home). */
@Composable
fun FolderTube(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(11.dp).background(TrackBrown, RoundedCornerShape(6.dp))) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                .fillMaxHeight()
                .background(CapsuleGradient, RoundedCornerShape(6.dp)),
        )
    }
}
