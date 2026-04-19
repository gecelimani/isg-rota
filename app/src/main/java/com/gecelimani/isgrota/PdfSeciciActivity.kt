package com.gecelimani.isgrota

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import com.google.mlkit.vision.common.InputImage

class PdfSeciciActivity : AppCompatActivity() {

    private lateinit var rvPdfPages: RecyclerView
    private lateinit var emptyState: View
    private lateinit var progressBar: ProgressBar
    private val pages = mutableListOf<Bitmap>()
    private val selectedPages = mutableSetOf<Int>()
    private val biriktirilenMetin = StringBuilder()
    private var soruSayisi = 0
    private var pdfRenderer: PdfRenderer? = null
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var currentPdfFile: File? = null
    private var lastCroppedUri: Uri? = null
    private lateinit var btnProcessSelected: View

    private val selectPdfLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { renderPdf(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pdf_secici)

        rvPdfPages = findViewById(R.id.rvPdfPages)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        btnProcessSelected = findViewById(R.id.btnProcessSelected)

        findViewById<Button>(R.id.btnSelectPdf).setOnClickListener {
            selectPdfLauncher.launch("application/pdf")
        }

        btnProcessSelected.setOnClickListener {
            processMultiplePages()
        }

        rvPdfPages.layoutManager = LinearLayoutManager(this)
        rvPdfPages.adapter = PdfPageAdapter(pages) { position ->
            toggleSelection(position)
        }
    }

    private val soruKirpiciLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val metin = result.data?.getStringExtra("BIRIKTIRILEN_METIN")
            if (!metin.isNullOrBlank()) {
                biriktirilenMetin.append(metin)
                // Soru sayısını metinden hesapla (--- SORU X --- satırlarını say)
                soruSayisi = biriktirilenMetin.toString().split("--- SORU").size - 1
                
                btnProcessSelected.visibility = View.VISIBLE
                if (btnProcessSelected is Button) {
                    (btnProcessSelected as Button).text = "BİRİKTİRİLEN $soruSayisi SORUYU DÜZENLE"
                }
            }
        }
    }

    private fun launchSoruKirpici(uri: Uri, pageIndex: Int) {
        val intent = Intent(this, SoruKirpiciActivity::class.java)
        intent.putExtra("IMAGE_URI", uri)
        intent.putExtra("PDF_PATH", currentPdfFile?.absolutePath)
        intent.putExtra("PAGE_INDEX", pageIndex)
        soruKirpiciLauncher.launch(intent)
    }

    private fun cleanAndFormatText(text: String): String {
        return text
            .replace(Regex("(?m)^[0-9]+\\.\\s*"), "\n$0")
            .replace(Regex("\\s+([A-E]\\))"), "\n$1")
            .replace(Regex("\n+"), "\n")
            .trim()
    }

    private fun toggleSelection(position: Int) {
        val bitmap = pages[position]
        val cacheFile = File(cacheDir, "temp_crop_page_${position}.jpg")
        FileOutputStream(cacheFile).use { 
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        val uri = Uri.fromFile(cacheFile)
        launchSoruKirpici(uri, position)
    }

    private fun processMultiplePages() {
        if (biriktirilenMetin.isNotEmpty()) {
            val intent = Intent(this, HizliMetinDuzenleActivity::class.java).apply {
                putExtra("HAM_METIN", biriktirilenMetin.toString().trim())
            }
            startActivity(intent)
            return
        }

        if (pdfRenderer == null) return
        val selectedIndices = selectedPages.sorted().toList()
        if (selectedIndices.isEmpty()) return

        progressBar.visibility = View.VISIBLE
        
        CoroutineScope(Dispatchers.Main).launch {
            val results = mutableListOf<String>()
            val pdfFile = currentPdfFile ?: return@launch

            for (pageIndex in selectedIndices) {
                try {
                    // Sadece Native Metin Çıkarıcıyı (PDFBox) kullan
                    val extractedText = withContext(Dispatchers.IO) {
                        PdfMetinCikarici.metniCikar(pdfFile, pageIndex)
                    }

                    if (extractedText.trim().isNotEmpty()) {
                        results.add(extractedText)
                    } else {
                        results.add("[Sayfa ${pageIndex + 1}: Metin katmanı bulunamadı (Taranmış belge/Resim olabilir)]")
                    }
                } catch (e: Exception) {
                    results.add("[Sayfa ${pageIndex + 1} işlenirken hata: ${e.message}]")
                }
            }
            
            progressBar.visibility = View.GONE
            val allText = results.joinToString("\n\n--- SAYFA AYRACI ---\n\n")
            
            val intent = Intent(this@PdfSeciciActivity, HizliMetinDuzenleActivity::class.java)
            intent.putExtra("HAM_METIN", allText)
            startActivity(intent)
        }
    }

    private fun renderPdf(uri: Uri) {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        pages.clear()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Önceki renderer ve descriptor'ları temizle
                pdfRenderer?.close()
                fileDescriptor?.close()

                val inputStream = contentResolver.openInputStream(uri) ?: throw Exception("Dosya açılamadı")
                val tempFile = File(cacheDir, "temp_pdf_${System.currentTimeMillis()}.pdf")
                val outputStream = FileOutputStream(tempFile)
                
                inputStream.use { input ->
                    outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                
                currentPdfFile = tempFile
                fileDescriptor = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                pdfRenderer = PdfRenderer(fileDescriptor!!)

                val pageCount = pdfRenderer!!.pageCount
                for (i in 0 until pageCount) {
                    val page = pdfRenderer!!.openPage(i)
                    // Liste önizlemesi için düşük çözünürlük
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    pages.add(bitmap)
                    page.close()
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    rvPdfPages.adapter?.notifyDataSetChanged()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@PdfSeciciActivity, "Hata: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pdfRenderer?.close()
        fileDescriptor?.close()
    }

    inner class PdfPageAdapter(private val items: List<Bitmap>, private val onSelect: (Int) -> Unit) :
        RecyclerView.Adapter<PdfPageAdapter.PdfViewHolder>() {

        inner class PdfViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivPreview: ImageView = view.findViewById(R.id.ivPagePreview)
            val tvNumber: TextView = view.findViewById(R.id.tvPageNumber)
            val btnProcess: Button = view.findViewById(R.id.btnProcessPage)
            val cardView: com.google.android.material.card.MaterialCardView = view as com.google.android.material.card.MaterialCardView
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
            return PdfViewHolder(view)
        }

        override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
            holder.ivPreview.setImageBitmap(items[position])
            holder.tvNumber.text = "Sayfa ${position + 1}"
            
            val isSelected = selectedPages.contains(position)
            holder.cardView.strokeWidth = if (isSelected) 6 else 1
            holder.cardView.strokeColor = if (isSelected) 
                android.graphics.Color.parseColor("#FFD700")
            else 
                android.graphics.Color.parseColor("#1A888888")

            holder.btnProcess.text = "KIRP"
            holder.itemView.setOnClickListener { onSelect(position) }
            holder.btnProcess.setOnClickListener { onSelect(position) }
        }

        override fun getItemCount() = items.size
    }
}