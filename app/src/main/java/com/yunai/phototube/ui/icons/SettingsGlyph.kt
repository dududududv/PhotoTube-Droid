package com.yunai.phototube.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.theme.SettingsSymbol
import com.yunai.phototube.ui.theme.SettingsIllustrationColors as Paint

/** Small layered illustrations, drawn at a consistent 32-unit optical size. */
@Composable
fun SettingsGlyph(symbol: SettingsSymbol) {
    val shape = RoundedCornerShape(9.dp)
    Canvas(Modifier.size(32.dp).clip(shape)
        .background(Brush.verticalGradient(listOf(symbol.top, symbol.bottom)))
        .border(.5.dp, Brush.verticalGradient(listOf(Paint.ink.copy(.35f), Paint.ink.copy(.04f))), shape)) {
        scale(size.width / 32f, size.height / 32f, pivot = Offset.Zero) {
            when(symbol) {
                SettingsSymbol.FAVORITES -> {
                    val heart = Path().apply {
                        moveTo(16f, 25f); cubicTo(13f, 22f, 6f, 17f, 6f, 12f)
                        cubicTo(6f, 6f, 13f, 5f, 16f, 10f)
                        cubicTo(19f, 5f, 26f, 6f, 26f, 12f)
                        cubicTo(26f, 17f, 19f, 22f, 16f, 25f); close()
                    }
                    drawPath(heart, Brush.verticalGradient(listOf(Paint.ink, Paint.rose), endY = 25f))
                    drawOval(Paint.ink.copy(.4f), Offset(9f, 8f), Size(5f, 3f))
                }
                SettingsSymbol.CALENDAR -> {
                    drawRoundRect(Paint.shade, Offset(7f, 9f), Size(20f, 19f), CornerRadius(4f))
                    drawRoundRect(Paint.ink, Offset(6f, 7f), Size(20f, 19f), CornerRadius(4f))
                    drawRoundRect(Paint.cool, Offset(6f, 7f), Size(20f, 6f), CornerRadius(3f))
                    for(x in listOf(11f,21f)) drawLine(Paint.ink, Offset(x, 5.5f), Offset(x, 9f), 2f, StrokeCap.Round)
                    for(y in listOf(17f,21f)) for(x in listOf(11f,16f,21f)) drawCircle(symbol.bottom.copy(.8f), 1.1f, Offset(x,y))
                }
                SettingsSymbol.HAPTICS -> {
                    drawRoundRect(Paint.ink.copy(.92f), Offset(11f, 6f), Size(10f, 20f), CornerRadius(3f))
                    drawRoundRect(symbol.bottom.copy(.6f), Offset(13f, 9f), Size(6f, 12f), CornerRadius(1f))
                    drawCircle(Paint.ink, .9f, Offset(16f,23f))
                    drawArc(Paint.ink.copy(.8f), 140f, 80f, false, Offset(5f,10f), Size(7f,12f), style = Stroke(1.5f, cap = StrokeCap.Round))
                    drawArc(Paint.ink.copy(.8f), -40f, 80f, false, Offset(20f,10f), Size(7f,12f), style = Stroke(1.5f, cap = StrokeCap.Round))
                }
                SettingsSymbol.APPEARANCE -> {
                    drawCircle(Paint.shade, 8f, Offset(17f,20f))
                    drawCircle(Paint.cool, 8f, Offset(12f,17f))
                    drawCircle(Paint.rose.copy(.9f), 8f, Offset(20f,17f))
                    drawCircle(Paint.warm.copy(.9f), 7f, Offset(16f,12f))
                    drawCircle(Paint.ink.copy(.65f), 3f, Offset(16f,15f))
                }
                SettingsSymbol.VIDEO -> {
                    drawRoundRect(Paint.ink.copy(.4f), Offset(7f,6f), Size(18f,18f), CornerRadius(4f))
                    drawRoundRect(Paint.ink, Offset(5f,9f), Size(22f,17f), CornerRadius(4f))
                    drawPath(Path().apply { moveTo(14f,13f); lineTo(21f,17.5f); lineTo(14f,22f); close() }, symbol.bottom)
                }
                SettingsSymbol.ABOUT -> {
                    drawRoundRect(Paint.ink.copy(.3f), Offset(8f,5f), Size(18f,19f), CornerRadius(4f))
                    drawRoundRect(Paint.ink, Offset(5f,8f), Size(21f,19f), CornerRadius(4f))
                    drawCircle(Paint.warm, 2.4f, Offset(20f,14f))
                    drawPath(Path().apply { moveTo(8f,23f); lineTo(13f,16f); lineTo(17f,21f); lineTo(20f,18f); lineTo(24f,23f); close() }, symbol.bottom)
                }
            }
        }
    }
}
