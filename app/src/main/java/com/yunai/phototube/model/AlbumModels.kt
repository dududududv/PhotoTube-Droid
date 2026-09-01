package com.yunai.phototube.model

import androidx.annotation.DrawableRes

data class PhotoMemory(
    val id: String,
    @param:DrawableRes val drawableRes: Int,
    val contentDescription: String,
)

data class MemoryDay(
    val date: String,
    val relativeLabel: String,
    val location: String,
    val photos: List<PhotoMemory>,
)

data class SharedCollection(
    val id: String,
    val title: String,
    val date: String,
    val memoryCount: Int,
    @param:DrawableRes val coverRes: Int,
    val memberLabels: List<String>,
)
