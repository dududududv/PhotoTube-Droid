package com.yunai.phototube.data.remote

import com.yunai.phototube.data.album.Album
import com.yunai.phototube.data.album.AlbumAssetListing
import com.yunai.phototube.data.album.AlbumAssetsRequest
import com.yunai.phototube.data.album.AlbumPage
import com.yunai.phototube.data.album.AlbumPathChangeAccepted
import com.yunai.phototube.data.album.AlbumPathChangePreview
import com.yunai.phototube.data.album.AlbumPathChangeRequest
import com.yunai.phototube.data.album.AlbumPathPage
import com.yunai.phototube.data.album.AlbumPathSyncRun
import com.yunai.phototube.data.album.AlbumPathSyncRunDetail
import com.yunai.phototube.data.album.AlbumPathSyncRunPage
import com.yunai.phototube.data.album.CreateAlbumRequest
import com.yunai.phototube.data.album.SourceFolderListing
import com.yunai.phototube.data.folder.FolderListing
import com.yunai.phototube.data.system.SystemStatus
import com.yunai.phototube.data.memory.CreateDateMemoryExclusionRequest
import com.yunai.phototube.data.memory.MemoryExclusion
import com.yunai.phototube.data.memory.MemoryExclusionPage
import com.yunai.phototube.data.album.UpdateAlbumRequest
import com.yunai.phototube.data.timeline.MediaAssetPage
import com.yunai.phototube.data.timeline.TimelineSummaryResponse
import com.yunai.phototube.data.tag.AssetTagRequest
import com.yunai.phototube.data.tag.CreateTagRequest
import com.yunai.phototube.data.tag.Tag
import com.yunai.phototube.data.tag.TagPage
import com.yunai.phototube.data.tag.TagDeleteResult
import com.yunai.phototube.data.tag.UpdateTagRequest
import com.yunai.phototube.data.timeline.AssetTag
import com.yunai.phototube.data.job.Job
import com.yunai.phototube.data.job.JobPage
import com.yunai.phototube.data.job.JobSummaryResponse
import com.yunai.phototube.data.duplicate.DuplicateGroupPage
import com.yunai.phototube.data.home.HomeFeed
import com.yunai.phototube.data.edit.ActiveEdit
import com.yunai.phototube.data.edit.CreateEditVersionRequest
import com.yunai.phototube.data.edit.EditVersion
import com.yunai.phototube.data.edit.EditVersionPage
import com.yunai.phototube.data.edit.SelectActiveEditRequest
import com.yunai.phototube.data.xmp.XmpExportPreview
import com.yunai.phototube.data.xmp.XmpExportRequest
import com.yunai.phototube.data.xmp.XmpExportRun
import com.yunai.phototube.data.xmp.XmpExportRunPage
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.PUT

interface PhotoTubeApi {
    @GET("home")
    suspend fun getHomeFeed(): Response<HomeFeed>

    @GET("health")
    suspend fun getHealth(): Response<HealthResponse>

    @GET("system/status")
    suspend fun getSystemStatus(): Response<SystemStatus>

    @GET("memory-exclusions")
    suspend fun getMemoryExclusions(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<MemoryExclusionPage>

    @POST("memory-exclusions")
    suspend fun createMemoryExclusion(
        @Body request: CreateDateMemoryExclusionRequest,
    ): Response<MemoryExclusion>

    @DELETE("memory-exclusions/{exclusionId}")
    suspend fun deleteMemoryExclusion(
        @Path("exclusionId") exclusionId: String,
    ): Response<Unit>

    @GET("auth/session")
    suspend fun getSession(): Response<SessionInfo>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Unit>

    @POST("auth/logout")
    suspend fun logout(): Response<Unit>

    @POST("auth/private-access")
    suspend fun unlockPrivateAccess(@Body request: PrivateAccessRequest): Response<Unit>

    @DELETE("auth/private-access")
    suspend fun lockPrivateAccess(): Response<Unit>

    @GET("assets")
    suspend fun getAssets(
        @Query("limit") limit: Int,
        @Query("cursor") cursor: String? = null,
        @QueryMap filters: Map<String, String>,
    ): Response<MediaAssetPage>

    @GET("assets/timeline-summary")
    suspend fun getTimelineSummary(
        @Query("granularity") granularity: String,
        @QueryMap filters: Map<String, String>,
    ): Response<TimelineSummaryResponse>

    @GET("folders")
    suspend fun getFolders(
        @Query("libraryId") libraryId: String? = null,
        @Query("path") path: String = "",
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<FolderListing>

    @GET("assets/{assetId}")
    suspend fun getAsset(@Path("assetId") assetId: String): Response<com.yunai.phototube.data.timeline.MediaAsset>

    @GET("assets/{assetId}/edit-versions")
    suspend fun getAssetEditVersions(
        @Path("assetId") assetId: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<EditVersionPage>

    @POST("assets/{assetId}/edit-versions")
    suspend fun createAssetEditVersion(
        @Path("assetId") assetId: String,
        @Body request: CreateEditVersionRequest,
    ): Response<EditVersion>

    @PATCH("assets/{assetId}/active-edit")
    suspend fun selectAssetEditVersion(
        @Path("assetId") assetId: String,
        @Body request: SelectActiveEditRequest,
    ): Response<ActiveEdit?>

    @POST("assets/favorite")
    suspend fun setAssetsFavorite(
        @Body request: FavoriteByIdsRequest,
    ): Response<BatchOperationResult>

    @POST("assets/archive")
    suspend fun setAssetsArchived(
        @Body request: ArchiveByIdsRequest,
    ): Response<BatchOperationResult>

    @POST("assets/rating")
    suspend fun setAssetsRating(
        @Body request: RatingByIdsRequest,
    ): Response<BatchOperationResult>

    @POST("assets/private")
    suspend fun setAssetsPrivate(
        @Body request: PrivateByIdsRequest,
    ): Response<BatchOperationResult>

    @POST("assets/trash")
    suspend fun trashAssets(
        @Body request: AssetIdsRequest,
    ): Response<BatchOperationResult>

    @GET("trash")
    suspend fun getTrash(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<MediaAssetPage>

    @POST("assets/restore")
    suspend fun restoreAssets(
        @Body request: AssetIdsRequest,
    ): Response<BatchOperationResult>

    @POST("trash/purge")
    suspend fun purgeTrash(
        @Body request: AssetIdsRequest,
    ): Response<BatchOperationResult>

    @GET("tags")
    suspend fun getTags(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("keyword") keyword: String? = null,
    ): Response<TagPage>

    @POST("tags")
    suspend fun createTag(@Body request: CreateTagRequest): Response<Tag>

    @PATCH("tags/{tagId}")
    suspend fun updateTag(
        @Path("tagId") tagId: Long,
        @Body request: UpdateTagRequest,
    ): Response<Tag>

    @DELETE("tags/{tagId}")
    suspend fun deleteTag(@Path("tagId") tagId: Long): Response<TagDeleteResult>

    @POST("assets/{assetId}/tags")
    suspend fun addAssetTag(
        @Path("assetId") assetId: String,
        @Body request: AssetTagRequest,
    ): Response<AssetTag>

    @DELETE("assets/{assetId}/tags/{tagId}")
    suspend fun removeAssetTag(
        @Path("assetId") assetId: String,
        @Path("tagId") tagId: Long,
    ): Response<Unit>

    @GET("albums")
    suspend fun getAlbums(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<AlbumPage>

    @POST("albums")
    suspend fun createAlbum(@Body request: CreateAlbumRequest): Response<Album>

    @PATCH("albums/{albumId}")
    suspend fun updateAlbum(
        @Path("albumId") albumId: String,
        @Body request: UpdateAlbumRequest,
    ): Response<Album>

    @DELETE("albums/{albumId}")
    suspend fun deleteAlbum(@Path("albumId") albumId: String): Response<Unit>

    @GET("albums/{albumId}/assets")
    suspend fun getAlbumAssets(
        @Path("albumId") albumId: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<AlbumAssetListing>

    @POST("albums/{albumId}/assets")
    suspend fun addAlbumAssets(
        @Path("albumId") albumId: String,
        @Body request: AlbumAssetsRequest,
    ): Response<BatchOperationResult>

    @HTTP(method = "DELETE", path = "albums/{albumId}/assets", hasBody = true)
    suspend fun removeAlbumAssets(
        @Path("albumId") albumId: String,
        @Body request: AlbumAssetsRequest,
    ): Response<BatchOperationResult>

    @GET("source-folders")
    suspend fun getSourceFolders(
        @Query("path") path: String = "",
    ): Response<SourceFolderListing>

    @GET("albums/{albumId}/paths")
    suspend fun getAlbumPaths(
        @Path("albumId") albumId: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<AlbumPathPage>

    @POST("albums/{albumId}/path-change-previews")
    suspend fun previewAlbumPathChange(
        @Path("albumId") albumId: String,
        @Body request: AlbumPathChangeRequest,
    ): Response<AlbumPathChangePreview>

    @POST("albums/{albumId}/path-changes")
    suspend fun applyAlbumPathChange(
        @Path("albumId") albumId: String,
        @Body request: AlbumPathChangeRequest,
    ): Response<AlbumPathChangeAccepted>

    @GET("albums/{albumId}/syncs")
    suspend fun getAlbumPathSyncRuns(
        @Path("albumId") albumId: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<AlbumPathSyncRunPage>

    @POST("albums/{albumId}/syncs")
    suspend fun triggerAlbumPathSync(
        @Path("albumId") albumId: String,
    ): Response<AlbumPathSyncRun>

    @GET("albums/{albumId}/syncs/{syncRunId}")
    suspend fun getAlbumPathSyncRun(
        @Path("albumId") albumId: String,
        @Path("syncRunId") syncRunId: String,
    ): Response<AlbumPathSyncRunDetail>

    @GET("jobs")
    suspend fun getJobs(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("state") state: String? = null,
        @Query("kind") kind: String? = null,
    ): Response<JobPage>

    @GET("jobs/summary")
    suspend fun getJobSummary(): Response<JobSummaryResponse>

    @POST("job-queues/{kind}/pause")
    suspend fun pauseJobQueue(@Path("kind") kind: String): Response<Unit>

    @POST("job-queues/{kind}/resume")
    suspend fun resumeJobQueue(@Path("kind") kind: String): Response<Unit>

    @POST("jobs/{jobId}/cancel")
    suspend fun cancelJob(@Path("jobId") jobId: String): Response<Job>

    @GET("duplicates")
    suspend fun getDuplicateGroups(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("includeReviewed") includeReviewed: Boolean = false,
        @Query("private") privateScope: Boolean = false,
    ): Response<DuplicateGroupPage>

    @GET("duplicates/{contentHash}")
    suspend fun getDuplicateAssets(
        @Path("contentHash") contentHash: String,
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
        @Query("private") privateScope: Boolean = false,
    ): Response<MediaAssetPage>

    @PUT("duplicates/{contentHash}/review")
    suspend fun reviewDuplicateGroup(
        @Path("contentHash") contentHash: String,
        @Query("private") privateScope: Boolean = false,
    ): Response<Unit>

    @DELETE("duplicates/{contentHash}/review")
    suspend fun reopenDuplicateGroup(
        @Path("contentHash") contentHash: String,
        @Query("private") privateScope: Boolean = false,
    ): Response<Unit>

    @POST("xmp-exports/preview")
    suspend fun previewXmpExport(@Body request: XmpExportRequest): Response<XmpExportPreview>

    @GET("xmp-exports")
    suspend fun getXmpExportRuns(
        @Query("limit") limit: Int = 100,
        @Query("cursor") cursor: String? = null,
    ): Response<XmpExportRunPage>

    @POST("xmp-exports")
    suspend fun createXmpExport(@Body request: XmpExportRequest): Response<XmpExportRun>

    @GET("xmp-exports/{exportRunId}")
    suspend fun getXmpExportRun(
        @Path("exportRunId") exportRunId: String,
    ): Response<XmpExportRun>
}
