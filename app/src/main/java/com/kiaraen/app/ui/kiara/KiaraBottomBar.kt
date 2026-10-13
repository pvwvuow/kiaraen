package com.kiaraen.app.ui.kiara

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.theme.Ink

data class KiaraTab(
    val route: String,
    val label: String,
    val iconRes: Int,
)

/**
 * The four approved tabs. Icons are the exact 3D assets extracted from
 * kiara-app.html (`.nitem .ni` / `.nitem .nic .ic` — one image per tab, shown
 * plain when inactive and over the gold square when active).
 */
val KIARA_TABS = listOf(
    KiaraTab("home", "خانه", R.drawable.ki2_nav_home),
    KiaraTab("words", "واژه‌ها", R.drawable.ki2_nav_words),
    KiaraTab("progress", "پیشرفت", R.drawable.ki2_nav_progress),
    KiaraTab("profile", "من", R.drawable.ki2_nav_profile),
)

private val NavLabel = Color(0xFF75705F)
private val PanelCream = Color(0xFFFAF9F6)

/**
 * The approved floating bottom bar, geometry copied verbatim from
 * kiara-app.html (native 853-space ÷ 2.188 → dp):
 *   panel #FAF9F6, radius 44→20dp, bar height 180→82dp;
 *   inactive icon 98→45dp + label 27→12sp #75705F;
 *   active: gold square 126→58dp with the icon 86→39dp on top, label ink 800.
 * Fluid: width fills the screen with equal weights, so it stays correct on
 * any device size (the responsive fix of the absolute-positioned prototype).
 */
@Composable
fun KiaraBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0x26786946),
                spotColor = Color(0x26786946),
            )
            .background(PanelCream, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KIARA_TABS.forEach { tab ->
            TabItem(
                tab = tab,
                selected = currentRoute?.startsWith(tab.route) == true,
                onClick = { onSelect(tab.route) },
                modifier = Modifier.testTag("tab_${tab.route}"),
            )
        }
    }
}

@Composable
private fun RowScope.TabItem(tab: KiaraTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .weight(1f)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (selected) {
            // gold square (the exact nav_square asset) + icon overlay, 58dp
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(R.drawable.ki2_nav_square),
                    null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Image(
                    painterResource(tab.iconRes),
                    tab.label,
                    modifier = Modifier.size(38.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painterResource(tab.iconRes),
                    tab.label,
                    modifier = Modifier.size(42.dp),
                )
            }
        }
        Spacer(Modifier.height(1.dp))
        Text(
            tab.label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
            color = if (selected) Ink else NavLabel,
            maxLines = 1,
        )
    }
}
