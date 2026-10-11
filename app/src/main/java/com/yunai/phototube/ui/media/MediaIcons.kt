package com.yunai.phototube.ui.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.yunai.phototube.ui.theme.MediaColors

internal enum class MediaSymbol { BACK, MORE, SHARE, HEART, INFO, TRASH, PLAY, PAUSE, SOUND, MUTED, GRID, EDIT, PHOTO, VIDEO, HEART_OFF, ARCHIVE, CALENDAR, FOLDER, TAG }
@Composable
internal fun MediaIcon(symbol: MediaSymbol, tint: Color = MediaColors.text, filled: Boolean = false) {
    Canvas(Modifier.size(23.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            fun line(a: Offset, b: Offset) = drawLine(tint, a, b, 1.7f, StrokeCap.Round)
            fun path(points: List<Offset>) { drawPath(Path().apply { moveTo(points[0].x,points[0].y); points.drop(1).forEach { lineTo(it.x,it.y) } }, tint, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            when(symbol) {
                MediaSymbol.EDIT -> {
                    listOf(6f to 8f,12f to 16f,18f to 10f).forEach {(y,x) ->
                        line(Offset(3f,y),Offset(x-3f,y));line(Offset(x+3f,y),Offset(21f,y))
                        drawCircle(tint,2.5f,Offset(x,y),style=Stroke(1.7f))
                    }
                }
                MediaSymbol.BACK -> path(listOf(Offset(15f,4f),Offset(7f,12f),Offset(15f,20f)))
                MediaSymbol.MORE -> listOf(5f,12f,19f).forEach { drawCircle(tint,1.5f,Offset(it,12f)) }
                MediaSymbol.SHARE -> {
                    drawPath(Path().apply {
                        moveTo(7f,9f);lineTo(6f,9f);quadraticTo(4.5f,9f,4.5f,10.5f)
                        lineTo(4.5f,18.5f);quadraticTo(4.5f,21f,7f,21f)
                        lineTo(17f,21f);quadraticTo(19.5f,21f,19.5f,18.5f)
                        lineTo(19.5f,10.5f);quadraticTo(19.5f,9f,18f,9f);lineTo(17f,9f)
                    },tint,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    line(Offset(12f,15f),Offset(12f,2f)); path(listOf(Offset(8f,6f),Offset(12f,2f),Offset(16f,6f)))
                }
                MediaSymbol.HEART, MediaSymbol.HEART_OFF -> { drawPath(Path().apply {
                    moveTo(12f,21f); cubicTo(8f,18f,2f,13f,2f,8f); cubicTo(2f,2f,9f,1f,12f,6f)
                    cubicTo(15f,1f,22f,2f,22f,8f); cubicTo(22f,13f,16f,18f,12f,21f); close()
                },tint,style = if(filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.7f, cap=StrokeCap.Round, join=StrokeJoin.Round))
                    if(symbol==MediaSymbol.HEART_OFF) line(Offset(3f,3f),Offset(21f,21f))
                }
                MediaSymbol.INFO -> {
                    drawCircle(tint,9f,Offset(12f,12f),style=if(filled) androidx.compose.ui.graphics.drawscope.Fill else Stroke(1.7f))
                    val ink = if(filled) MediaColors.background else tint
                    drawCircle(ink,1f,Offset(12f,7f))
                    drawPath(Path().apply { moveTo(10f,11f); lineTo(12f,11f); lineTo(12f,17f); lineTo(14f,17f) },
                        ink, style = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                MediaSymbol.TRASH -> {
                    line(Offset(3f,6f),Offset(21f,6f)); path(listOf(Offset(9f,5f),Offset(9f,3f),Offset(15f,3f),Offset(15f,5f)))
                    drawPath(Path().apply {
                        moveTo(5.5f,7f);lineTo(6.3f,18.7f);quadraticTo(6.4f,21f,8.6f,21f)
                        lineTo(15.4f,21f);quadraticTo(17.6f,21f,17.7f,18.7f);lineTo(18.5f,7f)
                    },tint,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    line(Offset(10f,10f),Offset(10f,17f));line(Offset(14f,10f),Offset(14f,17f))
                }
                MediaSymbol.PLAY -> drawPath(Path().apply {
                    moveTo(7f,4.8f);quadraticTo(7f,3.3f,8.3f,4.1f)
                    lineTo(19.8f,11f);quadraticTo(21.4f,12f,19.8f,13f)
                    lineTo(8.3f,19.9f);quadraticTo(7f,20.7f,7f,19.2f);close()
                },tint)
                MediaSymbol.PAUSE -> { drawRoundRect(tint,Offset(6f,4f),Size(4f,16f),CornerRadius(1.3f));drawRoundRect(tint,Offset(14f,4f),Size(4f,16f),CornerRadius(1.3f)) }
                MediaSymbol.SOUND, MediaSymbol.MUTED -> {
                    drawPath(Path().apply {
                        moveTo(4f,9f);lineTo(7f,9f);lineTo(11.2f,5f);quadraticTo(12.5f,3.8f,12.5f,5.6f)
                        lineTo(12.5f,18.4f);quadraticTo(12.5f,20.2f,11.2f,19f)
                        lineTo(7f,15f);lineTo(4f,15f);quadraticTo(3f,15f,3f,14f)
                        lineTo(3f,10f);quadraticTo(3f,9f,4f,9f);close()
                    },tint)
                    if(symbol == MediaSymbol.MUTED) {line(Offset(16f,9f),Offset(22f,15f));line(Offset(16f,15f),Offset(22f,9f))}
                    else {drawArc(tint,-50f,100f,false,Offset(9f,5f),Size(12f,14f),style=Stroke(1.6f,cap=StrokeCap.Round))}
                }
                MediaSymbol.PHOTO -> {
                    drawRoundRect(tint,Offset(2.5f,3.5f),Size(19f,17f),CornerRadius(3f),style=Stroke(1.7f))
                    drawCircle(tint,1.6f,Offset(8f,8.5f))
                    path(listOf(Offset(4f,18f),Offset(10f,12f),Offset(13f,15f),Offset(16f,11f),Offset(20f,16f)))
                }
                MediaSymbol.VIDEO -> {
                    drawRoundRect(tint,Offset(2f,5f),Size(14f,14f),CornerRadius(3f),style=Stroke(1.7f))
                    path(listOf(Offset(16f,9f),Offset(22f,6f),Offset(22f,18f),Offset(16f,15f)))
                }
                MediaSymbol.ARCHIVE -> {
                    drawRoundRect(tint,Offset(3f,3.5f),Size(18f,4.5f),CornerRadius(1.2f),style=Stroke(1.7f))
                    path(listOf(Offset(4.5f,8f),Offset(4.5f,20f),Offset(19.5f,20f),Offset(19.5f,8f)))
                    line(Offset(9f,12f),Offset(15f,12f))
                }
                MediaSymbol.CALENDAR -> {
                    drawRoundRect(tint,Offset(3f,5f),Size(18f,16f),CornerRadius(3f),style=Stroke(1.7f))
                    line(Offset(3f,10f),Offset(21f,10f))
                    line(Offset(8f,3f),Offset(8f,7f));line(Offset(16f,3f),Offset(16f,7f))
                    for(x in listOf(8f,12f,16f)) for(y in listOf(14f,17.5f)) drawCircle(tint,.8f,Offset(x,y))
                }
                MediaSymbol.FOLDER -> drawPath(Path().apply {
                    moveTo(3f,8f);lineTo(3f,6f);quadraticTo(3f,4f,5f,4f);lineTo(9f,4f)
                    lineTo(12f,7f);lineTo(19f,7f);quadraticTo(21f,7f,21f,9f)
                    lineTo(21f,18f);quadraticTo(21f,20f,19f,20f);lineTo(5f,20f)
                    quadraticTo(3f,20f,3f,18f);close()
                },tint,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                MediaSymbol.TAG -> {
                    drawPath(Path().apply {
                        moveTo(3f,4f);lineTo(11f,4f);lineTo(21f,14f);lineTo(14f,21f);lineTo(3f,10f);close()
                    },tint,style=Stroke(1.7f,cap=StrokeCap.Round,join=StrokeJoin.Round))
                    drawCircle(tint,1.2f,Offset(7.5f,8f))
                }
                MediaSymbol.GRID -> for(x in listOf(3f,14f)) for(y in listOf(3f,14f)) drawRoundRect(tint,Offset(x,y),Size(7f,7f),CornerRadius(1.5f),style=Stroke(1.7f))
            }
        }
    }
}
