package com.novikon.pace.ui.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.novikon.pace.R
import com.novikon.pace.models.BlockedUser

class BlockedUsersAdapter(
    private val onUnblockClick: (BlockedUser) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.BlockedUserViewHolder>() {

    private val items = mutableListOf<BlockedUser>()

    class BlockedUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.iv_blocked_profile)
        val tvName: TextView = itemView.findViewById(R.id.tv_blocked_name)
        val btnUnblock: MaterialButton = itemView.findViewById(R.id.btn_unblock)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return BlockedUserViewHolder(view)
    }

    override fun onBindViewHolder(holder: BlockedUserViewHolder, position: Int) {
        val user = items[position]
        holder.tvName.text = user.displayName.ifBlank {
            holder.itemView.context.getString(R.string.default_user)
        }

        // Por ahora placeholder (si luego añades carga real de foto, va aquí)
        holder.ivProfile.setImageResource(R.drawable.ic_person)

        holder.btnUnblock.setOnClickListener {
            onUnblockClick(user)
        }
    }

    override fun getItemCount(): Int = items.size

    fun submitList(newItems: List<BlockedUser>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}