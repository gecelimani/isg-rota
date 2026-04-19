package com.gecelimani.isgrota

data class Soru(
    val soruMetni: String,
    val secenekler: List<String>,
    val dogruCevapIndeksi: Int,
    val kategori: String = "Genel",
    val id: String = ""
)