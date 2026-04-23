package com.gecelimani.isgrota

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.*

class SoruKirpiciActivity : AppCompatActivity() {

    private lateinit var cropImageView: CropImageView
    private lateinit var btnEkle: Button
    private lateinit var btnBitti: Button
    private lateinit var progressBar: ProgressBar
    
    private val biriktirilenMetin = StringBuilder()
    private var soruSayisi = 0

    private var pdfPath: String? = null
    private var pageIndex: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_soru_kirpici)

        cropImageView = findViewById(R.id.cropImageView)
        btnEkle = findViewById(R.id.btnEkle)
        btnBitti = findViewById(R.id.btnBitti)
        progressBar = findViewById(R.id.cropProgressBar)

        val imageUri = intent.getParcelableExtra<Uri>("IMAGE_URI")
        pdfPath = intent.getStringExtra("PDF_PATH")
        pageIndex = intent.getIntExtra("PAGE_INDEX", -1)
        if (imageUri == null) {
            Toast.makeText(this, "Görsel yüklenemedi", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Başlangıç ayarları: Kırpma çerçevesini gizli tutuyoruz
        cropImageView.setImageUriAsync(imageUri)
        cropImageView.setFixedAspectRatio(false)
        cropImageView.guidelines = CropImageView.Guidelines.ON
        cropImageView.isShowCropOverlay = false // Çerçeveyi başta gizle
        
        // Köşeleri belirginleştirme ayarları (Doğrudan özellik erişimi)
        // Not: 4.5.0 sürümünde bu alanlar public field olarak erişilebilir
        cropImageView.apply {
            setMultiTouchEnabled(true)
            setCenterMoveEnabled(true)
        }
        
        btnEkle.text = "KIRPMAYI BAŞLAT"
        btnEkle.setBackgroundColor(android.graphics.Color.parseColor("#2196F3")) // Mavi
        
        btnEkle.setOnClickListener {
            if (btnEkle.text == "KIRPMAYI BAŞLAT" || btnEkle.text == "SIRADAKİ SORU İÇİN KIRP") {
                // Çerçeveyi ekrana getiriyoruz
                cropImageView.isShowCropOverlay = true
                
                // ZORLAMA: Kırpma kutusunu ekranın ortasına %50 boyutunda zorla getir
                val imageRect = cropImageView.wholeImageRect
                if (imageRect != null) {
                    val width = imageRect.width()
                    val height = imageRect.height()
                    val cropRect = android.graphics.Rect(
                        width / 4, height / 4, (width * 3) / 4, (height * 3) / 4
                    )
                    cropImageView.cropRect = cropRect
                }

                btnEkle.text = "BU ALANI SEÇ VE EKLE"
                btnEkle.setBackgroundColor(android.graphics.Color.parseColor("#4CAF50")) // Yeşil
            } else {
                // Seçilen alanı işle
                cropAndRecognize()
            }
        }

        btnBitti.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra("BIRIKTIRILEN_METIN", biriktirilenMetin.toString())
            setResult(Activity.RESULT_OK, resultIntent)
            
            // Eğer PDF'den gelmiyorsak (Görselden Ekle modundaysak) doğrudan düzenleme ekranına git
            if (pdfPath == null) {
                val editIntent = Intent(this, HizliMetinDuzenleActivity::class.java).apply {
                    putExtra("HAM_METIN", biriktirilenMetin.toString())
                }
                startActivity(editIntent)
            }
            finish()
        }
    }

    private fun cropAndRecognize() {
        val cropRect = cropImageView.cropRect
        val wholeRect = cropImageView.wholeImageRect
        
        if (cropRect == null || wholeRect == null) {
            Toast.makeText(this, "Kırpma alanı geçersiz", Toast.LENGTH_SHORT).show()
            return
        }

        val normalizedRect = android.graphics.RectF(
            cropRect.left.toFloat() / wholeRect.width(),
            cropRect.top.toFloat() / wholeRect.height(),
            cropRect.right.toFloat() / wholeRect.width(),
            cropRect.bottom.toFloat() / wholeRect.height()
        )

        progressBar.visibility = View.VISIBLE
        btnEkle.isEnabled = false

        CoroutineScope(Dispatchers.Main).launch {
            var finalResultText = ""

            // 1. ADIM: PDFBox ile Metin Ayıklamayı Dene (Dijital PDF ise)
            if (pdfPath != null && pageIndex != -1) {
                finalResultText = withContext(Dispatchers.IO) {
                    PdfMetinCikarici.bolgeMetniCikar(java.io.File(pdfPath!!), pageIndex, normalizedRect)
                }
            }

            // 2. ADIM: PDFBox başarısızsa veya PDF değilse ML Kit OCR kullan
            if (finalResultText.isBlank()) {
                val croppedBitmap = cropImageView.getCroppedImage()
                if (croppedBitmap != null) {
                    finalResultText = withContext(Dispatchers.IO) {
                        try {
                            val processedBitmap = preprocessBitmap(croppedBitmap)
                            val image = InputImage.fromBitmap(processedBitmap, 0)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            
                            val visionText = Tasks.await(recognizer.process(image))
                            
                            // Eğer ham metin varsa ama satır gruplama başarısız olursa diye yedek al
                            var extracted = visionText.text

                            val allLines = visionText.textBlocks.flatMap { it.lines }
                            if (allLines.isNotEmpty()) {
                                val linesByY = mutableListOf<MutableList<com.google.mlkit.vision.text.Text.Line>>()
                                val sortedByTop = allLines.sortedBy { it.boundingBox?.top ?: 0 }
                                
                                for (line in sortedByTop) {
                                    val top = line.boundingBox?.top ?: 0
                                    var added = false
                                    for (group in linesByY) {
                                        val groupTop = group[0].boundingBox?.top ?: 0
                                        val groupHeight = group[0].boundingBox?.height() ?: 10
                                        if (Math.abs(top - groupTop) < groupHeight / 3) {
                                            group.add(line)
                                            added = true
                                            break
                                        }
                                    }
                                    if (!added) linesByY.add(mutableListOf(line))
                                }
                                
                                val sb = StringBuilder()
                                for (group in linesByY) {
                                    val sortedGroup = group.sortedBy { it.boundingBox?.left ?: 0 }
                                    for (line in sortedGroup) {
                                        sb.append(line.text).append(" ")
                                    }
                                    sb.append("\n")
                                }
                                extracted = sb.toString()
                            }
                            extracted
                        } catch (e: Exception) {
                            e.printStackTrace()
                            ""
                        }
                    }
                }
            }

            // SONUÇ: Metni temizle ve ekle
            val processedText = cleanAndFormatText(finalResultText)
            if (processedText.isNotBlank()) {
                soruSayisi++
                biriktirilenMetin.append(processedText).append("\n\n")
                Toast.makeText(this@SoruKirpiciActivity, "$soruSayisi. soru eklendi!", Toast.LENGTH_SHORT).show()
                
                cropImageView.isShowCropOverlay = false
                btnEkle.text = "SIRADAKİ SORU İÇİN KIRP"
                btnEkle.setBackgroundColor(android.graphics.Color.parseColor("#2196F3"))
            } else {
                Toast.makeText(this@SoruKirpiciActivity, "Metin okunamadı, tekrar deneyin", Toast.LENGTH_SHORT).show()
            }

            progressBar.visibility = View.GONE
            btnEkle.isEnabled = true
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // 1. Kenarlara orta karar boşluk ekleyelim (Padding 40)
        val padding = 40
        val config = Bitmap.Config.ARGB_8888
        val paddedBitmap = Bitmap.createBitmap(bitmap.width + padding * 2, bitmap.height + padding * 2, config)
        val canvasPadded = android.graphics.Canvas(paddedBitmap)
        canvasPadded.drawColor(android.graphics.Color.WHITE)
        canvasPadded.drawBitmap(bitmap, padding.toFloat(), padding.toFloat(), null)

        // 2. Resmi 2.5 kat büyütelim (Netlik için ideal oran)
        val matrix = android.graphics.Matrix()
        matrix.postScale(2.5f, 2.5f)
        val scaledBitmap = Bitmap.createBitmap(paddedBitmap, 0, 0, paddedBitmap.width, paddedBitmap.height, matrix, true)
        
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val output = Bitmap.createBitmap(width, height, config)
        
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint()
        
        // 3. Siyah Beyaz ve Yumuşak Kontrast (Harfleri koparmaz, y'leri v yapmaz)
        val cm = android.graphics.ColorMatrix()
        cm.setSaturation(0f)
        
        val contrast = 1.3f 
        val brightness = 0f 
        val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        
        cm.postConcat(contrastMatrix)
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        
        return output
    }

    private fun cleanAndFormatText(text: String): String {
        var result = text
            // 1. Tire temizliği (Yabancı karakterleri de kapsayacak şekilde)
            .replace(Regex("([a-zA-ZğüşıöçĞÜŞİÖÇ\\u017D])\\s*-\\s*\n?\\s*([a-zA-ZğüşıöçĞÜŞİÖÇ\\u017D])"), "$1$2")
            
            // 2. Karakter ve Kelime Hatalarını Düzelt
            .replace("ä", "a").replace("hä", "ha").replace("hå", "ha").replace("Žrdakilerden", "aşağıdakilerden")
            .replace("Acıl", "Acil").replace("acıl", "acil").replace("(alıştırnlma", "Çalıştırılma").replace("(Guvenlık", "Güvenlik").replace("(Güvenliği", "Güvenliği")
            .replace("uvgun", "uygun").replace("uv gun", "uygun").replace("Avdın", "Aydın").replace("av dın", "aydın").replace("Ay dın", "Aydın")
            .replace("veterlı", "yeterli").replace("sağlay acak", "sağlayacak").replace("sağlayacak", "sağlayacak")
            .replace("bır", "bir").replace("bur ", "bir ").replace(" vil ", " yıl ").replace("bur\nvil", "bir yıl")
            .replace("SIstemı", "Sistemi").replace("Sistemı", "Sistemi").replace("çalışabılıt", "çalışabilir")
            .replace("çıkŞ", "çıkış").replace("volları", "yolları").replace("kapılan", "kapıları").replace("Pos talarında", "Postalarında")
            .replace("Ciüvenlik", "Güvenlik").replace("kapılar I", "kapıları").replace("yollarn", "yolları")
            .replace("şekılde", "şekilde").replace("Işaretlenmelı", "işaretlenmeli").replace("Emzıren", "Emziren").replace("emzıren", "emziren")
            .replace("kalıCI", "kalıcı").replace("kesılmes I", "kesilmesi").replace("kesılmesı", "kesilmesi").replace("kendı", "kendi")
            .replace("lşveren", "İşveren").replace("I Şveren", "İşveren").replace("lşyer", "İşyer").replace("1ş\\erenrn", "işverenin").replace("işv eren", "işveren").replace("iş v erinin", "işverenin").replace("iș verinn", "işverenin")
            .replace("değerlendıumesı", "değerlendirmesi").replace("degerlendıumes", "değerlendirmesi").replace("değerlendırmesı", "değerlendirmesi").replace("Vönetmeliği", "Yönetmeliği").replace("Y önetmeliği", "Yönetmeliği")
            .replace("v apılmalıdır", "yapılmalıdır").replace("vapılmalıdır", "yapılmalıdır").replace("hukumler", "hükümler")
            .replace("maruZIvet", "maruziyet").replace("maruzZiyet", "maruziyet").replace("evlem", "eylem").replace("kaydıy la", "kaydıyla")
            .replace("iştme", "işitme").replace("Işıtme", "İşitme").replace("lşıtme", "İşitme").replace("sùreyle", "süreyle").replace("enerjı", "enerji")
            .replace("tespıt edılrse", "tespit edilirse").replace("taramadan geçırılmelidıu", "taramadan geçirilmelidir").replace("tespıt", "tespit")
            .replace("(alışanların", "Çalışanların").replace("nedenıv le", "nedeniyle").replace("nedenıy le", "nedeniyle").replace("nedeniy le", "nedeniyle")
            .replace("1zolas ona", "izolasyona").replace("hastav la", "hastayla").replace("amacıy la", "amacıyla")
            .replace("kay nağ", "kaynağ").replace("göze timi", "gözetimi").replace("sağık", "sağlık").replace("y asağı", "yasağı")
            .replace("tann", "tanı").replace("ayrn", "ayrı").replace("verden", "yerden").replace("de- ģer", "değer")
            .replace("raporuy la", "raporuyla").replace("surelerde", "sürelerde").replace("günduz", "gündüz").replace("znivle", "izniyle")
            .replace("çalışmalar ının", "çalışmalarının").replace("postalarrna", "postalarına").replace("duzenlenmesı", "düzenlenmesi")
            .replace("donemlernde", "dönemlerinde").replace("vardiv alarında", "vardiyalarında").replace("postaSında", "postasında")
            .replace("aşağıdakile rin", "aşağıdakilerin").replace("oluşturmav acak", "oluşturmayacak").replace("oluştumav acak", "oluşturmayacak").replace("oluştumay acak", "oluşturmayacak")
            .replace("varalanma", "yaralanma").replace("yar alanma", "yaralanma").replace("ka ip", "kayıp").replace("kay ıp", "kayıp")
            .replace("ukümluluklere", "yükümlülüklere").replace("y ukumluluklere", "yükümlülüklere").replace("vükümlulüklere", "yükümlülüklere").replace("vükümlulük", "yükümlülük").replace("ukúmluluklere", "yükümlülüklere")
            .replace("isk sevNe", "risk seviyesi").replace("polıtikasına", "politikasına").replace("polıtıkasına", "politikasına").replace("sevIve", "seviyesi")
            .replace("zaran", "zararı").replace("girişlerınde", "girişlerinde").replace("değışıklığnde", "değişikliğinde")
            .replace("avrılışında", "ayrılışında").replace("donuşlerınde", "dönüşlerinde").replace("etmelern", "etmeleri")
            .replace("avdınlatmayı", "aydınlatmayı").replace("eşvalar", "eşyalar")

            // 3. Kelime Sonu ve Ek Onarıcı
            .replace("yaptırılmalıdırlıdır", "yaptırılmalıdır")
            .replace("yaptırılma", "yaptırılmalıdır").replace("bulundurulmalıdr", "bulundurulmalıdır").replace("çalıştırtlamaz", "çalıştırılamaz").replace("çaliştırılamaz", "çalıştırılamaz").replace("bulunmalıdıu", "bulunmalıdır")
            .replace(Regex("alıdu\\b"), "alıdır").replace("alıdr\\b", "alıdır")
            .replace(Regex("elidıu\\b"), "elidir").replace("elidı\\b", "elidir")
            .replace(Regex("yapmalIdır\\b"), "yapmalıdır").replace("yapmalıdu\\b", "yapmalıdır")
            .replace("yanlıstr", "yanlıştır").replace("br ", "bir ")
            .replace("ve.", "ve")

            // 4. Satır başı Romen rakamı düzeltmeleri ve Birleşmiş Maddeleri Ayırma
            .replace(Regex("([a-zğüşıöç])\\s+(I+|IV|V|VI|VII|VIII|IX|X)\\b")) { "${it.groupValues[1]}. ${it.groupValues[2]}" }
            .replace(Regex("(?m)^\\s*([L1ilI|!İıV]{1,4})(\\.|\\,|\\-|\\s+|(?=[A-ZÇĞİÖŞÜ]))")) { match ->
                var raw = match.groupValues[1].uppercase()
                    .replace("L", "I").replace("1", "I").replace("|", "I")
                    .replace("!", "I").replace("İ", "I").replace("ı", "I").replace("i", "I")
                if (raw == "IIV") raw = "IV"
                if (raw == "IIII") raw = "III"
                if (raw.startsWith("I") || raw == "IV" || raw == "V") "$raw. " else "$raw. "
            }

            // 5. Şıkların yanındaki yapışık metinleri ayır
            .replace(Regex("([A-E]\\))([\\S])"), "$1 $2")
            
            // 6. Bozulmuş Şıkları ve Romen Dizilimlerini Kurtar
            .replace("IIV", "IV").replace("IIII", "III")
            .replace(Regex("\\|\\s*-\\s*Il\\s*-\\s*\\|\\|\\|\\s*-IV"), "I, II, III ve IV")
            .replace(Regex("\\|\\s*-\\s*\\|\\|\\s*–\\|-\\s*IV"), "I, II, III ve IV")
            .replace(Regex("IVI\\|\\|"), "IV, III ve II")
            .replace(Regex("II\\s*–\\|-\\s*IV\\s*–\\s*\\|\\|"), "II, I, IV ve III")
            .replace("Yalnız. IV", "Yalnız IV").replace("ve. I", "ve II").replace("ve. II", "ve II").replace("ve. III", "ve III").replace("ve. IV", "ve IV")
            .replace("L II", "I, II").replace("e. III", "ve III").replace("e. IV", "ve IV")
            .replace(Regex("L\\.\\s+Il\\s+ve\\s+II"), "I, II ve III")
            .replace(Regex("EL\\|\\|e"), "E) I, II ve IV")
            .replace(Regex("([I|V]+)\\s*-\\s*([I|V]+)"), "$1, $2") // Romen rakamları arasındaki tireleri virgüle çevir
            
            // 7. Metin içindeki Romen rakamlarını standartlaştır
            .replace(Regex("(?<=[I|V|X])\\.(?=\\s+[I|V|X])"), ",") 
            .replace(Regex("([I|V|X]{1,5})\\."), "$1")
            
            // 8. Kelimeye yapışmış Romen rakamlarını ayır ve I/l hatalarını düzelt
            .replace("lş Kanunu", "İş Kanunu").replace("sayılılş", "sayılı İş").replace("sralaması", "sıralaması")
            .replace(Regex("([a-zğüşıöç])([I|V|X]{1,3})\\b")) { match ->
                "${match.groupValues[1]} ${match.groupValues[2]}"
            }
            
            // 9. Genel Makyaj
            .replace("veva", "veya").replace("mūcadele", "mücadele")
            .replace("Yaln1z", "Yalnız").replace("degil", "değil")
            .replace("İ; ", "İş ").replace("Işe ", "İşe ").replace("İ, Sağlığ", "İş Sağlığ")
            .replace(Regex("\\s+(\\\\e|/e|\\e)\\s+"), " ve ")
            .replace("Yon", "Yön").replace("Yönetmelığı", "Yönetmeliği")
            .replace("oğrencıler", "öğrenciler").replace("geçıcı", "geçici")
            .replace("oncesı", "öncesi").replace("içın", "için")
            .replace("halınde", "halinde")
            
            // 10. Seçenekleri yeni satıra al
            .replace(Regex("([A-E]\\))"), "\n$1")
            
            // 11. Temizlik
            .replace(Regex(" +"), " ")
            .replace(Regex("\n+"), "\n")
            .trim()
            
        return result
    }
}
