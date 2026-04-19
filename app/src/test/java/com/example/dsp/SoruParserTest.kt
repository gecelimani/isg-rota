package com.example.dsp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoruParserTest {

    @Test
    fun testSimpleQuestion() {
        val hamMetin = """
            1. Aşağıdakilerden hangisi bir meyvedir?
            A) Elma
            B) Masa
            C) Kalem
            D) Defter
            E) Bilgisayar
        """.trimIndent()

        val sorular = SoruParser.parse(hamMetin)
        assertEquals(1, sorular.size)
        val soru = sorular[0]
        assertTrue(soru.soruMetni.contains("meyvedir"))
        assertEquals("Elma", soru.secenekler["A"])
        assertEquals("Masa", soru.secenekler["B"])
        assertEquals("Kalem", soru.secenekler["C"])
        assertEquals("Defter", soru.secenekler["D"])
        assertEquals("Bilgisayar\n", soru.secenekler["E"])
    }

    @Test
    fun testQuestionWithRomanNumerals() {
        val hamMetin = """
            13. İş sağlığı ve güvenliği ile ilgili;
            I. İşveren sorumludur.
            II. Çalışanlar uymalıdır.
            III. Devlet denetler.
            yukarıdakilerden hangileri doğrudur?
            A) Yalnız I
            B) I ve II
            C) II ve III
            D) I ve III
            E) I, II ve III
        """.trimIndent()

        val sorular = SoruParser.parse(hamMetin)
        assertEquals(1, sorular.size)
        val soru = sorular[0]
        
        // Check if Roman numerals are correctly grouped or present in the question text
        assertTrue(soru.soruMetni.contains("I. İşveren sorumludur."))
        assertTrue(soru.soruMetni.contains("II. Çalışanlar uymalıdır."))
        assertTrue(soru.soruMetni.contains("III. Devlet denetler."))
        assertTrue(soru.soruMetni.contains("hangileri doğrudur?"))
        
        assertEquals("Yalnız I", soru.secenekler["A"])
    }

    @Test
    fun testOCRArtifactsAndMultipleQuestions() {
        val hamMetin = """
            2025 İSG/1– DSP
            1. Aşağıdaki?
            A) Şık 1
            B) Şık 2
            C) Şık 3
            D) Şık 4
            E) Şık 5
            2. Diğer soru?
            A) A şıkkı
            B) B şıkkı
            C) C şıkkı
            D) D şıkkı
            E) E şıkkı
        """.trimIndent()

        val sorular = SoruParser.parse(hamMetin)
        assertEquals(2, sorular.size)
        assertEquals("Aşağıdaki?", sorular[0].soruMetni.trim())
        assertEquals("Diğer soru?", sorular[1].soruMetni.trim())
    }

    @Test
    fun testShortOptionsWithoutQuestionMarker() {
        // Test issue: isSikBaslangici ignores short options if no question marker is found
        val hamMetin = """
            1. Hangisi?
            A) 1
            B) 2
            C) 3
            D) 4
            E) 5
        """.trimIndent()

        val sorular = SoruParser.parse(hamMetin)
        assertEquals(1, sorular.size)
        // If "Hangisi?" is not recognized as having a question marker (though it has '?'), 
        // and şıklar are very short, they might be missed.
        assertEquals("1", sorular[0].secenekler["A"])
    }
}
