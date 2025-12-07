package com.example.mapapp.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.mapapp.R
import com.example.mapapp.data.model.StopScheduleItem

class ScheduleAdapter : ListAdapter<StopScheduleItem, ScheduleAdapter.ScheduleViewHolder>(ScheduleDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScheduleViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false)
        return ScheduleViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScheduleViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ScheduleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val routeIdView: TextView = itemView.findViewById(R.id.schedule_route_id)
        private val destinationView: TextView = itemView.findViewById(R.id.schedule_destination)
        private val timeView: TextView = itemView.findViewById(R.id.schedule_time)

        fun bind(item: StopScheduleItem) {
            routeIdView.text = item.routeId
            destinationView.text = item.headsign
            timeView.text = formatGtfsTime(item.time)
        }

        private fun formatGtfsTime(gtfsTime: String): String {
            // Format is H:mm:ss or HH:mm:ss
            val parts = gtfsTime.split(":")
            if (parts.size >= 2) {
                var hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1]
                
                if (hours >= 24) {
                    hours -= 24
                }
                
                // Pad hours with 0 if needed
                val hoursStr = if (hours < 10) "0$hours" else "$hours"
                return "$hoursStr:$minutes"
            }
            return gtfsTime
        }
    }

    class ScheduleDiffCallback : DiffUtil.ItemCallback<StopScheduleItem>() {
        override fun areItemsTheSame(oldItem: StopScheduleItem, newItem: StopScheduleItem): Boolean {
            // Assuming routeId + time + serviceId is unique enough for a single stop list
            return oldItem.routeId == newItem.routeId && oldItem.time == newItem.time
        }

        override fun areContentsTheSame(oldItem: StopScheduleItem, newItem: StopScheduleItem): Boolean {
            return oldItem == newItem
        }
    }
}
