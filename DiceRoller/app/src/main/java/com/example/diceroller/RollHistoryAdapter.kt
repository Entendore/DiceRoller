package com.example.diceroller

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class RollHistoryAdapter :
    ListAdapter<HistoryEntry, RollHistoryAdapter.RollViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<HistoryEntry>() {
            override fun areItemsTheSame(a: HistoryEntry, b: HistoryEntry) =
                a === b || (a.kind == b.kind && a.values == b.values &&
                            a.modifier == b.modifier && a.total == b.total)
            override fun areContentsTheSame(a: HistoryEntry, b: HistoryEntry) = a == b
        }
    }

    class RollViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.historyText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = RollViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.item_roll_history, parent, false)
    )

    override fun onBindViewHolder(holder: RollViewHolder, position: Int) {
        holder.text.text = RollText.history(holder.text.context, getItem(position))
    }
}
