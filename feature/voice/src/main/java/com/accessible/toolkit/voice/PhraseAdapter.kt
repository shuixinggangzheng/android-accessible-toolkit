package com.accessible.toolkit.voice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PhraseAdapter(
    private val phrases: List<String>,
    private val onPhraseClick: (String) -> Unit
) : RecyclerView.Adapter<PhraseAdapter.PhraseViewHolder>() {

    class PhraseViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPhrase: TextView = itemView.findViewById(R.id.tv_phrase)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhraseViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_phrase, parent, false)
        return PhraseViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhraseViewHolder, position: Int) {
        val phrase = phrases[position]
        holder.tvPhrase.text = phrase
        holder.itemView.setOnClickListener { onPhraseClick(phrase) }
    }

    override fun getItemCount(): Int = phrases.size
}