package com.gecelimani.isgrota

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var soruListesi: List<Soru>
    private var mevcutSoruIndeksi = 0
    private var dogruSayisi = 0
    private var yanlisSayisi = 0
    private var cevapVerildi = false

    private lateinit var soruMetni: TextView
    private lateinit var tvIlerleme: TextView
    private lateinit var secenekGroup: RadioGroup
    private lateinit var secenek1: RadioButton
    private lateinit var secenek2: RadioButton
    private lateinit var secenek3: RadioButton
    private lateinit var secenek4: RadioButton
    private lateinit var secenek5: RadioButton
    private lateinit var sonrakiButton: Button
    private lateinit var bitirButton: Button
    private lateinit var progressSoru: com.google.android.material.progressindicator.LinearProgressIndicator
    private lateinit var btnRaporEt: android.widget.ImageView
    private val client = OkHttpClient()

    companion object {
        private const val RAPOR_URL = "https://gecelimani.com/api_x7k9p/raporla.php"
        private const val API_KEY = BuildConfig.API_KEY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        soruMetni = findViewById(R.id.soruMetni)
        tvIlerleme = findViewById(R.id.tvIlerleme)
        secenekGroup = findViewById(R.id.secenekGroup)
        secenek1 = findViewById(R.id.secenek1)
        secenek2 = findViewById(R.id.secenek2)
        secenek3 = findViewById(R.id.secenek3)
        secenek4 = findViewById(R.id.secenek4)
        secenek5 = findViewById(R.id.secenek5)
        sonrakiButton = findViewById(R.id.sonrakiButton)
        bitirButton = findViewById(R.id.bitirButton)
        progressSoru = findViewById(R.id.progressSoru)
        btnRaporEt = findViewById(R.id.btnRaporEt)

        btnRaporEt.setOnClickListener {
            val soru = soruListesi[mevcutSoruIndeksi]
            MaterialAlertDialogBuilder(this, R.style.Theme_DSP_AlertDialog)
                .setTitle("Hatalı Soru Bildir")
                .setMessage("Bu soruyu hatalı olarak bildirmek istiyor musun?")
                .setNegativeButton("İptal", null)
                .setPositiveButton("Bildir") { _, _ -> soruRaporla(soru) }
                .show()
        }

        val mod = intent.getStringExtra("mod") ?: "deneme"
        val tumSorular = loadSorularFromAssets(this)

        soruListesi = when (mod) {
            "deneme" -> tumSorular.shuffled().take(50)
            "sonsuz" -> tumSorular.shuffled()
            else -> tumSorular.shuffled().take(50)
        }.map { soru ->
            val karisikSecenekler = soru.secenekler.toMutableList()
            val dogruCevap = soru.secenekler[soru.dogruCevapIndeksi]
            karisikSecenekler.shuffle()
            val yeniDogruIndeks = karisikSecenekler.indexOf(dogruCevap)
            Soru(soru.soruMetni, karisikSecenekler, yeniDogruIndeks, soru.kategori)
        }

        if (soruListesi.isEmpty()) {
            Toast.makeText(this, R.string.no_questions_found, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        soruyuGoster(mevcutSoruIndeksi)

        secenekGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != -1 && !cevapVerildi) {
                cevapVerildi = true
                val seciliRadioButton = findViewById<RadioButton>(checkedId)
                val seciliIndeks = secenekGroup.indexOfChild(seciliRadioButton)

                if (seciliIndeks == soruListesi[mevcutSoruIndeksi].dogruCevapIndeksi) {
                    dogruSayisi++
                    Toast.makeText(this, R.string.correct_toast, Toast.LENGTH_SHORT).show()
                } else {
                    yanlisSayisi++
                    val dogruCevapHarf = when (soruListesi[mevcutSoruIndeksi].dogruCevapIndeksi) {
                        0 -> "A"; 1 -> "B"; 2 -> "C"; 3 -> "D"; 4 -> "E"; else -> ""
                    }
                    Toast.makeText(this, getString(R.string.wrong_toast, dogruCevapHarf), Toast.LENGTH_LONG).show()
                }

                secenek1.isEnabled = false
                secenek2.isEnabled = false
                secenek3.isEnabled = false
                secenek4.isEnabled = false
                secenek5.isEnabled = false
                sonrakiButton.isEnabled = true
            }
        }

        sonrakiButton.setOnClickListener {
            mevcutSoruIndeksi++
            if (mevcutSoruIndeksi < soruListesi.size) {
                soruyuGoster(mevcutSoruIndeksi)
                cevapVerildi = false
            } else {
                sinaviBitir()
            }
        }

        bitirButton.setOnClickListener {
            MaterialAlertDialogBuilder(this, R.style.Theme_DSP_AlertDialog)
                .setTitle(R.string.finish_exam_title)
                .setMessage(R.string.finish_exam_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ -> sinaviBitir() }
                .show()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                MaterialAlertDialogBuilder(this@MainActivity, R.style.Theme_DSP_AlertDialog)
                    .setTitle(R.string.exit_exam_title)
                    .setMessage(R.string.exit_exam_message)
                    .setNegativeButton(R.string.no, null)
                    .setPositiveButton(R.string.yes) { _, _ -> finish() }
                    .show()
            }
        })
    }

    private fun soruRaporla(soru: Soru) {
        val json = JSONObject().apply {
            put("soru_id", soru.id)
            put("soru_metni", soru.soruMetni)
        }
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(RAPOR_URL)
            .post(body)
            .addHeader("X-API-Key", API_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, R.string.connection_error, Toast.LENGTH_SHORT).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Rapor iletildi, teşekkürler!", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun soruyuGoster(index: Int) {
        val soru = soruListesi[index]
        soruMetni.text = getString(R.string.question_format, index + 1, soru.soruMetni)
        tvIlerleme.text = getString(R.string.progress_format, index + 1, soruListesi.size)
        
        // Progress barı güncelle
        val progress = ((index + 1).toFloat() / soruListesi.size.toFloat() * 100).toInt()
        progressSoru.setProgress(progress, true)

        secenek1.text = soru.secenekler[0]
        secenek2.text = soru.secenekler[1]
        secenek3.text = soru.secenekler[2]
        secenek4.text = soru.secenekler[3]
        secenek5.text = soru.secenekler[4]

        secenek1.isEnabled = true
        secenek2.isEnabled = true
        secenek3.isEnabled = true
        secenek4.isEnabled = true
        secenek5.isEnabled = true
        sonrakiButton.isEnabled = false
        secenekGroup.clearCheck()
    }

    private fun sinaviBitir() {
        val basari = if (soruListesi.isNotEmpty()) {
            (dogruSayisi * 100) / soruListesi.size
        } else {
            0
        }
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra("dogru", dogruSayisi)
            putExtra("yanlis", yanlisSayisi)
            putExtra("toplam", soruListesi.size)
            putExtra("basari", basari)
        }
        startActivity(intent)
        finish()
    }
}