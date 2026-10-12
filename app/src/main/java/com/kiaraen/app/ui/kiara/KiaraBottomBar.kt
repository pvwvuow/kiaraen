package com.kiaraen.app.ui.kiara

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.theme.Cream
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted

data class KiaraTab(
    val route: String,
    val label: String,
    val iconRes: Int,
)

val KIARA_TABS = listOf(
    KiaraTab("home", "خانه", R.drawable.ki_house),
    KiaraTab("words", "واژه‌ها", R.drawable.ki_words),
    KiaraTab("progress", "پیشرفت", R.drawable.ki_trophy),
    KiaraTab("profile", "من", R.drawable.ki_person),
)

/**
 * The approved floating bottom bar: cream pill, four 3D icons, active item sits
 * on a gold rounded square. Labels always visible below (approved geometry).
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
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .background(Cream, RoundedCornerShape(30.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
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
    Column(
        modifier = modifier
            .weight(1f)
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(54.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .size(54.dp)
                        .background(Gold, RoundedCornerShape(16.dp)),
                )
            }
            Image(
                painterResource(tab.iconRes),
                contentDescription = tab.label,
                modifier = Modifier.size(if (selected) 34.dp else 38.dp),
            )
        }
        Text(
            tab.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
            color = if (selected) Ink else Muted,
        )
    }
}
