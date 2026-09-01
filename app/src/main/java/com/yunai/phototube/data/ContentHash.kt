package com.yunai.phototube.data

/** PhotoTube 内容版本键只接受规范的 64 位小写 SHA-256。 */
internal fun String.isSha256ContentHash(): Boolean =
    length == SHA256_HEX_LENGTH && all { it in '0'..'9' || it in 'a'..'f' }

private const val SHA256_HEX_LENGTH = 64
