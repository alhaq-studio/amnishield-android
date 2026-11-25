package com.alhaq.amnishield.ui.adapters

import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.alhaq.amnishield.R
import com.alhaq.amnishield.utils.NotificationInboxStore.InboxItem

class NotificationsAdapter(
    private var items: List<InboxItem>
) : RecyclerView.Adapter<NotificationsAdapter.ViewHolder>() {

    fun updateData(newItems: List<InboxItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val title: TextView = itemView.findViewById(R.id.notification_title)
        private val message: TextView = itemView.findViewById(R.id.notification_message)
        private val meta: TextView = itemView.findViewById(R.id.notification_meta)
        private val unreadDot: View = itemView.findViewById(R.id.unread_dot)

        fun bind(item: InboxItem) {
            title.text = item.title
            message.text = item.body

            val relative = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            )
            meta.text = "${categoryLabel(item.category)} · $relative"

            unreadDot.visibility = if (item.read) View.INVISIBLE else View.VISIBLE
            itemView.alpha = if (item.read) 0.78f else 1f
        }

        private fun categoryLabel(category: String): String = when (category.lowercase()) {
            "report" -> "Daily report"
            "smart" -> "Smart Coach"
            "achievement" -> "Achievement"
            "blocking" -> "Block alert"
            "streak" -> "Streak"
            else -> "AmniShield"
        }
    }
}
