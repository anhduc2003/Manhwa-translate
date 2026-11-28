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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.manhwatranslate.ui.components.AppLogo
import com.manhwatranslate.ui.theme.LocalAccents

@Composable
fun SplashScreen(modifier: Modifier = Modifier) {
    val accents = LocalAccents.current
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val logoScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.65f,
        animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing),
        label = "logoScale",
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 650),
        label = "logoAlpha",
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = 650, delayMillis = 220),
        label = "textAlpha",
    )

    val infinite = rememberInfiniteTransition(label = "splashPulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        // Quầng ánh sáng tím-hồng mềm phía sau logo
        Box(
            modifier = Modifier
                .size(300.dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            accents.heroMiddle.copy(alpha = 0.34f * pulse),
                            accents.heroStart.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            LogoMark(
                scale = logoScale,
                alpha = logoAlpha,
                pulse = pulse,
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Manhwa Translate",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Dịch truyện trong một chạm",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha },
            )
        }
    }
}

@Composable
private fun LogoMark(scale: Float, alpha: Float, pulse: Float) {
    val accents = LocalAccents.current
    Box(
        modifier = Modifier.size(144.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.linearGradient(listOf(accents.glowStart, accents.glowEnd)),
                radius = size.minDimension * 0.46f * pulse,
                center = center,
                style = Stroke(width = size.minDimension * 0.02f),
                alpha = 0.75f * pulse,
            )
        }
        AppLogo(
            size = 118.dp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            },
        )
    }
}


