/*
 * Copyright 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yunai.phototube.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

public val Icons.Rounded.PhotoAlbum: ImageVector
    get() {
        if (_photoAlbum != null) {
            return _photoAlbum!!
        }
        _photoAlbum = materialIcon(name = "Rounded.PhotoAlbum") {
            materialPath {
                moveTo(18.0f, 2.0f)
                horizontalLineTo(6.0f)
                curveTo(4.9f, 2.0f, 4.0f, 2.9f, 4.0f, 4.0f)
                verticalLineToRelative(16.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(12.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(4.0f)
                curveTo(20.0f, 2.9f, 19.1f, 2.0f, 18.0f, 2.0f)
                close()
                moveTo(15.24f, 10.55f)
                lineTo(13.5f, 9.5f)
                lineToRelative(-1.74f, 1.05f)
                curveToRelative(-0.33f, 0.2f, -0.76f, -0.04f, -0.76f, -0.43f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(6.12f)
                curveTo(16.0f, 10.51f, 15.58f, 10.75f, 15.24f, 10.55f)
                close()
                moveTo(7.6f, 17.2f)
                lineToRelative(1.38f, -1.83f)
                curveToRelative(0.2f, -0.27f, 0.6f, -0.27f, 0.8f, 0.0f)
                lineTo(11.0f, 17.0f)
                lineToRelative(2.23f, -2.97f)
                curveToRelative(0.2f, -0.27f, 0.6f, -0.27f, 0.8f, 0.0f)
                lineToRelative(2.38f, 3.17f)
                curveToRelative(0.25f, 0.33f, 0.01f, 0.8f, -0.4f, 0.8f)
                horizontalLineTo(8.0f)
                curveTo(7.59f, 18.0f, 7.35f, 17.53f, 7.6f, 17.2f)
                close()
            }
        }
        return _photoAlbum!!
    }

private var _photoAlbum: ImageVector? = null
