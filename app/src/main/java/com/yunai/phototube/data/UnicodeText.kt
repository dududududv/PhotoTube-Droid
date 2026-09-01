package com.yunai.phototube.data

internal fun String.unicodeCodePointCount(): Int = codePointCount(0, length)

internal fun String.takeUnicodeCodePoints(maxCodePoints: Int): String {
    require(maxCodePoints >= 0) { "maxCodePoints 不能为负数" }
    if (unicodeCodePointCount() <= maxCodePoints) return this
    return substring(0, offsetByCodePoints(0, maxCodePoints))
}
