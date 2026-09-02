package com.yunai.phototube.ui.photos

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yunai.phototube.data.MockAlbumRepository
import com.yunai.phototube.model.MemoryDay
import com.yunai.phototube.model.PhotoMemory
import com.yunai.phototube.ui.components.AlbumTopBar
import com.yunai.phototube.ui.components.Android16HazeProvider
import com.yunai.phototube.ui.components.FloatingAlbumDock
import com.yunai.phototube.ui.components.android16Glass
import com.yunai.phototube.ui.components.rememberAndroid16HazeState
import com.yunai.phototube.ui.theme.PhotoTubeColors
import com.yunai.phototube.ui.theme.PhotoTubeDimens
import dev.chrisbanes.haze.hazeSource

@Composable
fun PhotoTimelineScreen(
    onOpenCollections: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberAndroid16HazeState()
    var selectedDockItem by remember { mutableIntStateOf(0) }
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
                top = 0.dp,
                end = PhotoTubeDimens.ScreenPadding,
                bottom = 116.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Column(Modifier.statusBarsPadding()) {
                    Spacer(Modifier.height(20.dp))
                    AlbumTopBar(onProfileClick = onOpenCollections)
                    Spacer(Modifier.height(30.dp))
                    PhotoHeading()
                }
            }
            items(MockAlbumRepository.days, key = { it.date }) { day ->
                DaySection(day)
            }
        }

        FloatingAlbumDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            selectedItem = selectedDockItem,
            onItemClick = { index ->
                selectedDockItem = index
                if (index == 1) onOpenCollections()
            },
        )
      }
    }
}

@Composable
private fun PhotoHeading() {
    var selectedPeriod by remember { mutableIntStateOf(2) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column {
            Text("照片", style = MaterialTheme.typography.displayLarge, color = PhotoTubeColors.Ink)
            Text(
                "2026年8月",
                color = PhotoTubeColors.Muted,
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Spacer(Modifier.weight(1f))
        PeriodSelector(selectedPeriod, onSelected = { selectedPeriod = it })
    }
}

@Composable
private fun PeriodSelector(selected: Int, onSelected: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .width(196.dp)
            .height(52.dp)
            .android16Glass(cornerRadius = 28.dp, strength = 0.78f)
            .padding(3.dp),
    ) {
        listOf("年", "月", "日").forEachIndexed { index, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .then(
                        if (selected == index) {
                            Modifier.android16Glass(cornerRadius = 24.dp, strength = 0.44f)
                        } else Modifier
                    )
                    .clickable { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected == index) PhotoTubeColors.Ink else PhotoTubeColors.Muted,
                    fontSize = 18.sp,
                    fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DaySection(day: MemoryDay) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${day.date}  ${day.relativeLabel}",
                style = MaterialTheme.typography.headlineMedium,
                color = PhotoTubeColors.Ink,
            )
            Spacer(Modifier.width(12.dp))
            Text(day.location, color = PhotoTubeColors.Muted, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        PhotoMosaic(day.photos)
    }
}

@Composable
private fun PhotoMosaic(photos: List<PhotoMemory>) {
    val gap = PhotoTubeDimens.GridGap
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        if (photos.size >= 7) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                PhotoCell(photos[0], Modifier.weight(1.72f).fillMaxSize())
                PhotoCell(photos[1], Modifier.weight(1f).fillMaxSize())
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(152.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                PhotoCell(photos[2], Modifier.weight(.80f).fillMaxSize())
                PhotoCell(photos[3], Modifier.weight(1f).fillMaxSize())
                PhotoCell(photos[4], Modifier.weight(1.04f).fillMaxSize())
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(136.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                PhotoCell(photos[5], Modifier.weight(1f).fillMaxSize())
                PhotoCell(photos[6], Modifier.weight(1f).fillMaxSize())
            }
        } else {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(170.dp),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                photos.forEach { PhotoCell(it, Modifier.weight(1f).fillMaxSize()) }
            }
        }
    }
}

@Composable
private fun PhotoCell(photo: PhotoMemory, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(photo.drawableRes),
        contentDescription = photo.contentDescription,
        modifier = modifier.clip(RoundedCornerShape(PhotoTubeDimens.PhotoRadius)),
        contentScale = ContentScale.Crop,
    )
}
