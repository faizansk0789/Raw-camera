package com.example.procam.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.procam.model.GridType
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.FocusCyan
import com.example.ui.theme.FocusGreen
import com.example.ui.theme.RecordRed
import com.example.ui.theme.WarningYellow
import kotlin.math.abs

@Composable
fun ViewfinderGrid(
    gridType: GridType,
    modifier: Modifier = Modifier
) {
    if (gridType == GridType.NONE) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        when (gridType) {
            GridType.RULE_OF_THIRDS -> {
                val lineColor = Color(0x55FFFFFF)
                val strokeW = 1.dp.toPx()

                // Vertical lines
                drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = strokeW)
                drawLine(lineColor, Offset(2f * w / 3f, 0f), Offset(2f * w / 3f, h), strokeWidth = strokeW)

                // Horizontal lines
                drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = strokeW)
                drawLine(lineColor, Offset(0f, 2f * h / 3f), Offset(w, 2f * h / 3f), strokeWidth = strokeW)

                // Center crosshair
                val cx = w / 2f
                val cy = h / 2f
                val chLen = 12.dp.toPx()
                drawLine(AccentAmber, Offset(cx - chLen, cy), Offset(cx + chLen, cy), strokeWidth = 1.5.dp.toPx())
                drawLine(AccentAmber, Offset(cx, cy - chLen), Offset(cx, cy + chLen), strokeWidth = 1.5.dp.toPx())
            }

            GridType.GOLDEN_RATIO -> {
                val lineColor = Color(0x44FFFFFF)
                val strokeW = 1.dp.toPx()
                val phi = 0.618f
                val invPhi = 1f - phi

                drawLine(lineColor, Offset(w * invPhi, 0f), Offset(w * invPhi, h), strokeWidth = strokeW)
                drawLine(lineColor, Offset(w * phi, 0f), Offset(w * phi, h), strokeWidth = strokeW)
                drawLine(lineColor, Offset(0f, h * invPhi), Offset(w, h * invPhi), strokeWidth = strokeW)
                drawLine(lineColor, Offset(0f, h * phi), Offset(w, h * phi), strokeWidth = strokeW)
            }

            GridType.CINEMATIC_16_9, GridType.CINEMATIC_2_35, GridType.CINEMATIC_2_39 -> {
                val targetRatio = when (gridType) {
                    GridType.CINEMATIC_16_9 -> 16f / 9f
                    GridType.CINEMATIC_2_35 -> 2.35f
                    else -> 2.39f
                }
                val currentRatio = w / h
                val matteColor = Color(0xBB000000)

                if (currentRatio < targetRatio) {
                    // Pillar/letterbox on top & bottom
                    val activeH = w / targetRatio
                    val barH = (h - activeH) / 2f
                    drawRect(matteColor, Offset(0f, 0f), Size(w, barH))
                    drawRect(matteColor, Offset(0f, h - barH), Size(w, barH))
                    drawLine(Color(0x88FFFFFF), Offset(0f, barH), Offset(w, barH), strokeWidth = 1.dp.toPx())
                    drawLine(Color(0x88FFFFFF), Offset(0f, h - barH), Offset(w, h - barH), strokeWidth = 1.dp.toPx())
                }
            }

            else -> {}
        }
    }
}

@Composable
fun ElectronicLevelHorizon(
    rollDegrees: Float,
    pitchDegrees: Float,
    modifier: Modifier = Modifier
) {
    val isLevel = abs(rollDegrees) <= 1.0f
    val lineColor = if (isLevel) FocusGreen else Color(0x99FFFFFF)

    Box(
        modifier = modifier
            .size(160.dp, 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val lineLength = 50.dp.toPx()

            // Center static reference notches
            drawCircle(Color(0x44FFFFFF), radius = 3.dp.toPx(), center = Offset(cx, cy))
            drawLine(Color(0x66FFFFFF), Offset(cx - 30.dp.toPx(), cy), Offset(cx - 15.dp.toPx(), cy), strokeWidth = 2.dp.toPx())
            drawLine(Color(0x66FFFFFF), Offset(cx + 15.dp.toPx(), cy), Offset(cx + 30.dp.toPx(), cy), strokeWidth = 2.dp.toPx())

            // Dynamic roll line
            val rollRad = Math.toRadians(rollDegrees.toDouble())
            val dx = (lineLength * Math.cos(rollRad)).toFloat()
            val dy = (lineLength * Math.sin(rollRad)).toFloat()

            drawLine(
                color = lineColor,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = if (isLevel) 3.dp.toPx() else 1.5.dp.toPx()
            )
        }

        Text(
            text = "${if (rollDegrees >= 0) "+" else ""}${String.format("%.1f", rollDegrees)}°",
            color = if (isLevel) FocusGreen else Color.White,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0x66000000), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun LiveHistogram(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(110.dp, 56.dp)
            .background(Color(0x8811141B), RoundedCornerShape(6.dp))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(4.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val steps = 24

            val path = Path().apply {
                moveTo(0f, h)
                for (i in 0..steps) {
                    val x = (i.toFloat() / steps) * w
                    // Typical bell curve with shadow & highlight rolloff
                    val norm = (i.toFloat() - 12f) / 6f
                    val bell = Math.exp(-0.5 * norm * norm).toFloat()
                    val y = h - (bell * (h * 0.85f))
                    lineTo(x, y)
                }
                lineTo(w, h)
                close()
            }

            drawPath(path, color = Color(0x55FFFFFF))
            drawPath(path, color = Color(0xDDFFFFFF), style = Stroke(width = 1.5.dp.toPx()))

            // Zone lines: Shadows, Midtones, Highlights
            drawLine(Color(0x33FFFFFF), Offset(w * 0.25f, 0f), Offset(w * 0.25f, h), strokeWidth = 0.5.dp.toPx())
            drawLine(Color(0x33FFFFFF), Offset(w * 0.75f, 0f), Offset(w * 0.75f, h), strokeWidth = 0.5.dp.toPx())
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 2.dp, top = 2.dp)
        ) {
            Text("HIST", fontSize = 9.sp, color = AccentAmber, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun TapFocusIndicator(
    focusPoint: Offset?,
    modifier: Modifier = Modifier
) {
    if (focusPoint == null) return

    val scale = remember { Animatable(1.4f) }
    LaunchedEffect(focusPoint) {
        scale.snapTo(1.4f)
        scale.animateTo(1.0f, animationSpec = tween(220))
    }

    Box(
        modifier = modifier
            .offset { IntOffset(focusPoint.x.toInt() - 36, focusPoint.y.toInt() - 36) }
            .size(72.dp)
            .border(1.5.dp, FocusCyan, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(FocusCyan, CircleShape)
        )
    }
}

@Composable
fun RecordingBadge(
    durationSec: Long,
    modifier: Modifier = Modifier
) {
    val minutes = durationSec / 60
    val seconds = durationSec % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    Row(
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(16.dp))
            .border(1.dp, RecordRed, RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(RecordRed, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "REC $timeFormatted",
            color = Color.White,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold
        )
    }
}
