package com.kiaraen.app.ui.kiara

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.ui.theme.Baloo
import com.kiaraen.app.ui.theme.CardCream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.GoldShade
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Line
import com.kiaraen.app.ui.theme.Muted

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** Latin digits → Persian digits with the ٬ group separator (approved style). */
fun faNum(v: Long): String {
    val grouped = java.text.DecimalFormat("#,###").format(v)
    val sb = StringBuilder(grouped.length)
    for (c in grouped) {
        sb.append(
            when {
                c.isDigit() -> PERSIAN_DIGITS[c - '0']
                c == ',' -> '٬'
                else -> c
            },
        )
    }
    return sb.toString()
}

fun faNum(v: Int): String = faNum(v.toLong())

/** The «kiara» logotype (Baloo 2 ExtraBold, ink). */
@Composable
fun KiaraLogotype(modifier: Modifier = Modifier, size: TextUnit = 30.sp, color: Color = Ink) {
    Text(
        "kiara",
        fontFamily = Baloo,
        fontWeight = FontWeight.Black,
        fontSize = size,
        letterSpacing = (-0.4).sp,
        color = color,
        modifier = modifier,
    )
}

/**
 * Clay button (دکمه پف‌دار): gold pill sitting on a hard darker-gold base —
 * exactly the approved «ادامه بده» button.
 */
@Composable
fun ClayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    fontSize: TextUnit = 16.sp,
    container: Color = Gold,
    base: Color = GoldShade,
    contentColor: Color = Ink,
    leading: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(percent = 50)
    Box(modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .background(base, shape)
                .align(Alignment.BottomCenter),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height - 5.dp)
                .background(container, shape)
                .clickable { onClick() }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            leading?.invoke(this)
            Spacer(Modifier.width(6.dp))
            Text(
                text,
                color = contentColor,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Cream rounded card (کارت خمیری روشن) — the base container of the design. */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    corner: Dp = 24.dp,
    fill: Color = CardCream,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(fill, RoundedCornerShape(corner))
            .padding(16.dp),
        content = content,
    )
}

/** Small gold pill (پیل «مرور»). */
@Composable
fun GoldPill(
    text: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
) {
    Box(
        modifier = modifier
            .background(Gold, RoundedCornerShape(percent = 50))
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(text, fontSize = fontSize, fontWeight = FontWeight.Black, color = Ink)
    }
}

/** Section header: bold title (right in RTL), optional muted action (left). */
@Composable
fun SectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Black, color = Ink)
        Spacer(Modifier.weight(1f))
        if (actionText != null) {
            Text(
                actionText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Muted,
                modifier = Modifier.clickable(enabled = onAction != null) { onAction?.invoke() },
            )
        }
    }
}

/** Thin hairline used between rows/cards. */
@Composable
fun KiaraDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Line, RoundedCornerShape(1.dp)),
    )
}

/** A design icon (PNG from the approved collection sheet). */
@Composable
fun KiIcon(res: Int, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painterResource(res),
        null,
        modifier = modifier.size(size),
    )
}

/** «به‌زودی» toast for rows whose feature ships later. */
fun showSoon(context: android.content.Context) {
    android.widget.Toast.makeText(context, "به‌زودی ✨", android.widget.Toast.LENGTH_SHORT).show()
}
