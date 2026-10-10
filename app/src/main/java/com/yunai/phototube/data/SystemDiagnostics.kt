package com.yunai.phototube.data

import org.json.JSONObject

/** Only contract-approved, non-sensitive fields can reach the diagnostics UI. */
internal data class SystemDiagnostics(
    val generatedAt: String,
    val version: String,
    val schemaVersion: Long?,
    val schemaDirty: Boolean,
    val thumbnailBytes: Long?,
    val videoPreviewBytes: Long?,
    val limitBytes: Long?,
    val aiConfigured: Boolean,
    val originalDeleteEnabled: Boolean,
    val xmpExportAvailable: Boolean,
) {
    companion object {
        fun from(value: JSONObject): SystemDiagnostics {
            val schema = value.getJSONObject("schema")
            val cache = value.getJSONObject("cache")
            val capabilities = value.getJSONObject("capabilities")
            fun JSONObject.optionalLong(key: String) = if (isNull(key)) null else getLong(key)
            return SystemDiagnostics(
                value.getString("generatedAt"), value.getString("version"),
                schema.optionalLong("version"), schema.getBoolean("dirty"),
                cache.optionalLong("thumbnailBytes"), cache.optionalLong("videoPreviewBytes"), cache.optionalLong("limitBytes"),
                capabilities.getBoolean("aiConfigured"), capabilities.getBoolean("originalDeleteEnabled"), capabilities.getBoolean("xmpExportAvailable"),
            )
        }
    }
}
