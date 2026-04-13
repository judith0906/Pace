package com.novikon.pace.ui.circles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.novikon.pace.R
import com.novikon.pace.models.CircleMember

class GroupMembersAdapter(
    private val currentUserId: String?,
    private val members: List<CircleMember>,
    private val onBlockClicked: (CircleMember) -> Unit
) : RecyclerView.Adapter<GroupMembersAdapter.MemberViewHolder>() {

    class MemberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvName: TextView = itemView.findViewById(R.id.tv_member_name)
        val btnMore: ImageButton = itemView.findViewById(R.id.btn_member_more)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MemberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group_member, parent, false)
        return MemberViewHolder(view)
    }

    override fun onBindViewHolder(holder: MemberViewHolder, position: Int) {
        val member = members[position]
        holder.tvName.text = member.displayName

        // No mostrar menú de acciones para el propio usuario
        if (member.userId == currentUserId) {
            holder.btnMore.visibility = View.GONE
            return
        }

        holder.btnMore.visibility = View.VISIBLE
        holder.btnMore.setOnClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            popup.menuInflater.inflate(R.menu.menu_member_actions, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_block_member -> {
                        onBlockClicked(member)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
    }

    override fun getItemCount(): Int = members.size
}