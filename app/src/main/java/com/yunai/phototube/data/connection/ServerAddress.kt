package com.yunai.phototube.data.connection

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@JvmInline
value class ServerRoot private constructor(val value: String) {
    val apiBaseUrl: String
        get() = "$value/api/v1/"

    companion object {
        fun parse(rawValue: String): Result<ServerRoot> = runCatching {
            val trimmed = rawValue.trim()
            require(trimmed.isNotEmpty()) { "请输入 PhotoTube 服务地址" }

            val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
            val parsed = withScheme.toHttpUrlOrNull()
                ?: throw IllegalArgumentException("服务地址格式不正确")
            require(parsed.scheme == "http" || parsed.scheme == "https") {
                "服务地址只支持 HTTP 或 HTTPS"
            }
            require(parsed.query == null && parsed.fragment == null) {
                "服务地址不能包含查询参数或片段"
            }

            val normalizedPath = parsed.encodedPath.trimEnd('/')
            require(normalizedPath.isEmpty() || normalizedPath == "/api/v1") {
                "请输入服务根地址，不要附加其它路径"
            }

            val root = parsed.newBuilder()
                .encodedPath("/")
                .query(null)
                .fragment(null)
                .build()
                .toString()
                .removeSuffix("/")
            ServerRoot(root)
        }

        fun fromStored(value: String): ServerRoot? = parse(value).getOrNull()
    }
}
