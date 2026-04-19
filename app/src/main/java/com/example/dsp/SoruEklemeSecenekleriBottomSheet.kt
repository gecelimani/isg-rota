package com.example.dsp

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SoruEklemeSecenekleriBottomSheet : BottomSheetDialogFragment() {

    private val selectImageLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        uri?.let {
            val intent = Intent(requireContext(), SoruKirpiciActivity::class.java)
            intent.putExtra("IMAGE_URI", it)
            startActivity(intent)
            dismiss()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_soru_ekle_secenekleri, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val optionManual = view.findViewById<LinearLayout>(R.id.optionManual)
        val optionPaste = view.findViewById<LinearLayout>(R.id.optionPaste)
        val optionImage = view.findViewById<LinearLayout>(R.id.optionImage)
        val optionPdf = view.findViewById<LinearLayout>(R.id.optionPdf)

        optionManual.setOnClickListener {
            startActivity(Intent(requireContext(), SoruEkleActivity::class.java))
            dismiss()
        }

        optionPaste.setOnClickListener {
            startActivity(Intent(requireContext(), HizliAktarActivity::class.java))
            dismiss()
        }

        optionImage.setOnClickListener {
            selectImageLauncher.launch("image/*")
        }

        optionPdf.setOnClickListener {
            startActivity(Intent(requireContext(), PdfSeciciActivity::class.java))
            dismiss()
        }
    }
}