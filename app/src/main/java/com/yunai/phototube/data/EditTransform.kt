package com.yunai.phototube.data

import org.json.JSONObject

internal const val CROP_UNIT=1_000_000
internal data class EditTransform(val x:Int=0,val y:Int=0,val width:Int=CROP_UNIT,val height:Int=CROP_UNIT,
    val rotation:Int=0,val flipHorizontal:Boolean=false,val flipVertical:Boolean=false) {
    init {
        require(x in 0 until CROP_UNIT && y in 0 until CROP_UNIT)
        require(width in 1..CROP_UNIT && height in 1..CROP_UNIT && x+width<=CROP_UNIT && y+height<=CROP_UNIT)
        require(rotation in setOf(0,90,180,270))
    }
    fun json()=JSONObject().put("cropXPPM",x).put("cropYPPM",y).put("cropWidthPPM",width).put("cropHeightPPM",height)
        .put("rotationDegrees",rotation).put("flipHorizontal",flipHorizontal).put("flipVertical",flipVertical)
    companion object {
        fun from(json:JSONObject)=EditTransform(json.getInt("cropXPPM"),json.getInt("cropYPPM"),json.getInt("cropWidthPPM"),json.getInt("cropHeightPPM"),
            json.getInt("rotationDegrees"),json.getBoolean("flipHorizontal"),json.getBoolean("flipVertical"))
    }
}
