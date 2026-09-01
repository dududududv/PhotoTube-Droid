package com.yunai.phototube.ui.collections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.R
import com.yunai.phototube.data.MockAlbumRepository
import com.yunai.phototube.model.SharedCollection
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.AppIcon
import com.yunai.phototube.ui.components.AppIconKind
import com.yunai.phototube.ui.components.FloatingAlbumDock
import com.yunai.phototube.ui.components.android16Glass
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource

@Composable
fun CollectionsScreen(
    onCollectionClick: (SharedCollection) -> Unit,
    onOpenPhotos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    var selectedDockItem by remember { mutableIntStateOf(1) }
    Android16HazeProvider(state = hazeState) {
      Box(
          modifier = modifier
              .fillMaxSize()
              .background(PhotoTubeColors.Background),
      ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = PhotoTubeDimens.ScreenPadding,
                end = PhotoTubeDimens.ScreenPadding,
                bottom = 120.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Spacer(Modifier.height(20.dp))
                    AlbumTopBar()
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (MockAlbumRepository.collections.isEmpty()) {
                item { EmptyCollectionsState(onCreateClick = {}) }
            } else {
                items(MockAlbumRepository.collections, key = { it.id }) { collection ->
                    CollectionCard(collection, onClick = { onCollectionClick(collection) })
                }
            }
        }

        FloatingAlbumDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedItem = selectedDockItem,
            onItemClick = {
                selectedDockItem = it
                if (it == 0) onOpenPhotos()
            },
            onLayoutClick = {},
            onMenuClick = {},
        )
      }
    }
}

@Composable
private fun EmptyCollectionsState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center,
        ) {
            listOf(
                Triple(R.drawable.photo_mountain, -38.dp, -9f),
                Triple(R.drawable.photo_city, 42.dp, 7f),
                Triple(R.drawable.photo_sunset, 0.dp, 0f),
            ).forEachIndexed { index, (res, x, rotation) ->
                Image(
                    painter = painterResource(res),
                    contentDescription = if (index == 2) "旅行照片示例" else null,
                    modifier = Modifier
                        .width(184.dp)
                        .height(176.dp)
                        .graphicsLayer {
                            translationX = x.toPx()
                            translationY = if (index == 2) 30.dp.toPx() else 0f
                            rotationZ = rotation
                            shadowElevation = 8.dp.toPx()
                            shape = RoundedCornerShape(24.dp)
                            clip = true
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Text("It starts with a collection", style = MaterialTheme.typography.titleLarge, color = PhotoTubeColors.Ink)
        Spacer(Modifier.height(4.dp))
        Text("Create one to start sharing memories", color = PhotoTubeColors.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        Box(
            modifier = Modifier
                .width(300.dp)
                .height(52.dp)
                .background(PhotoTubeColors.Accent, RoundedCornerShape(28.dp))
                .clickable(onClick = onCreateClick),
            contentAlignment = Alignment.Center,
        ) {
            Text("Create your first collection", color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CollectionCard(collection: SharedCollection, onClick: () -> Unit) {
    val coverHazeState = rememberAndroid16HazeState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Android16HazeProvider(state = coverHazeState) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (collection.id == "japan") PhotoTubeColors.JapanTint else PhotoTubeColors.PartyTint),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .hazeSource(state = coverHazeState),
                ) {
                    Image(
                        painter = painterResource(collection.coverRes),
                        contentDescription = "${collection.title} 合集封面",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(72.dp)
                            .background(
                                if (collection.id == "japan") Color(0xB8FF7C87) else Color(0xB45D6EE7),
                                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            ),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 18.dp)
                        .size(50.dp)
                        .android16Glass(cornerRadius = 25.dp, strength = 0.84f),
                    contentAlignment = Alignment.Center,
                ) {
                    AppIcon(AppIconKind.Plus, Modifier.size(22.dp), PhotoTubeColors.Ink)
                }
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(collection.title, style = MaterialTheme.typography.titleLarge, color = PhotoTubeColors.Ink)
        Spacer(Modifier.height(2.dp))
        Text(
            "${collection.date}  •  ${collection.memoryCount} Memories",
            style = MaterialTheme.typography.bodyMedium,
            color = PhotoTubeColors.Muted,
        )
        Spacer(Modifier.height(8.dp))
        MemberRow(collection.memberLabels)
    }
}

@Composable
private fun MemberRow(labels: List<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { index, label ->
            if (label.startsWith("+")) {
                Text(label, color = PhotoTubeColors.Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            } else {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            listOf(Color(0xFFE7B6A7), Color(0xFFF28091), Color(0xFF9D72E8))[index % 3],
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
