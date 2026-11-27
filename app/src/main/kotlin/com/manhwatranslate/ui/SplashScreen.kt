package com.manhwatranslate.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manhwatranslate.ui.theme.Gold
import com.manhwatranslate.ui.theme.Terracotta
import com.manhwatranslate.ui.theme.Violet

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.6f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700),
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 250),
    )

    val infinite = rememberInfiniteTransition()
    val pulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(
                scale = logoScale,
                alpha = logoAlpha,
                pulse = pulse,
            )
            Spacer(Modifier.height(28.dp))
            Text(
                text = "Manhwa Translate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dịch truyện ngay trên máy",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
        }
    }
}

@Composable
private fun LogoMark(scale: Float, alpha: Float, pulse: Float) {
    Canvas(
        modifier = Modifier
            .size(140.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
    ) {
        val w = size.width
        val h = size.height

        // Speech bubble body
        val bubble = Path().apply {
            val r = w * 0.16f
            moveTo(w * 0.12f, h * 0.10f)
            lineTo(w * 0.88f, h * 0.10f)
            arcTo(
                Rect(Offset(w * 0.88f - r, h * 0.10f), Offset(w * 0.88f + r, h * 0.10f + r * 2)),
                0f,
                90f,
                false,
            )
            lineTo(w * 0.98f, h * 0.66f)
            arcTo(
                Rect(Offset(w * 0.98f - r, h * 0.66f - r), Offset(w * 0.98f + r, h * 0.66f + r)),
                0f,
                90f,
                false,
            )
            lineTo(w * 0.56f, h * 0.76f)
            lineTo(w * 0.42f, h * 0.94f)
            lineTo(w * 0.40f, h * 0.76f)
            lineTo(w * 0.12f, h * 0.76f)
            arcTo(
                Rect(Offset(w * 0.12f - r, h * 0.76f - r), Offset(w * 0.12f + r, h * 0.76f + r)),
                0f,
                90f,
                false,
            )
            close()
        }

        drawPath(
            path = bubble,
            brush = Brush.linearGradient(
                colors = listOf(Terracotta, Violet),
                start = Offset.Zero,
                end = Offset(w, h),
            ),
        )

        // Text lines inside bubble
        val lineColor = Color.White.copy(alpha = 0.92f)
        drawRoundRect(
            color = lineColor,
            topLeft = Offset(w * 0.24f, h * 0.30f),
            size = Size(w * 0.52f, h * 0.06f),
            cornerRadius = CornerRadius(w * 0.03f),
        )
        drawRoundRect(
            color = lineColor.copy(alpha = 0.7f),
            topLeft = Offset(w * 0.24f, h * 0.44f),
            size = Size(w * 0.38f, h * 0.06f),
            cornerRadius = CornerRadius(w * 0.03f),
        )

        // Sparkle
        val spark = Path().apply {
            moveTo(w * 0.78f, h * 0.16f)
            lineTo(w * 0.80f, h * 0.26f)
            lineTo(w * 0.90f, h * 0.28f)
            lineTo(w * 0.80f, h * 0.30f)
            lineTo(w * 0.78f, h * 0.40f)
            lineTo(w * 0.76f, h * 0.30f)
            lineTo(w * 0.66f, h * 0.28f)
            lineTo(w * 0.76f, h * 0.26f)
            close()
        }
        drawPath(spark, color = Gold, style = Stroke(width = w * 0.018f, cap = StrokeCap.Round))

        // Ring pulse
        drawCircle(
            color = Violet.copy(alpha = 0.18f * pulse),
            radius = w * 0.58f * pulse,
            center = Offset(w * 0.5f, h * 0.5f),
            style = Stroke(width = w * 0.02f),
        )
    }
}
