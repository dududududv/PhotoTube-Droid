package com.yunai.phototube.data.home

import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.job.JobSummaryResponse
import com.yunai.phototube.data.timeline.MediaAsset

data class HomeFeed(
    val onThisDay: List<MediaAsset>,
    val recentPhotos: List<MediaAsset>,
    val recentImports: List<MediaAsset>,
    val frequentAlbums: List<Album>,
    val jobSummary: JobSummaryResponse,
) {
    init {
        require(onThisDay.size <= MAX_ASSET_ITEMS)
        require(recentPhotos.size <= MAX_ASSET_ITEMS)
        require(recentImports.size <= MAX_ASSET_ITEMS)
        require(frequentAlbums.size <= MAX_ALBUM_ITEMS)
    }

    val isEmpty: Boolean
        get() = onThisDay.isEmpty() &&
            recentPhotos.isEmpty() &&
            recentImports.isEmpty() &&
            frequentAlbums.isEmpty() &&
            jobSummary.items.isEmpty()

    private companion object {
        const val MAX_ASSET_ITEMS = 18
        const val MAX_ALBUM_ITEMS = 8
    }
}
