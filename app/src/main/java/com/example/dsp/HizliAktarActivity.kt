package com.example.dsp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class HizliAktarActivity : AppCompatActivity() {

    private lateinit var etHamMetin: TextInputEditText
    private lateinit var btnAktar: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hizli_aktar)

        etHamMetin = findViewById(R.id.etHamMetin)
        btnAktar = findViewById(R.id.btnAktarVeDevam)

        if (intent.hasExtra("TANINAN_METIN")) {
            val taninanMetin = intent.getStringExtra("TANINAN_METIN") ?: ""
            etHamMetin.setText(taninanMetin)
            Toast.makeText(this, "Metin tanındı. Dilerseniz düzenleyip devam edin.", Toast.LENGTH_LONG).show()
        }

        btnAktar.setOnClickListener {
            val hamMetin = etHamMetin.text.toString().trim()
            if (hamMetin.isEmpty()) {
                Toast.makeText(this, "Lütfen bir metin yapıştırın", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            try {
                val intent = Intent(this, SoruSecActivity::class.java).apply {
                    putExtra("HAM_METIN", hamMetin)
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Toast.makeText(this, "Bir hata oluştu: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}