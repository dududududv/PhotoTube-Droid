package com.yunai.phototube.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted at rest, exact-origin scoped, never logged or exposed to UI. */
internal class SessionCookieJar(context:Context,private val origin:HttpUrl):CookieJar {
    init { PhotoTubeServer.normalize(origin.toString()) }
    // Fresh namespace avoids silently restoring sessions from earlier development builds.
    // Each explicitly selected origin has its own encrypted entry.
    private val prefs=context.getSharedPreferences("server_sessions_v2", Context.MODE_PRIVATE)
    private val storageKey=java.security.MessageDigest.getInstance("SHA-256").digest(origin.toString().toByteArray()).joinToString(""){"%02x".format(it)}
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        return (store.getKey("phototube-session-v1",null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("phototube-session-v1",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private var cookies:List<Cookie> = runCatching {
        val stored=prefs.getString(storageKey,null) ?: return@runCatching emptyList()
        val parts=stored.split(':')
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        val list=JSONArray(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP))))
        (0 until list.length()).mapNotNull {i ->
            val entry=list.getJSONObject(i)
            Cookie.parse(origin,entry.getString("cookie"))?.newBuilderWithExpiry(entry.getLong("expires"))
        }.filter {it.expiresAt>System.currentTimeMillis()}
    }.getOrDefault(emptyList())
    private fun Cookie.newBuilderWithExpiry(expires:Long):Cookie=Cookie.Builder().name(name).value(value).path(path).expiresAt(expires).apply {
        if(hostOnly) hostOnlyDomain(domain) else domain(domain)
        if(secure) secure();if(httpOnly) httpOnly()
    }.build()
    private fun same(url:HttpUrl)=url.scheme==origin.scheme && url.host==origin.host && url.port==origin.port
    @Synchronized override fun loadForRequest(url:HttpUrl)=if(same(url)) cookies.filter {it.matches(url) && it.expiresAt>System.currentTimeMillis()} else emptyList()
    @Synchronized override fun saveFromResponse(url:HttpUrl,incoming:List<Cookie>) {
        if(!same(url)) return
        val accepted=incoming.filter {it.name=="phototube_session" && it.domain==origin.host}
        cookies=(cookies.filter {old -> accepted.none {it.name==old.name}}+accepted).filter {it.expiresAt>System.currentTimeMillis()}
        val json=JSONArray().apply {cookies.forEach {put(JSONObject().put("cookie",it.toString()).put("expires",it.expiresAt))}}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,key())}
        val encrypted=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(json.toString().toByteArray()),Base64.NO_WRAP)
        // Called on OkHttp workers: finish durable storage before login succeeds.
        check(prefs.edit().putString(storageKey,encrypted).commit()) {"无法保存登录会话，请检查设备存储空间"}
    }
    @Synchronized fun clear() {cookies=emptyList();prefs.edit().remove(storageKey).apply()}
}
