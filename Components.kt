package com.nearchat.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearchat.app.R
import com.nearchat.app.core.Media
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun formatSize(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
    b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
    else -> "%.2f GB".format(b / 1073741824.0)
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .height(56.dp)
            .shadow(if (enabled) 10.dp else 0.dp, shape, ambientColor = Electric, spotColor = Electric)
            .clip(shape)
            .background(if (enabled) brand else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

/** NearChat logo (circular), with a small reveal animation. */
@Composable
fun LogoMark(size: Int = 44, animate: Boolean = true) {
    val reveal = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = "NearChat",
        modifier = Modifier
            .size(size.dp)
            .graphicsLayer {
                alpha = reveal.value
                scaleX = 0.6f + 0.4f * reveal.value
                scaleY = 0.6f + 0.4f * reveal.value
                rotationZ = (1f - reveal.value) * -25f
            }
            .clip(CircleShape),
    )
}

/** Round avatar with the first letter of the name. */
@Composable
fun Avatar(name: String, size: Int = 52, online: Boolean = false) {
    Box(Modifier.size(size.dp)) {
        Box(Modifier.fillMaxSize().clip(CircleShape).background(brand), contentAlignment = Alignment.Center) {
            Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size * 0.42f).sp)
        }
        if (online) Box(Modifier.align(Alignment.BottomEnd)) { StatusDot(true, 13) }
    }
}

/** Lightweight radar: pulsing rings drawn on a Canvas, no heavy rendering. */
@Composable
fun Radar(modifier: Modifier = Modifier, active: Boolean, size: Int = 220) {
    val t = rememberInfiniteTransition(label = "radar")
    val p by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (active) 1500 else 3500, easing = LinearEasing), RepeatMode.Restart),
        label = "p",
    )
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxR = this.size.minDimension / 2
            for (i in 0..2) {
                val f = (p + i / 3f) % 1f
                drawCircle(
                    color = Electric.copy(alpha = (1f - f) * 0.35f),
                    radius = maxR * (0.3f + 0.7f * f),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
            drawCircle(brush = Brush.linearGradient(listOf(Electric, Violet)), radius = maxR * 0.28f)
        }
        Icon(Icons.Default.Bluetooth, null, tint = Color.White, modifier = Modifier.size((size * 0.15f).dp))
    }
}

@Composable
fun StatusDot(on: Boolean, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(if (on) Online else Offline))
}

/** Decodes a (small) image file off the main thread. */
@Composable
fun rememberBitmap(path: String?, maxDim: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, path, maxDim) {
        value = if (path == null) null else withContext(Dispatchers.IO) { Media.decodeFile(path, maxDim)?.asImageBitmap() }
    }
    return state.value
}
