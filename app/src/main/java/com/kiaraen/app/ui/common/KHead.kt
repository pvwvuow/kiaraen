package com.kiaraen.app.ui.common

import android.content.res.Resources
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.kiaraen.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Low-end friendly bitmap loading. painterResource() decodes the FULL image
 * on the main thread every time the screen enters composition — for the
 * 295 KB mascot that is megabytes of pixels and a visible stall on weak
 * phones. Here: decode once, off the main thread, downsampled to the size
 * actually drawn, and keep it in a tiny process-wide cache.
 */
private object BitmapCache {
    val cache = LruCache<String, ImageBitmap>(6)
}

@Composable
fun rememberDownsampledImage(@DrawableRes res: Int, size: Dp): ImageBitmap? {
    val resources = LocalContext.current.resources
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val key = "$res@$px"
    return produceState(initialValue = BitmapCache.cache.get(key), key) {
        if (value == null) {
            value = withContext(Dispatchers.IO) { decodeSampled(resources, res, px) }
                ?.also { BitmapCache.cache.put(key, it) }
        }
    }.value
}

private fun decodeSampled(resources: Resources, @DrawableRes res: Int, reqPx: Int): ImageBitmap? =
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeResource(resources, res, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= reqPx && bounds.outHeight / (sample * 2) >= reqPx) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeResource(resources, res, opts)?.asImageBitmap()
    }.getOrNull()

/** The Kiara mascot head, sized and cached for weak phones. */
@Composable
fun KHead(size: Dp, modifier: Modifier = Modifier) {
    val image = rememberDownsampledImage(R.drawable.khead, size)
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Spacer(modifier.size(size))   // same footprint while decoding → no layout jump
    }
}
