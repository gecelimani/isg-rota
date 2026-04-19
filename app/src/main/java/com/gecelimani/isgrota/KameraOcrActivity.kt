package com.gecelimani.isgrota

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.util.Log
import android.content.Intent
import androidx.camera.core.ExperimentalGetImage

@OptIn(ExperimentalGetImage::class)
class KameraOcrActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var btnCapture: com.google.android.material.floatingactionbutton.FloatingActionButton
    private lateinit var progressBar: android.widget.ProgressBar
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kamera_ocr)

        previewView = findViewById(R.id.previewView)
        btnCapture = findViewById(R.id.btnCapture)
        progressBar = findViewById(R.id.progressBar)
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                REQUEST_CODE_PERMISSIONS
            )
        }

        btnCapture.setOnClickListener {
            takePhoto()
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        btnCapture.isEnabled = false
        progressBar.visibility = android.view.View.VISIBLE
        Toast.makeText(this, "Metin çözümleniyor...", Toast.LENGTH_SHORT).show()

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    processImageProxy(imageProxy)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("KameraOcr", "Fotoğraf çekilemedi", exception)
                    btnCapture.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                }
            }
        )
    }

    private fun allPermissionsGranted() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Kamera izni gerekli", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(previewView.display.rotation)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("KameraOcr", "Kamera başlatılamadı", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val bitmap = imageProxy.toBitmap()

            // Çerçevenin ekrandaki oranına göre resmi kırp
            val croppedBitmap = cropToFocusFrame(bitmap, rotationDegrees)
            
            // GÖRÜNTÜ ÖN İŞLEME (PDF Modülündeki altın oranlar)
            val processedBitmap = preprocessBitmap(croppedBitmap)
            
            val image = InputImage.fromBitmap(processedBitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { visionText ->
                    // Satırları dikey koordinatlarına göre grupla (Aynı satırdaki metinleri birleştir)
                    val allLines = visionText.textBlocks.flatMap { it.lines }
                    if (allLines.isEmpty()) {
                        Toast.makeText(this, "Metin bulunamadı", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

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

                    val processedText = cleanAndFormatText(sb.toString())

                    if (processedText.isNotBlank()) {
                        val intent = Intent(this, HizliMetinDuzenleActivity::class.java).apply {
                            putExtra("HAM_METIN", processedText)
                        }
                        startActivity(intent)
                        btnCapture.isEnabled = true
                        progressBar.visibility = android.view.View.GONE
                    } else {
                        Toast.makeText(this, "Metin bulunamadı, lütfen tekrar deneyin.", Toast.LENGTH_SHORT).show()
                        btnCapture.isEnabled = true
                        progressBar.visibility = android.view.View.GONE
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("KameraOcr", "OCR başarısız", e)
                    btnCapture.isEnabled = true
                    progressBar.visibility = android.view.View.GONE
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
            btnCapture.isEnabled = true
            progressBar.visibility = android.view.View.GONE
        }
    }

    private fun preprocessBitmap(bitmap: android.graphics.Bitmap): android.graphics.Bitmap {
        val scale = 2.5f
        val padding = 40
        val config = android.graphics.Bitmap.Config.ARGB_8888
        
        // 1. Ölçekleme
        val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(
            bitmap, 
            (bitmap.width * scale).toInt(), 
            (bitmap.height * scale).toInt(), 
            true
        )
        
        // 2. Padding ekleme
        val width = scaledBitmap.width
        val height = scaledBitmap.height
        val output = android.graphics.Bitmap.createBitmap(width + padding * 2, height + padding * 2, config)
        
        val canvas = android.graphics.Canvas(output)
        canvas.drawColor(android.graphics.Color.WHITE)
        
        val paint = android.graphics.Paint()
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
        canvas.drawBitmap(scaledBitmap, padding.toFloat(), padding.toFloat(), paint)
        
        return output
    }

    private fun cleanAndFormatText(text: String): String {
        var result = text
            // 1. Tire temizliği
            .replace(Regex("([a-zA-ZğüşıöçĞÜŞİÖÇ\\u017D])\\s*-\\s*\n?\\s*([a-zA-ZğüşıöçĞÜŞİÖÇ\\u017D])"), "$1$2")
            
            // 2. Karakter ve Kelime Hatalarını Düzelt (Versiyon 9.0 Sözlüğü)
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
            
            // 6. Bozulmuş Şıkları Kurtar (En Kapsamlı Liste)
            .replace("IIV", "IV").replace("IIII", "III")
            .replace("Yalnız. IV", "Yalnız IV").replace("ve. I", "ve II").replace("ve. II", "ve II").replace("ve. III", "ve III").replace("ve. IV", "ve IV")
            .replace("L II", "I, II").replace("e. III", "ve III").replace("e. IV", "ve IV")
            .replace(Regex("L\\.\\s+Il\\s+ve\\s+II"), "I, II ve III")
            .replace(Regex("EL\\|\\|e"), "E) I, II ve IV")
            
            // 7. Metin içindeki Romen rakamlarını standartlaştır
            .replace(Regex("(?<=[I])\\.(?=\\s+[I])"), ",") 
            .replace(Regex("([I]{1,3})\\."), "$1")
            
            // 8. Kelimeye yapışmış Romen rakamlarını ayır
            .replace(Regex("([a-zğüşıöç])([I]{1,3})\\b")) { match ->
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

    private fun cropToFocusFrame(bitmap: android.graphics.Bitmap, rotation: Int): android.graphics.Bitmap {
        val focusFrame = findViewById<android.view.View>(R.id.focusFrame)
        
        // Ekran ve bitmap boyutları arasındaki oranı bul
        val viewWidth = previewView.width.toFloat()
        val viewHeight = previewView.height.toFloat()
        
        // Eğer resim yan duruyorsa boyutları takas et (çoğu cihazda kamera 90 derece döndürülmüştür)
        val bitmapWidth = if (rotation == 90 || rotation == 270) bitmap.height.toFloat() else bitmap.width.toFloat()
        val bitmapHeight = if (rotation == 90 || rotation == 270) bitmap.width.toFloat() else bitmap.height.toFloat()

        val scaleX = bitmapWidth / viewWidth
        val scaleY = bitmapHeight / viewHeight

        // focusFrame'in koordinatlarını bitmap üzerine izdüşür
        val left = (focusFrame.left * scaleX).toInt()
        val top = (focusFrame.top * scaleY).toInt()
        val width = (focusFrame.width * scaleX).toInt()
        val height = (focusFrame.height * scaleY).toInt()

        // Sınır kontrolleri
        val finalLeft = left.coerceIn(0, bitmap.width - 1)
        val finalTop = top.coerceIn(0, bitmap.height - 1)
        val finalWidth = width.coerceAtMost(bitmap.width - finalLeft)
        val finalHeight = height.coerceAtMost(bitmap.height - finalTop)

        // Bitmap'i döndürüp sonra kırpıyoruz (ML Kit daha iyi sonuç alır)
        val matrix = android.graphics.Matrix()
        matrix.postRotate(rotation.toFloat())
        val rotatedBitmap = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        // Döndürülmüş bitmap üzerinde kırpma (koordinatlar değiştiği için tekrar hesapla)
        val rotatedScaleX = rotatedBitmap.width.toFloat() / viewWidth
        val rotatedScaleY = rotatedBitmap.height.toFloat() / viewHeight
        
        val rLeft = (focusFrame.left * rotatedScaleX).toInt()
        val rTop = (focusFrame.top * rotatedScaleY).toInt()
        val rWidth = (focusFrame.width * rotatedScaleX).toInt()
        val rHeight = (focusFrame.height * rotatedScaleY).toInt()

        return android.graphics.Bitmap.createBitmap(
            rotatedBitmap,
            rLeft.coerceIn(0, rotatedBitmap.width - 1),
            rTop.coerceIn(0, rotatedBitmap.height - 1),
            rWidth.coerceAtMost(rotatedBitmap.width - rLeft),
            rHeight.coerceAtMost(rotatedBitmap.height - rTop)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}