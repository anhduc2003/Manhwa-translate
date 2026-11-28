package com.manhwatranslate.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Bộ thông số tuỳ biến riêng cho Hero Card.
 * Có thể tạo nhiều preset (Tím-San hô, Xanh-Tím, Đỏ-Đen...) rồi truyền vào CustomManhwaHeroCard.
 */
@Immutable
data class HeroStyleConfig(
    val cornerRadius: Dp = 28.dp,
    val gradientColors: List<Color> = listOf(
        Color(0xFF331D60), // Tím đậm góc trái
        Color(0xFF8B2464), // Hồng tím ở giữa
        Color(0xFFE87070), // Hồng cam san hô góc phải
    ),
    val borderColors: List<Color> = listOf(
        Color(0x998C5CE6),
        Color(0x88E04F8E),
        Color(0xCCFF9B85),
    ),
    val titleColor: Color = Color(0xFFFFF5F8),
    val subtitleColor: Color = Color(0xDDFCEAF2),
    val primaryBtnBg: Color = Color(0xFFFBE9EE),
    val primaryBtnContent: Color = Color(0xFF471A2B),
    val secondaryBtnBg: Color = Color(0x5273284D),
    val secondaryBtnBorder: Color = Color(0x80FFFFFF),
    val secondaryBtnContent: Color = Color(0xFFFFFFFF),
    /** Độ rõ của ảnh truyện nền bên phải (0f = ẩn, 0.35f..0.55f = đẹp nhất). */
    val artAlpha: Float = 0.42f,
    /** Chuyển ảnh truyện về đơn sắc trước khi phủ màu để không bị chỏi màu nền. */
    val monochromeArt: Boolean = true,
    /** Chiếm tỷ lệ chiều ngang bên phải của thẻ (0.55f..0.75f). */
    val artWidthFraction: Float = 0.65f,
)

@Composable
fun CustomManhwaHeroCard(
    onPickImages: () -> Unit,
    onCapture: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Dịch truyện\ntrong một chạm",
    subtitle: String = "Bản dịch được vẽ thẳng vào bong bóng thoại.",
    /**
     * Truyền R.drawable.bg_manhwa_collage, File, Uri hoặc URL ảnh.
     * Nếu null, thẻ sẽ tự vẽ họa tiết khung truyện mờ mặc định.
     */
    artModel: Any? = null,
    style: HeroStyleConfig = HeroStyleConfig(),
) {
    val shape = RoundedCornerShape(style.cornerRadius)
    val grayscaleFilter = remember(style.monochromeArt) {
        if (style.monochromeArt) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = style.gradientColors,
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, 220f),
                ),
            )
            .border(
                width = 1.2.dp,
                brush = Brush.horizontalGradient(style.borderColors),
                shape = shape,
            ),
    ) {
        // 1. Lớp ảnh nền Manhwa bên phải (hòa mờ dần từ trái sang phải)
        if (artModel != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    .drawWithContent {
                        drawContent()
                        // Mặt nạ fade mượt từ trong suốt (bên trái) sang hiện rõ (bên phải)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                0.0f to Color.Transparent,
                                0.34f to Color.Transparent,
                                0.62f to Color.Black.copy(alpha = 0.65f),
                                1.0f to Color.Black,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) {
                AsyncImage(
                    model = artModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.CenterEnd,
                    colorFilter = grayscaleFilter,
                    alpha = style.artAlpha,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(style.artWidthFraction),
                )
            }

            // Lớp phủ tint ấm đồng bộ ảnh truyện với nền hồng san hô
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            0.0f to Color.Transparent,
                            0.50f to Color(0x28A62E6B),
                            1.0f to Color(0x52FF7E75),
                        ),
                    ),
            )
        } else {
            FallbackManhwaPanels(modifier = Modifier.matchParentSize())
        }

        // 2. Ánh sáng ấm nhẹ góc phải trên (tạo chiều sâu như bản mẫu)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x45FFA38F), Color.Transparent),
                        center = Offset(900f, 80f),
                        radius = 520f,
                    ),
                ),
        )

        // 3. Nội dung chữ & 2 nút bấm
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 22.dp),
        ) {
            Text(
                text = title,
                color = style.titleColor,
                fontSize = 29.sp,
                lineHeight = 35.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = subtitle,
                color = style.subtitleColor,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
            )

            Spacer(Modifier.height(22.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Nút trái: Chọn ảnh (nền kem hồng nổi bật)
                Button(
                    onClick = onPickImages,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = style.primaryBtnBg,
                        contentColor = style.primaryBtnContent,
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 2.dp,
                        pressedElevation = 0.dp,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chọn ảnh",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Nút phải: Chụp ảnh (kính mờ + viền sáng)
                OutlinedButton(
                    onClick = onCapture,
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.2.dp, style.secondaryBtnBorder),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = style.secondaryBtnBg,
                        contentColor = style.secondaryBtnContent,
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PhotoCamera,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Chụp ảnh",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/** Họa tiết khung truyện dự phòng khi chưa gắn ảnh drawable/thư viện. */
@Composable
private fun FallbackManhwaPanels(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Color.White.copy(alpha = 0.14f)
        val fill = Color.White.copy(alpha = 0.06f)
        drawRoundRect(
            color = fill,
            topLeft = Offset(size.width * 0.56f, -16f),
            size = Size(size.width * 0.24f, size.height * 0.56f),
            cornerRadius = CornerRadius(18f, 18f),
        )
        drawRoundRect(
            color = stroke,
            topLeft = Offset(size.width * 0.56f, -16f),
            size = Size(size.width * 0.24f, size.height * 0.56f),
            cornerRadius = CornerRadius(18f, 18f),
            style = Stroke(width = 2.5f),
        )
        drawRoundRect(
            color = fill,
            topLeft = Offset(size.width * 0.82f, 18f),
            size = Size(size.width * 0.24f, size.height * 0.44f),
            cornerRadius = CornerRadius(16f, 16f),
        )
        drawOval(
            color = Color.White.copy(alpha = 0.12f),
            topLeft = Offset(size.width * 0.68f, size.height * 0.18f),
            size = Size(size.width * 0.16f, size.height * 0.22f),
        )
    }
}
