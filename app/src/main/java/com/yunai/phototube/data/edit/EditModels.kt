package com.yunai.phototube.data.edit

import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

const val EDIT_PPM = 1_000_000
const val MIN_EDIT_CROP_PPM = 10_000

data class EditTransform(
    val cropXPPM: Int = 0,
    val cropYPPM: Int = 0,
    val cropWidthPPM: Int = EDIT_PPM,
    val cropHeightPPM: Int = EDIT_PPM,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "旋转角度只能是 0/90/180/270" }
        require(cropXPPM in 0 until EDIT_PPM && cropYPPM in 0 until EDIT_PPM) {
            "裁剪起点必须位于画布内"
        }
        require(cropWidthPPM in MIN_EDIT_CROP_PPM..EDIT_PPM) { "裁剪宽度越界" }
        require(cropHeightPPM in MIN_EDIT_CROP_PPM..EDIT_PPM) { "裁剪高度越界" }
        require(cropXPPM + cropWidthPPM <= EDIT_PPM) { "裁剪区域超出画布右侧" }
        require(cropYPPM + cropHeightPPM <= EDIT_PPM) { "裁剪区域超出画布底部" }
    }

    fun rotateClockwise(): EditTransform {
        val oddFlipParity = flipHorizontal != flipVertical
        return copy(
            cropXPPM = if (oddFlipParity) {
                cropYPPM
            } else {
                EDIT_PPM - cropYPPM - cropHeightPPM
            },
            cropYPPM = if (oddFlipParity) {
                EDIT_PPM - cropXPPM - cropWidthPPM
            } else {
                cropXPPM
            },
            cropWidthPPM = cropHeightPPM,
            cropHeightPPM = cropWidthPPM,
            rotationDegrees = (rotationDegrees + 90) % 360,
        )
    }

    fun flipHorizontally(): EditTransform = copy(
        cropXPPM = EDIT_PPM - cropXPPM - cropWidthPPM,
        flipHorizontal = !flipHorizontal,
    )

    fun flipVertically(): EditTransform = copy(
        cropYPPM = EDIT_PPM - cropYPPM - cropHeightPPM,
        flipVertical = !flipVertical,
    )

    fun withHorizontalCrop(startPPM: Int, endPPM: Int): EditTransform {
        val (start, end) = normalizedCropRange(startPPM, endPPM)
        return copy(cropXPPM = start, cropWidthPPM = end - start)
    }

    fun withVerticalCrop(startPPM: Int, endPPM: Int): EditTransform {
        val (start, end) = normalizedCropRange(startPPM, endPPM)
        return copy(cropYPPM = start, cropHeightPPM = end - start)
    }

    private fun normalizedCropRange(startPPM: Int, endPPM: Int): Pair<Int, Int> {
        val start = startPPM.coerceIn(0, EDIT_PPM - MIN_EDIT_CROP_PPM)
        val end = endPPM.coerceIn(start + MIN_EDIT_CROP_PPM, EDIT_PPM)
        return start to end
    }

    companion object {
        val Source = EditTransform()
    }
}

enum class EditSourceState { CURRENT, STALE }

enum class EditRenderState { PENDING, READY }

data class ActiveEdit(
    val editVersionId: String,
    val sourceState: EditSourceState,
    val renderState: EditRenderState,
    val displayWidth: Int,
    val displayHeight: Int,
)

data class EditVersion(
    val id: String,
    val assetId: String,
    val parentEditVersionId: String?,
    val sourceContentHash: String,
    val transform: EditTransform,
    val outputWidth: Int,
    val outputHeight: Int,
    val active: Boolean,
    val sourceState: EditSourceState,
    val renderState: EditRenderState,
    val createdAt: String,
) {
    fun activeSummary(): ActiveEdit = ActiveEdit(
        editVersionId = id,
        sourceState = sourceState,
        renderState = renderState,
        displayWidth = outputWidth,
        displayHeight = outputHeight,
    )
}

data class EditVersionPage(
    val items: List<EditVersion>,
    val nextCursor: String?,
)

data class CreateEditVersionRequest(
    val expectedActiveEditVersionId: String?,
    val sourceContentHash: String,
    val transform: EditTransform,
)

data class SelectActiveEditRequest(
    val expectedActiveEditVersionId: String?,
    val targetEditVersionId: String?,
)

object EditRequestJsonAdapter {
    @ToJson
    fun createToJson(writer: JsonWriter, value: CreateEditVersionRequest?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        writer.name("expectedActiveEditVersionId")
        writer.value(value.expectedActiveEditVersionId)
        writer.serializeNulls = previousSerializeNulls
        writer.name("sourceContentHash").value(value.sourceContentHash)
        writer.name("transform")
        writer.beginObject()
        writer.name("cropXPPM").value(value.transform.cropXPPM.toLong())
        writer.name("cropYPPM").value(value.transform.cropYPPM.toLong())
        writer.name("cropWidthPPM").value(value.transform.cropWidthPPM.toLong())
        writer.name("cropHeightPPM").value(value.transform.cropHeightPPM.toLong())
        writer.name("rotationDegrees").value(value.transform.rotationDegrees.toLong())
        writer.name("flipHorizontal").value(value.transform.flipHorizontal)
        writer.name("flipVertical").value(value.transform.flipVertical)
        writer.endObject()
        writer.endObject()
    }

    @ToJson
    fun selectToJson(writer: JsonWriter, value: SelectActiveEditRequest?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        val previousSerializeNulls = writer.serializeNulls
        writer.serializeNulls = true
        writer.name("expectedActiveEditVersionId")
        writer.value(value.expectedActiveEditVersionId)
        writer.name("targetEditVersionId")
        writer.value(value.targetEditVersionId)
        writer.serializeNulls = previousSerializeNulls
        writer.endObject()
    }
}
