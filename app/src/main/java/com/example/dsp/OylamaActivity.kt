package com.example.dsp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class OylamaActivity : AppCompatActivity() {

    private lateinit var progressBar: ProgressBar
    private lateinit var scrollView: View
    private lateinit var tvSayac: TextView
    private lateinit var tvSoru: TextView
    private lateinit var tvA: TextView
    private lateinit var tvB: TextView
    private lateinit var tvC: TextView
    private lateinit var tvD: TextView
    private lateinit var tvE: TextView
    private lateinit var tvDogruCevap: TextView
    private lateinit var tvIstatistik: TextView
    private lateinit var btnOnayla: MaterialButton
    private lateinit var btnReddet: MaterialButton

    private val client = OkHttpClient()
    private var bekleyenSorular = mutableListOf<JSONObject>()
    private var mevcutIndex = 0
    private lateinit var token: String

    companion object {
        private const val BEKLEYEN_URL = "https://gecelimani.com/api_x7k9p/bekleyen_sorular.json"
        private const val OY_VER_URL = "https://gecelimani.com/api_x7k9p/oy_ver.php"
        private const val API_KEY = BuildConfig.API_KEY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_oylama)

        progressBar = findViewById(R.id.progressBar)
        scrollView = findViewById(R.id.scrollView)
        tvSayac = findViewById(R.id.tvSayac)
        tvSoru = findViewById(R.id.tvSoru)
        tvA = findViewById(R.id.tvSecenekA)
        tvB = findViewById(R.id.tvSecenekB)
        tvC = findViewById(R.id.tvSecenekC)
        tvD = findViewById(R.id.tvSecenekD)
        tvE = findViewById(R.id.tvSecenekE)
        tvDogruCevap = findViewById(R.id.tvDogruCevap)
        tvIstatistik = findViewById(R.id.tvIstatistik)
        btnOnayla = findViewById(R.id.btnOnayla)
        btnReddet = findViewById(R.id.btnReddet)

        token = App.getToken(this)

        sorulariYukle()

        btnOnayla.setOnClickListener { oyVer("onay") }
        btnReddet.setOnClickListener { oyVer("red") }
    }

    private fun sorulariYukle() {
        progressBar.visibility = View.VISIBLE
        scrollView.visibility = View.GONE

        val request = Request.Builder()
            .url(BEKLEYEN_URL)
            .get()
            .addHeader("X-API-Key", API_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            @Suppress("UNUSED_PARAMETER")
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@OylamaActivity, R.string.connection_error, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                Log.d("OYLAMA_JSON", "Gelen JSON: ${body.take(200)}")
                runOnUiThread {
                    try {
                        val jsonArray = JSONArray(body)
                        bekleyenSorular.clear()
                        for (i in 0 until jsonArray.length()) {
                            bekleyenSorular.add(jsonArray.getJSONObject(i))
                        }

                        bekleyenSorular = bekleyenSorular.filter { soru ->
                            val oylarObj = soru.optJSONObject("oylar")
                            val oylayanlar = oylarObj?.optJSONObject("oylayanlar")
                            oylayanlar == null || !oylayanlar.has(token)
                        }.toMutableList()

                        if (bekleyenSorular.isEmpty()) {
                            Toast.makeText(this@OylamaActivity, R.string.no_pending_questions, Toast.LENGTH_LONG).show()
                            finish()
                        } else {
                            mevcutIndex = 0
                            soruyuGoster(mevcutIndex)
                            progressBar.visibility = View.GONE
                            scrollView.visibility = View.VISIBLE
                        }
                    } catch (e: JSONException) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@OylamaActivity, "Veri ayrıştırma hatası: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e("OYLAMA", "JSON ayrıştırma hatası", e)
                    }
                }
            }
        })
    }

    private fun soruyuGoster(index: Int) {
        if (index < 0 || index >= bekleyenSorular.size) {
            Toast.makeText(this, R.string.no_more_questions, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val soru = bekleyenSorular[index]

        tvSayac.text = "${index + 1} / ${bekleyenSorular.size}"
        tvSoru.text = soru.optString("soru", "Soru metni yok")

        try {
            val secenekler = soru.getJSONArray("secenekler")
            tvA.text = getString(R.string.option_format, "A", secenekler.getString(0))
            tvB.text = getString(R.string.option_format, "B", secenekler.getString(1))
            tvC.text = getString(R.string.option_format, "C", secenekler.getString(2))
            tvD.text = getString(R.string.option_format, "D", secenekler.getString(3))
            tvE.text = getString(R.string.option_format, "E", secenekler.getString(4))
        } catch (e: JSONException) {
            Toast.makeText(this, "Seçenekler okunamadı", Toast.LENGTH_SHORT).show()
            Log.e("OYLAMA", "Seçenekler JSON hatası", e)
            return
        }

        val dogruIndex = soru.optInt("dogru", -1)
        val dogruHarf = when (dogruIndex) {
            1 -> "A"
            2 -> "B"
            3 -> "C"
            4 -> "D"
            5 -> "E"
            else -> "?"
        }
        tvDogruCevap.text = getString(R.string.correct_answer_label, dogruHarf)

        val oylar = soru.optJSONObject("oylar")
        if (oylar != null) {
            val onay = oylar.optInt("onay", 0)
            val red = oylar.optInt("red", 0)
            tvIstatistik.text = getString(R.string.vote_stats, onay, red)
        } else {
            tvIstatistik.text = "✅ 0  |  ❌ 0"
        }
    }

    private fun oyVer(oyTipi: String) {
        if (bekleyenSorular.isEmpty() || mevcutIndex >= bekleyenSorular.size) {
            Toast.makeText(this, R.string.no_questions_to_vote, Toast.LENGTH_SHORT).show()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnOnayla.isEnabled = false
        btnReddet.isEnabled = false

        val soru = bekleyenSorular[mevcutIndex]
        val soruId = soru.optString("id", "")
        if (soruId.isEmpty()) {
            Toast.makeText(this, "Soru ID bulunamadı", Toast.LENGTH_SHORT).show()
            progressBar.visibility = View.GONE
            btnOnayla.isEnabled = true
            btnReddet.isEnabled = true
            return
        }

        val json = JSONObject()
        json.put("soru_id", soruId)
        json.put("oy", oyTipi)
        json.put("token", token)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(OY_VER_URL)
            .post(body)
            .addHeader("X-API-Key", API_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            @Suppress("UNUSED_PARAMETER")
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    btnOnayla.isEnabled = true
                    btnReddet.isEnabled = true
                    Toast.makeText(this@OylamaActivity, R.string.connection_error, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val obj = JSONObject(body)
                        val durum = obj.getString("durum")
                        val mesaj = obj.getString("mesaj")

                        if (durum == "basarili") {
                            Toast.makeText(this@OylamaActivity, mesaj, Toast.LENGTH_SHORT).show()
                            bekleyenSorular.removeAt(mevcutIndex)

                            if (bekleyenSorular.isEmpty()) {
                                Toast.makeText(this@OylamaActivity, R.string.no_pending_questions, Toast.LENGTH_LONG).show()
                                finish()
                            } else {
                                if (mevcutIndex >= bekleyenSorular.size) {
                                    mevcutIndex = 0
                                }
                                soruyuGoster(mevcutIndex)
                            }
                        } else {
                            Toast.makeText(this@OylamaActivity, mesaj, Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(this@OylamaActivity, "Sunucu yanıtı hatalı: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("OYLAMA", "Yanıt JSON hatası", e)
                    }
                    progressBar.visibility = View.GONE
                    btnOnayla.isEnabled = true
                    btnReddet.isEnabled = true
                }
            }
        })
    }
}