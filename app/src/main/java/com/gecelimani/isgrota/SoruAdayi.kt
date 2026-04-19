package com.gecelimani.isgrota

data class SoruAdayi(
    val numara: String,                     // "13."
    val hamMetin: String,                   // Sorunun tüm ham metni (şıklar ve cevap dahil)
    val soruMetni: String,                  // Sadece soru cümlesi
    val secenekler: Map<String, String>,    // A -> metin, B -> metin...
    val dogruCevapHarf: String              // "A"
)