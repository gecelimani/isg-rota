package com.example.dsp

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatDelegate
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar

class App : Application() {

    companion object {
        private const val TOKEN_AL_URL = "https://gecelimani.com/api_x7k9p/token_al.php"
        private const val API_KEY = BuildConfig.API_KEY
        private const val PREF_NAME = "dsp_prefs"
        private const val KEY_TOKEN = "oylama_token"

        fun getToken(context: Context): String {
            return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_TOKEN, "") ?: ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
        temaUygula()
        tokenAlVeKaydet()
        Thread { sorulariSunucudanGuncelle(this) }.start()
        gunlukBildirimKur()
    }

    private fun temaUygula() {
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        when {
            !prefs.contains("dark_mode") ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            prefs.getBoolean("dark_mode", true) ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else ->
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        }
    }

    private fun gunlukBildirimKur() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, BildirimAlici::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        if (intent != null) return // Alarm zaten kurulu

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, BildirimAlici::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Her gün saat 20:00'de bildirim
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 20)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, 1)
        }

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    private fun tokenAlVeKaydet() {
        val prefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_TOKEN, "")!!.isNotEmpty()) return

        val body = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(TOKEN_AL_URL)
            .post(body)
            .addHeader("X-API-Key", API_KEY)
            .build()

        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: return
                    if (body.isBlank()) return
                    val token = JSONObject(body).optString("token", "")
                    if (token.isNotEmpty()) {
                        prefs.edit().putString(KEY_TOKEN, token).apply()
                    }
                } catch (e: Exception) {}
            }
        })
    }
}
