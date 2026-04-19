package com.example.dsp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

class SoruEkleActivity : AppCompatActivity() {

    private lateinit var etSoru: EditText
    private lateinit var etSecenek1: EditText
    private lateinit var etSecenek2: EditText
    private lateinit var etSecenek3: EditText
    private lateinit var etSecenek4: EditText
    private lateinit var etSecenek5: EditText
    private lateinit var spinnerDogru: AutoCompleteTextView
    private lateinit var btnGonder: Button

    private lateinit var tvHamMetinReferans: TextView
    private lateinit var btnVazgec: Button

    private val secenekler = arrayOf("A Şıkkı", "B Şıkkı", "C Şıkkı", "D Şıkkı", "E Şıkkı")

    companion object {
        private const val API_URL = "https://gecelimani.com/api_x7k9p/kaydet.php"
        private const val API_KEY = BuildConfig.API_KEY
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soru_ekle)

        etSoru = findViewById(R.id.etSoru)
        etSecenek1 = findViewById(R.id.etSecenek1)
        etSecenek2 = findViewById(R.id.etSecenek2)
        etSecenek3 = findViewById(R.id.etSecenek3)
        etSecenek4 = findViewById(R.id.etSecenek4)
        etSecenek5 = findViewById(R.id.etSecenek5)
        spinnerDogru = findViewById(R.id.spinnerDogru)
        btnGonder = findViewById(R.id.btnGonder)
        tvHamMetinReferans = findViewById(R.id.tvHamMetinReferans)
        btnVazgec = findViewById(R.id.btnVazgec)

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, secenekler)
        spinnerDogru.setAdapter(adapter)

        if (intent.hasExtra("soru")) {
            etSoru.setText(intent.getStringExtra("soru"))
            etSecenek1.setText(intent.getStringExtra("secenekA"))
            etSecenek2.setText(intent.getStringExtra("secenekB"))
            etSecenek3.setText(intent.getStringExtra("secenekC"))
            etSecenek4.setText(intent.getStringExtra("secenekD"))
            etSecenek5.setText(intent.getStringExtra("secenekE"))
            tvHamMetinReferans.text = intent.getStringExtra("hamMetin")

            val dogruHarf = intent.getStringExtra("dogruHarf") ?: ""
            val dogruIndex = when (dogruHarf) {
                "A" -> 0
                "B" -> 1
                "C" -> 2
                "D" -> 3
                "E" -> 4
                else -> -1
            }
            if (dogruIndex != -1) {
                spinnerDogru.setText(secenekler[dogruIndex], false)
            }
        }

        btnGonder.setOnClickListener {
            soruyuGonder()
        }

        btnVazgec.setOnClickListener {
            finish()
        }
    }

    private fun soruyuGonder() {
        val soru = etSoru.text.toString().trim()
        val s1 = etSecenek1.text.toString().trim()
        val s2 = etSecenek2.text.toString().trim()
        val s3 = etSecenek3.text.toString().trim()
        val s4 = etSecenek4.text.toString().trim()
        val s5 = etSecenek5.text.toString().trim()

        val dogruIndex = when (spinnerDogru.text.toString()) {
            "A Şıkkı" -> 1
            "B Şıkkı" -> 2
            "C Şıkkı" -> 3
            "D Şıkkı" -> 4
            "E Şıkkı" -> 5
            else -> 0
        }

        if (soru.isEmpty() || s1.isEmpty() || s2.isEmpty() || s3.isEmpty() || s4.isEmpty() || s5.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (dogruIndex == 0) {
            Toast.makeText(this, R.string.select_correct_answer, Toast.LENGTH_SHORT).show()
            return
        }

        val json = JSONObject()
        try {
            json.put("soru", soru)
            json.put("secenek1", s1)
            json.put("secenek2", s2)
            json.put("secenek3", s3)
            json.put("secenek4", s4)
            json.put("secenek5", s5)
            json.put("dogru", dogruIndex)
        } catch (e: JSONException) {
            e.printStackTrace()
            return
        }

        val client = OkHttpClient()
        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url(API_URL)
            .post(body)
            .addHeader("X-API-Key", API_KEY)
            .build()

        client.newCall(request).enqueue(object : Callback {
            @Suppress("UNUSED_PARAMETER")
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@SoruEkleActivity, R.string.connection_error, Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val cevap = response.body?.string() ?: ""
                runOnUiThread {
                    try {
                        val obj = JSONObject(cevap)
                        val mesaj = obj.getString("mesaj")
                        Toast.makeText(this@SoruEkleActivity, mesaj, Toast.LENGTH_SHORT).show()
                        if (obj.getString("durum") == "basarili") {
                            finish()
                        }
                    } catch (e: JSONException) {
                        Toast.makeText(this@SoruEkleActivity, R.string.server_response_error, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}