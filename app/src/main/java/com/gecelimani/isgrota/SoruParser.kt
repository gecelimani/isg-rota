package com.gecelimani.isgrota

import android.util.Log

object SoruParser {

    private fun isSikBaslangici(line: String, mevcutSoruMetni: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.length < 2) return false

        val gürültüKelimeleri = listOf("KİTAPÇIĞI", "TESTİ", "SINAVI", "DENEME", "SAYFA", "İSG", "2024", "2025")
        if (gürültüKelimeleri.any { trimmed.uppercase().contains(it) }) return false

        val firstChar = trimmed[0].uppercaseChar()
        val secondChar = trimmed.getOrNull(1) ?: return false

        // A) B) C) D) E) kontrolü
        if (firstChar !in listOf('A', 'B', 'C', 'D', 'E')) return false
        if (secondChar !in listOf(')', '.', '-', ':')) return false

        // Soru bitmeden gelen çok kısa şıkları (yan sütun kayması) engelle
        val hasQuestionMarker = mevcutSoruMetni.contains("?") || 
                               mevcutSoruMetni.contains("hangisi", ignoreCase = true) ||
                               mevcutSoruMetni.contains("hangileri", ignoreCase = true)
        
        if (!hasQuestionMarker && trimmed.length < 10) return false

        return true
    }

    private fun cleanSikMetni(line: String): String {
        return line.trim().replaceFirst(Regex("^[A-Eİ][).:-]\\s*"), "").trim()
    }

    private fun fixNumeral(raw: String): String {
        val clean = raw.trim().uppercase().replace(Regex("[.)-]$"), "")
        val mapped = when (clean) {
            "L", "|", "!", "1", "İ", "LL", "LLL" -> {
                when {
                    clean.length == 1 -> "I"
                    clean == "LL" -> "II"
                    clean == "LLL" -> "III"
                    else -> clean
                }
            }
            "V1" -> "VI"
            "VIL" -> "VII"
            "V111" -> "VIII"
            else -> clean
        }
        val validRoman = Regex("^(I|II|III|IV|V|VI|VII|VIII|IX|X)$")
        
        return when {
            mapped.matches(validRoman) -> "$mapped."
            else -> ""
        }
    }

    private fun splitNumeralAndText(line: String): Pair<String, String> {
        val trimmed = line.trim()
        // Madde başlarını (I. 1. vb) yakala. Nokta/Parantez zorunlu.
        val regex = Regex("^([IVX|!1Lİ]{1,4}|\\d+)([.)-]\\s+)(.*)", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        
        if (match != null) {
            val fixed = fixNumeral(match.groupValues[1])
            if (fixed.isNotEmpty()) {
                return Pair(fixed, match.groupValues[3])
            }
        }
        
        // Noktasız ama büyük harfle başlayan Romen rakamı kontrolü (I Metin gibi)
        val regexNoDot = Regex("^([IVX|!1Lİ]{1,4})\\s+([A-ZÇĞİÖŞÜ].*)", RegexOption.IGNORE_CASE)
        val matchNoDot = regexNoDot.find(trimmed)
        if (matchNoDot != null) {
            val fixed = fixNumeral(matchNoDot.groupValues[1])
            if (fixed.isNotEmpty()) {
                return Pair(fixed, matchNoDot.groupValues[2])
            }
        }

        return Pair("", trimmed)
    }

    private fun isNumeral(line: String): Boolean = splitNumeralAndText(line).first.isNotEmpty()

    private fun repairSplitLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var currentItem: StringBuilder? = null
        val stemKeywords = listOf("hangisi", "hangileri", "yanlıştır", "doğrudur", "söylenemez", "belirtilmiştir", "hangisidir")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            val (num, rest) = splitNumeralAndText(trimmed)
            val isStem = stemKeywords.any { trimmed.contains(it, ignoreCase = true) } || trimmed.contains("?")

            if (num.isNotEmpty() && !isStem) {
                // Mıknatıs: Önceki maddeyi kaydet, yenisini başlat
                currentItem?.let { result.add(it.toString().trim()) }
                currentItem = StringBuilder("$num $rest")
            } else if (isStem) {
                // Soru kökü geldi, mıknatıs devreden çıkar
                currentItem?.let { result.add(it.toString().trim()) }
                currentItem = null
                result.add(trimmed)
            } else {
                // Düz metin: Eğer bir madde (I. II.) açıksa ona yapışır, değilse serbest kalır
                if (currentItem != null) {
                    if (!currentItem.endsWith("-")) currentItem.append(" ")
                    currentItem.append(trimmed)
                } else {
                    result.add(trimmed)
                }
            }
        }
        currentItem?.let { result.add(it.toString().trim()) }
        return result
    }

    fun parse(hamMetin: String): List<SoruAdayi> {
        var processedText = hamMetin
            .replace(Regex("(?i)A KİTAPÇIĞI"), "")
            .replace(Regex("(?i)\\d{4}\\s*İSG/\\d+– [A-Z]+"), "")
            .replace(Regex("(?i)2025 İSG/1– DSP"), "")
            .replace(Regex("([a-zA-ZçğıöşüÇĞİÖŞÜ])\\s*-\\s*\\n\\s*([a-zA-ZçğıöşüÇĞİÖŞÜ])"), "$1$2")
            .replace("\r", "")
        
        val allLines = processedText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val soruAdaylari = mutableListOf<SoruAdayi>()
        var index = 0

        while (index < allLines.size) {
            val soruMetniSatirlari = mutableListOf<String>()
            
            // 1. Soru Metnini ve Maddeleri Topla
            while (index < allLines.size) {
                val line = allLines[index]
                if (isSikBaslangici(line, soruMetniSatirlari.joinToString(" "))) break
                
                // Yeni bir soru numarası mı? (Örn: "7.")
                if (soruMetniSatirlari.isNotEmpty() && line.matches(Regex("^\\d+[.)-].*"))) {
                    if (!isNumeral(line)) break // Romen maddesi değilse yeni sorudur
                }
                
                soruMetniSatirlari.add(line)
                index++
            }

            if (soruMetniSatirlari.isEmpty()) { index++; continue }

            // 2. Mıknatıs ve Romen Onarımı
            val repairedLines = repairSplitLines(soruMetniSatirlari)
            val finalSoruMetni = repairedLines.joinToString("\n")

            // 3. Şıkları Topla
            val secenekler = mutableMapOf("A" to "", "B" to "", "C" to "", "D" to "", "E" to "")
            var lastHarf = ""

            while (index < allLines.size) {
                val line = allLines[index]
                // Yeni soru başladığında şık toplamayı kes
                if (line.matches(Regex("^\\d+[.)-].*")) && !isNumeral(line)) break
                
                if (isSikBaslangici(line, finalSoruMetni)) {
                    val harf = line[0].uppercaseChar().toString()
                    secenekler[harf] = cleanSikMetni(line)
                    lastHarf = harf
                } else if (lastHarf.isNotEmpty()) {
                    // Şık metni alt satıra sarkmışsa ekle
                    secenekler[lastHarf] = (secenekler[lastHarf] ?: "") + " " + line
                }
                index++
            }

            // User Request: E seçeneğinden sonra boşluk bırak
            if (secenekler["E"]?.isNotEmpty() == true) {
                secenekler["E"] = secenekler["E"] + "\n"
            }

            soruAdaylari.add(SoruAdayi(
                numara = "${soruAdaylari.size + 1}.",
                hamMetin = finalSoruMetni,
                soruMetni = finalSoruMetni,
                secenekler = secenekler,
                dogruCevapHarf = ""
            ))
        }
        return soruAdaylari
    }
}
