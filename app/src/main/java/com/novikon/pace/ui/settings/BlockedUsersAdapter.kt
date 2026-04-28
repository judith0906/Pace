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

// Adapter de bloqueados: lista usuarios bloqueados y expone accion de desbloqueo.
class BlockedUsersAdapter(
    private val onUnblockClick: (BlockedUser) -> Unit
) : RecyclerView.Adapter<BlockedUsersAdapter.BlockedUserViewHolder>() {

    private val items = mutableListOf<BlockedUser>()

// ViewHolder de bloqueado: conserva referencias de cada fila de usuario bloqueado.
    class BlockedUserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivProfile: ImageView = itemView.findViewById(R.id.iv_blocked_profile)
        val tvName: TextView = itemView.findViewById(R.id.tv_blocked_name)
        val btnUnblock: MaterialButton = itemView.findViewById(R.id.btn_unblock)
    }

// onCreateViewHolder: infla el layout de cada item y crea su ViewHolder.
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BlockedUserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_blocked_user, parent, false)
        return BlockedUserViewHolder(view)
    }

// onBindViewHolder: vincula los datos del elemento actual con su vista.
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

// getItemCount: devuelve la cantidad total de elementos a renderizar.
    override fun getItemCount(): Int = items.size
    fun submitList(newItems: List<BlockedUser>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}