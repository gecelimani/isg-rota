package com.gecelimani.isgrota

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.io.File
import java.security.MessageDigest

private const val SORULAR_URL = "https://gecelimani.com/api_x7k9p/sorular_getir.php"
private const val API_KEY = BuildConfig.API_KEY
private const val CACHE_DOSYA = "sorular_cache.json"

fun loadSorularFromAssets(context: Context): List<Soru> {
    return parseSorular(getCacheJson(context))
}

fun sorulariSunucudanGuncelle(context: Context) {
    try {
        val request = Request.Builder()
            .url(SORULAR_URL)
            .get()
            .addHeader("X-API-Key", API_KEY)
            .build()

        val response = OkHttpClient().newCall(request).execute()
        val body = response.body?.string() ?: return
        if (body.isBlank() || !body.trimStart().startsWith("[")) return

        // Geçerli JSON mu kontrol et
        JSONArray(body)

        // Cache'e kaydet
        File(context.filesDir, CACHE_DOSYA).writeText(body)
        Log.d("DSP", "Sorular güncellendi: ${JSONArray(body).length()} soru")
    } catch (e: Exception) {
        Log.d("DSP", "Sunucudan güncelleme başarısız, cache kullanılacak")
    }
}

private fun getCacheJson(context: Context): String {
    val cacheFile = File(context.filesDir, CACHE_DOSYA)
    if (cacheFile.exists() && cacheFile.length() > 10) return cacheFile.readText()

    // Cache yoksa assets'teki yedek dosyayı kullan
    val assetsJson = context.assets.open("sorular.json").bufferedReader().use { it.readText() }
    // Assets'i de cache'e yaz ki bir sonraki açılışta tutarlı olsun
    cacheFile.writeText(assetsJson)
    return assetsJson
}

private fun parseSorular(jsonString: String): List<Soru> {
    return try {
        val jsonArray = JSONArray(jsonString)
        val soruListesi = mutableListOf<Soru>()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val seceneklerJson = obj.getJSONArray("secenekler")
            val secenekler = mutableListOf<String>()
            for (j in 0 until seceneklerJson.length()) {
                secenekler.add(seceneklerJson.getString(j))
            }
            val soruMetni = obj.getString("soruMetni")
            soruListesi.add(
                Soru(
                    soruMetni = soruMetni,
                    secenekler = secenekler,
                    dogruCevapIndeksi = obj.getInt("dogruCevapIndeksi"),
                    kategori = obj.optString("kategori", "Genel"),
                    id = sha256(soruMetni).take(16)
                )
            )
        }
        soruListesi
    } catch (e: Exception) {
        emptyList()
    }
}

fun sha256(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
