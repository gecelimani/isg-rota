package com.example.dsp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class HizliMetinDuzenleActivity : AppCompatActivity() {

    private lateinit var etHamMetinEditor: EditText
    private lateinit var btnSorulariAyikla: Button
    private lateinit var btnTemizle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hizli_metin_duzenle)

        etHamMetinEditor = findViewById(R.id.etHamMetinEditor)
        btnSorulariAyikla = findViewById(R.id.btnSorulariAyikla)
        btnTemizle = findViewById(R.id.btnTemizle)

        val gelenMetin = intent.getStringExtra("HAM_METIN") ?: ""
        etHamMetinEditor.setText(gelenMetin)

        btnSorulariAyikla.setOnClickListener {
            val duzenlenmisMetin = etHamMetinEditor.text.toString().trim()
            if (duzenlenmisMetin.isEmpty()) {
                Toast.makeText(this, "Metin boş olamaz!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SoruSecActivity::class.java)
            intent.putExtra("HAM_METIN", duzenlenmisMetin)
            startActivity(intent)
        }

        btnTemizle.setOnClickListener {
            etHamMetinEditor.setText("")
        }
    }
}