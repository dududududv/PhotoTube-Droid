package com.yunai.phototube.data

import java.util.Locale

// Indexed photo formats excluded from derivatives by contracts/media-formats.json.
// This is a server capability check, not a claim about Android original decoding.
private val originalOnlyPhotoFormats = setOf("avif", "bmp", "tif", "tiff", "dng", "cr2", "cr3", "nef", "arw", "raf")
internal val RemoteAsset.originalOnlyFormat: String?
    get() = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        .takeIf { kind == "PHOTO" && it in originalOnlyPhotoFormats }
