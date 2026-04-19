package com.gecelimani.isgrota

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat

class DashboardActivity : AppCompatActivity() {

    private lateinit var btnTema: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_dashboard)
        supportActionBar?.hide()

        // Her iki temada da tam ekran, içerik kaymasın
        val rootView = findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.rootLayout)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val controller = androidx.core.view.WindowInsetsControllerCompat(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            view.setPadding(0, 0, 0, 0)
            insets
        }

        val btnDenemeSinavi = findViewById<View>(R.id.btnDenemeSinaviContainer)
        val btnSonsuzTest = findViewById<View>(R.id.btnSonsuzTestContainer)
        val btnSoruEkle = findViewById<View>(R.id.btnSoruEkleContainer)
        val btnOylama = findViewById<View>(R.id.btnOylamaContainer)
        
        btnTema = findViewById(R.id.btnTema)
        val tvFooter = findViewById<TextView>(R.id.tvFooter)

        guncelleTemaIkonu()

        btnOylama.setOnClickListener {
            startActivity(Intent(this, OylamaActivity::class.java))
        }

        btnDenemeSinavi.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra("mod", "deneme"))
        }

        btnSonsuzTest.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).putExtra("mod", "sonsuz"))
        }

        btnSoruEkle.setOnClickListener {
            SoruEklemeSecenekleriBottomSheet().show(supportFragmentManager, "SoruEklemeSecenekleri")
        }

        btnTema.setOnClickListener {
            val sharedPref = getSharedPreferences("settings", Context.MODE_PRIVATE)
            val manuelSecimVar = sharedPref.contains("dark_mode")
            // Mevcut gece modu aktif mi?
            val simdikiGece = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            val nextMode = !simdikiGece

            with(sharedPref.edit()) {
                putBoolean("dark_mode", nextMode)
                apply()
            }

            if (nextMode) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            recreate()
        }

        btnTema.setOnLongClickListener {
            getSharedPreferences("settings", Context.MODE_PRIVATE).edit().remove("dark_mode").apply()
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            recreate()
            true
        }

        val btnInfo = findViewById<ImageView>(R.id.btnInfo)
        btnInfo.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.Theme_DSP_AlertDialog)
                .setTitle("🌙 Hoş Geldin!")
                .setMessage("Bu uygulama DSP belgesine hazırlananların birbirine destek olması için gönüllülük esasıyla geliştirilmiştir.\n\n" +
                        "📖 Soru Havuzu\n" +
                        "Sorular topluluk tarafından eklenir, oylanır ve onaylananlar havuza katılır. Sen de soru ekleyerek katkı sağlayabilirsin!\n\n" +
                        "🔒 Gizlilik\n" +
                        "Uygulama hiçbir kişisel veri toplamaz. IP adresi, cihaz bilgisi veya konum bilgisine erişilmez.\n\n" +
                        "🗳️ Oylama Sistemi\n" +
                        "Mükerrer oyu önlemek için anonim bir oturum kodu kullanılır. Bu kod cihazına bağlı değildir ve kimliğine ulaşılamaz.\n\n" +
                        "Soru, öneri veya şikayetlerin için:\nrasitbostan@tuta.io")
                .setPositiveButton("Anladım") { d, _ -> d.dismiss() }
                .show()
        }

        tvFooter.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, "https://gecelimani.com".toUri()))
        }
    }

    private fun guncelleTemaIkonu() {
        val simdikiGece = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        if (simdikiGece) {
            btnTema.setImageResource(R.drawable.ic_tema_light)
        } else {
            btnTema.setImageResource(R.drawable.ic_tema_dark)
        }
    }
}