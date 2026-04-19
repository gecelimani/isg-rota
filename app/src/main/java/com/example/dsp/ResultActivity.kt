package com.example.dsp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        supportActionBar?.hide()

        val tvDogru = findViewById<TextView>(R.id.tvDogru)
        val tvYanlis = findViewById<TextView>(R.id.tvYanlis)
        val tvBasari = findViewById<TextView>(R.id.tvBasari)
        val tvToplam = findViewById<TextView>(R.id.tvToplam)
        val cpBasari = findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.cpBasari)
        val btnAnaSayfa = findViewById<Button>(R.id.btnAnaSayfa)
        val btnTekrarDene = findViewById<Button>(R.id.btnTekrarDene)

        val dogru = intent.getIntExtra("dogru", 0)
        val yanlis = intent.getIntExtra("yanlis", 0)
        val toplam = intent.getIntExtra("toplam", 0)
        val basari = intent.getIntExtra("basari", 0)

        tvDogru.text = dogru.toString()
        tvYanlis.text = yanlis.toString()
        tvBasari.text = getString(R.string.percentage_format, basari)
        tvToplam.text = toplam.toString()
        cpBasari.setProgress(basari, true)

        btnAnaSayfa.setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finishAffinity()
        }

        btnTekrarDene.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(this@ResultActivity, DashboardActivity::class.java))
                finishAffinity()
            }
        })
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}