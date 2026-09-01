package com.yunai.phototube.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.R
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import kotlin.math.roundToInt

@Composable
fun AlbumTopBar(
    modifier: Modifier = Modifier,
    onProfileClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
    filterActive: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.avatar_profile),
            contentDescription = "个人资料",
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onProfileClick),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.weight(1f))
        CircleIconButton(AppIconKind.Search, "搜索", onSearchClick)
        Spacer(Modifier.width(10.dp))
        CircleIconButton(AppIconKind.Filter, "筛选", onFilterClick, showIndicator = filterActive)
    }
}

@Composable
private fun CircleIconButton(
    kind: AppIconKind,
    contentDescription: String,
    onClick: () -> Unit,
    showIndicator: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(PhotoTubeDimens.FloatingControl)
            .android16Glass(cornerRadius = 26.dp, strength = 0.78f)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(kind, Modifier.size(27.dp), PhotoTubeColors.Ink)
        if (showIndicator) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 7.dp, end = 7.dp)
                    .size(7.dp)
                    .background(PhotoTubeColors.Alert, CircleShape),
            )
        }
    }
}

@Composable
fun FloatingAlbumDock(
    modifier: Modifier = Modifier,
    selectedItem: Int = 0,
    onItemClick: (Int) -> Unit,
    onLayoutClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val actionTouchSize = 48.dp
    val actionVisualSize = 36.dp
    val controlWidth = 242.dp
    val controlHeight = 24.dp
    val controlPadding = 1.dp
    val segmentWidth = controlWidth / 3
    val selectedIndex = selectedItem.coerceIn(0, 2)
    val stepPx = with(LocalDensity.current) { segmentWidth.toPx() }
    val indicatorInsetPx = with(LocalDensity.current) { controlPadding.toPx() }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val animatedBaseOffsetPx by animateFloatAsState(
        targetValue = selectedIndex * stepPx,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "底部文字导航滑块位置",
    )
    val indicatorOffsetPx = (animatedBaseOffsetPx + dragOffsetPx)
        .coerceIn(0f, stepPx * 2f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DockActionButton(
            kind = AppIconKind.Grid,
            contentDescription = "切换照片布局",
            touchSize = actionTouchSize,
            visualSize = actionVisualSize,
            onClick = onLayoutClick,
        )

        Box(
            modifier = Modifier
                .size(width = controlWidth, height = actionTouchSize)
                .pointerInput(selectedIndex, stepPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragOffsetPx = 0f },
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            val threshold = stepPx * 0.24f
                            val target = when {
                                dragOffsetPx < -threshold -> selectedIndex - 1
                                dragOffsetPx > threshold -> selectedIndex + 1
                                else -> selectedIndex
                            }.coerceIn(0, 2)
                            dragOffsetPx = 0f
                            if (target != selectedIndex) onItemClick(target)
                        },
                    ) { change, dragAmount ->
                        change.consume()
                        dragOffsetPx = (dragOffsetPx + dragAmount)
                            .coerceIn(-selectedIndex * stepPx, (2 - selectedIndex) * stepPx)
                    }
                },
        ) {
            val labels = listOf("照片", "图集", "创作")

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = controlWidth, height = controlHeight)
                    .shadow(
                        elevation = 0.5.dp,
                        shape = RoundedCornerShape(controlHeight / 2),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.018f),
                        spotColor = Color.Black.copy(alpha = 0.028f),
                    )
                    .android16Glass(
                        cornerRadius = controlHeight / 2,
                        strength = 0.91f,
                        showBorder = false,
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.38f),
                            1f to Color.White.copy(alpha = 0.26f),
                        ),
                        shape = RoundedCornerShape(controlHeight / 2),
                    ),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset {
                        IntOffset(
                            x = (indicatorOffsetPx + indicatorInsetPx).roundToInt(),
                            y = 0,
                        )
                    }
                    .size(
                        width = segmentWidth - controlPadding * 2,
                        height = controlHeight - controlPadding * 2,
                    )
                    .shadow(
                        elevation = 0.5.dp,
                        shape = RoundedCornerShape((controlHeight - controlPadding * 2) / 2),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.016f),
                        spotColor = Color.Black.copy(alpha = 0.024f),
                    )
                    .background(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.90f),
                            1f to Color.White.copy(alpha = 0.74f),
                        ),
                        shape = RoundedCornerShape((controlHeight - controlPadding * 2) / 2),
                    ),
            )

            Row(Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    val isSelected = selectedIndex == index
                    Box(
                        modifier = Modifier
                            .size(width = segmentWidth, height = actionTouchSize)
                            .clip(RoundedCornerShape(actionTouchSize / 2))
                            .semantics {
                                contentDescription = label
                                selected = isSelected
                            }
                            .clickable(role = Role.Tab) { onItemClick(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = PhotoTubeColors.Ink,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        )
                    }
                }
            }
        }

        DockActionButton(
            kind = AppIconKind.Filter,
            contentDescription = "打开显示选项",
            touchSize = actionTouchSize,
            visualSize = actionVisualSize,
            onClick = onMenuClick,
        )
    }
}

@Composable
private fun DockActionButton(
    kind: AppIconKind,
    contentDescription: String,
    touchSize: Dp,
    visualSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(touchSize)
            .semantics { this.contentDescription = contentDescription }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(visualSize)
                .shadow(
                    elevation = 2.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.025f),
                    spotColor = Color.Black.copy(alpha = 0.04f),
                )
                .android16Glass(
                    cornerRadius = visualSize / 2,
                    strength = 0.91f,
                    showBorder = false,
                )
                .background(Color.White.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(kind, Modifier.size(18.dp), PhotoTubeColors.Ink)
        }
    }
}
