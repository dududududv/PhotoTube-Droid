package com.yunai.phototube.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

internal val Icons.Rounded.LibraryFolder: ImageVector by lazy {
    materialIcon(name = "LibraryFolder") {
        materialPath {
            moveTo(4f, 4f)
            horizontalLineTo(8.4f)
            curveTo(9.1f, 4f, 9.5f, 4.2f, 10f, 4.7f)
            lineTo(11.5f, 6.2f)
            horizontalLineTo(20f)
            curveTo(21.1f, 6.2f, 22f, 7.1f, 22f, 8.2f)
            verticalLineTo(9f)
            horizontalLineTo(2f)
            verticalLineTo(6f)
            curveTo(2f, 4.9f, 2.9f, 4f, 4f, 4f)
            close()
        }
        materialPath {
            moveTo(2f, 10.3f)
            horizontalLineTo(22f)
            verticalLineTo(18f)
            curveTo(22f, 19.1f, 21.1f, 20f, 20f, 20f)
            horizontalLineTo(4f)
            curveTo(2.9f, 20f, 2f, 19.1f, 2f, 18f)
            close()
        }
    }
}
