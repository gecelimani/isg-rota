package com.gecelimani.isgrota

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class SoruSecActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvBaslik: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var btnIptal: Button
    private lateinit var adapter: SoruAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soru_sec)

        tvBaslik = findViewById(R.id.tvBaslik)
        recyclerView = findViewById(R.id.recyclerViewSorular)
        progressBar = findViewById(R.id.progressBar)
        btnIptal = findViewById(R.id.btnIptal)

        val hamMetin = intent.getStringExtra("HAM_METIN") ?: ""
        if (hamMetin.isEmpty()) {
            Toast.makeText(this, R.string.empty_text_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        recyclerView.visibility = View.GONE
        progressBar.visibility = View.VISIBLE
        tvBaslik.text = getString(R.string.parsing_questions)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val soruAdaylari = withTimeoutOrNull(10000) {
                    SoruParser.parse(hamMetin)
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (soruAdaylari.isEmpty()) {
                        Toast.makeText(this@SoruSecActivity,
                            R.string.no_questions_found_in_text,
                            Toast.LENGTH_LONG).show()
                        finish()
                        return@withContext
                    }

                    recyclerView.visibility = View.VISIBLE
                    tvBaslik.text = getString(R.string.questions_found_count, soruAdaylari.size)

                    adapter = SoruAdapter(soruAdaylari) { position, secilenSoru ->
                        if (secilenSoru.dogruCevapHarf.isEmpty()) {
                            Toast.makeText(this@SoruSecActivity,
                                R.string.correct_answer_not_detected,
                                Toast.LENGTH_LONG).show()
                        }
                        
                        adapter.setEklendi(position)

                        val intent = Intent(this@SoruSecActivity, SoruEkleActivity::class.java).apply {
                            putExtra("soru", secilenSoru.soruMetni)
                            putExtra("secenekA", secilenSoru.secenekler["A"] ?: "")
                            putExtra("secenekB", secilenSoru.secenekler["B"] ?: "")
                            putExtra("secenekC", secilenSoru.secenekler["C"] ?: "")
                            putExtra("secenekD", secilenSoru.secenekler["D"] ?: "")
                            putExtra("secenekE", secilenSoru.secenekler["E"] ?: "")
                            putExtra("dogruHarf", secilenSoru.dogruCevapHarf)
                            putExtra("hamMetin", secilenSoru.hamMetin) // Ham metni referans için gönderiyoruz
                        }
                        startActivity(intent)
                        // finish() kaldırıldı, böylece geri gelince listeden devam edebiliriz.
                    }
                    recyclerView.layoutManager = LinearLayoutManager(this@SoruSecActivity)
                    recyclerView.adapter = adapter
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@SoruSecActivity,
                        getString(R.string.parse_error_message, e.message),
                        Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        btnIptal.setOnClickListener { finish() }
    }
}