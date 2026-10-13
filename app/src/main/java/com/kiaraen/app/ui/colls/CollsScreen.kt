package com.kiaraen.app.ui.colls

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kiaraen.app.R
import com.kiaraen.app.ui.common.LocalAppContainer
import com.kiaraen.app.ui.kiara.DetailHeader
import com.kiaraen.app.ui.kiara.faNum
import com.kiaraen.app.ui.theme.Gold
import com.kiaraen.app.ui.theme.Ink
import com.kiaraen.app.ui.theme.Muted
import com.kiaraen.app.ui.theme.Paper
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn

/**
 * همه مجموعه‌ها — the approved #colls screen: a 3-per-row grid of the 16
 * topic books. Tap the card → open it; tap the bookmark → mark/unmark it
 * as «مجموعه‌های من» (gold ring), the header counter updates live.
 */
@Composable
fun CollsScreen(
    onBack: () -> Unit,
    onOpenCollection: (Long) -> Unit,
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val collections by remember { container.database.contentDao().collections() }
        .collectAsState(initial = emptyList())
    val marked by remember { container.appPrefs.markedCollections }.collectAsState(initial = emptySet())

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
                title = "همه مجموعه‌ها",
                subtitle = "${faNum(marked.size)} نشان‌شده",
                onBack = onBack,
            )
            Text(
                "روی نشانک هر مجموعه بزن تا بره تو «مجموعه‌های من».",
                fontSize = 12.sp,
                lineHeight = 20.sp,
                color = Muted,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
            ) {
                items(collections, key = { it.id }) { col ->
                    val isMarked = col.id.toString() in marked
                    val idx = collections.indexOfFirst { it.id == col.id }
                    CollCell(
                        title = col.title,
                        coverRes = com.kiaraen.app.ui.words.topicCoverRes(idx),
                        marked = isMarked,
                        onToggle = {
                            scope.launch { runCatching { container.appPrefs.toggleMarkedCollection(col.id) } }
                        },
                        onOpen = { onOpenCollection(col.id) },
                    )
                }
            }
    
        }
    }
}

/** One grid cell: topic-book cover + name + count; gold ring when marked. */
@Composable
private fun CollCell(
    title: String,
    coverRes: Int,
    marked: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .then(
                if (marked) Modifier.border(2.5.dp, Gold, RoundedCornerShape(20.dp))
                else Modifier,
            )
            .clickable { onOpen() }
            .padding(10.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(coverRes),
                null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.05f),
            )
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            CellCount(title = title, modifier = Modifier.padding(top = 2.dp))
        }
        Image(
            painterResource(R.drawable.ki_ui_bookmark),
            null,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(2.dp)
                .size(24.dp)
                .alpha(if (marked) 1f else 0.4f)
                .clickable { onToggle() },
        )
    }
}

/** Per-cell word count (Room flow is cheap; 16 collectors are fine). */
@Composable
private fun CellCount(title: String, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val collections by remember { container.database.contentDao().collections() }
        .collectAsState(initial = emptyList())
    val id = collections.firstOrNull { it.title == title }?.id ?: return
    val size by remember(id) { container.database.contentDao().collectionSize(id) }
        .collectAsState(initial = 0)
    Text(
        "${faNum(size)} واژه",
        fontSize = 10.sp,
        color = Muted,
        modifier = modifier,
    )
}
