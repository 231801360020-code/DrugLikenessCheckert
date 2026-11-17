package com.example.druglikenesschecker.ui.history

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.druglikenesschecker.R
import com.example.druglikenesschecker.data.db.DrugEntity
import com.google.android.material.chip.Chip

class SavedDrugsAdapter(
    private val onDeleteClick: (DrugEntity) -> Unit,
    private val onCompareCheckToggle: (DrugEntity) -> Unit,
    private val onItemClick: (DrugEntity) -> Unit
) : ListAdapter<DrugEntity, SavedDrugsAdapter.DrugViewHolder>(DiffCallback) {

    private var selectedCompareIds: Set<Long> = emptySet()

    fun updateSelectedCompareIds(ids: Set<Long>) {
        selectedCompareIds = ids
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrugViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_drug_saved, parent, false)
        return DrugViewHolder(view)
    }

    override fun onBindViewHolder(holder: DrugViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DrugViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvDrugName)
        private val tvFormula: TextView = itemView.findViewById(R.id.tvDrugFormula)
        private val tvProps: TextView = itemView.findViewById(R.id.tvDrugProps)
        private val ivThumb: ImageView = itemView.findViewById(R.id.ivDrugThumb)
        private val chipLipinski: Chip = itemView.findViewById(R.id.chipLipinskiStatus)
        private val cbCompare: CheckBox = itemView.findViewById(R.id.cbSelectCompare)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(drug: DrugEntity) {
            tvName.text = drug.name
            tvFormula.text = "Formula: ${drug.formula}"
            tvProps.text = "MW: ${"%.1f".format(drug.mw)} | LogP: ${"%.2f".format(drug.logP)} | TPSA: ${"%.1f".format(drug.tpsa)}"

            ivThumb.load(drug.imageUrl) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.ic_dialog_alert)
            }

            if (drug.lipinskiPasses) {
                chipLipinski.text = "Lipinski ✓"
                chipLipinski.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                chipLipinski.setTextColor(Color.parseColor("#2E7D32"))
            } else {
                chipLipinski.text = "Lipinski ✗"
                chipLipinski.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEBEE"))
                chipLipinski.setTextColor(Color.parseColor("#C62828"))
            }

            cbCompare.setOnCheckedChangeListener(null)
            cbCompare.isChecked = selectedCompareIds.contains(drug.id)
            cbCompare.setOnCheckedChangeListener { _, _ ->
                onCompareCheckToggle(drug)
            }

            btnDelete.setOnClickListener {
                onDeleteClick(drug)
            }

            itemView.setOnClickListener {
                onItemClick(drug)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<DrugEntity>() {
        override fun areItemsTheSame(oldItem: DrugEntity, newItem: DrugEntity): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DrugEntity, newItem: DrugEntity): Boolean =
            oldItem == newItem
    }
}
