package com.gecelimani.isgrota

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SoruAdapter(
    private val soruListesi: List<SoruAdayi>,
    private val onItemClick: (Int, SoruAdayi) -> Unit
) : RecyclerView.Adapter<SoruAdapter.ViewHolder>() {

    private val eklendiSet = mutableSetOf<Int>()

    fun setEklendi(position: Int) {
        eklendiSet.add(position)
        notifyItemChanged(position)
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNumara: TextView = view.findViewById(R.id.tvSoruNumarasi)
        val tvOzet: TextView = view.findViewById(R.id.tvSoruMetniOzet)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_soru_adayi, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val soru = soruListesi[position]
        holder.tvNumara.text = soru.numara
        holder.tvOzet.text = soru.soruMetni.ifEmpty { "Soru metni algılanamadı" }

        val eklendi = eklendiSet.contains(position)
        holder.itemView.alpha = if (eklendi) 0.5f else 1.0f
        holder.itemView.isEnabled = !eklendi

        holder.itemView.setOnClickListener { onItemClick(position, soru) }
    }

    override fun getItemCount() = soruListesi.size
}
