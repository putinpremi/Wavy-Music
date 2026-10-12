package com.wavymusic.app.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal object WavyColors {
    val Background = Color(0xFF080D19)
    val Surface = Color(0xFF172235)
    val SurfaceElevated = Color(0xFF202E45)

    val Cyan = Color(0xFF48E5FF)
    val Violet = Color(0xFF9272FF)

    val PrimaryText = Color(0xFFF4F7FF)
    val SecondaryText = Color(0xFFB9C6DC)
    val MutedText = Color(0xFF8F9DB5)

    val Error = Color(0xFFFF8F9A)
    val Success = Color(0xFF8BE9C0)
}

@Composable
fun WavyTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = darkColorScheme(
        primary = WavyColors.Cyan,
        onPrimary = WavyColors.Background,
        secondary = WavyColors.Violet,
        onSecondary = WavyColors.PrimaryText,
        background = WavyColors.Background,
        onBackground = WavyColors.PrimaryText,
        surface = WavyColors.Surface,
        onSurface = WavyColors.PrimaryText,
        surfaceVariant = WavyColors.SurfaceElevated,
        onSurfaceVariant = WavyColors.SecondaryText,
        error = WavyColors.Error,
        onError = WavyColors.Background
    )

    val shapes = Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(30.dp)
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = shapes,
        content = content
    )
}

@Composable
fun WavyWaveMark(
    modifier: Modifier = Modifier,
    description: String = "Wavy Music wave mark"
) {
    Canvas(
        modifier = modifier.semantics {
            contentDescription = description
            role = Role.Image
        }
    ) {
        drawWaveMark(size)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveMark(
    canvasSize: Size
) {
    val w = canvasSize.width
    val h = canvasSize.height

    if (w <= 0f || h <= 0f) return

    val strokeWidth = minOf(w, h) * 0.075f

    val upperWave = Path().apply {
        moveTo(w * 0.05f, h * 0.51f)

        cubicTo(
            w * 0.15f, h * 0.51f,
            w * 0.16f, h * 0.22f,
            w * 0.28f, h * 0.22f
        )

        cubicTo(
            w * 0.39f, h * 0.22f,
            w * 0.40f, h * 0.77f,
            w * 0.52f, h * 0.77f
        )

        cubicTo(
            w * 0.65f, h * 0.77f,
            w * 0.66f, h * 0.12f,
            w * 0.78f, h * 0.12f
        )

        cubicTo(
            w * 0.86f, h * 0.12f,
            w * 0.88f, h * 0.51f,
            w * 0.95f, h * 0.51f
        )
    }

    val lowerWave = Path().apply {
        moveTo(w * 0.05f, h * 0.69f)

        cubicTo(
            w * 0.18f, h * 0.69f,
            w * 0.18f, h * 0.47f,
            w * 0.29f, h * 0.47f
        )

        cubicTo(
            w * 0.41f, h * 0.47f,
            w * 0.41f, h * 0.88f,
            w * 0.53f, h * 0.88f
        )

        cubicTo(
            w * 0.66f, h * 0.88f,
            w * 0.67f, h * 0.43f,
            w * 0.78f, h * 0.43f
        )

        cubicTo(
            w * 0.87f, h * 0.43f,
            w * 0.88f, h * 0.69f,
            w * 0.95f, h * 0.69f
        )
    }

    drawPath(
        path = lowerWave,
        color = WavyColors.Violet,
        style = Stroke(
            width = strokeWidth * 0.75f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )

    drawPath(
        path = upperWave,
        color = WavyColors.Cyan,
        style = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}