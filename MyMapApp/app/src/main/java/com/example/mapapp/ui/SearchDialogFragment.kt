package com.example.mapapp.ui

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mapapp.R
import com.example.mapapp.data.model.BusRoute

class SearchDialogFragment(
    private val allRoutes: List<BusRoute>,
    private val onRouteSelected: (BusRoute) -> Unit
) : DialogFragment() {

    private lateinit var searchAdapter: SearchAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        // Adjust resizing for keyboard
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_search, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val searchView = view.findViewById<SearchView>(R.id.dialog_search_view)
        val recyclerView = view.findViewById<RecyclerView>(R.id.dialog_recycler_view)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        // Setup Recycler
        searchAdapter = SearchAdapter { route ->
            onRouteSelected(route)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = searchAdapter

        // Setup Search
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                searchView.clearFocus()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterRoutes(newText)
                return true
            }
        })

        // Focus search view and show keyboard
        searchView.requestFocus()
        // Note: Keyboard showing usually requires a slight delay or WindowManager setup in Dialog

        btnCancel.setOnClickListener { dismiss() }
        
        // Initial list (empty or full? Let's show full list sorted)
        filterRoutes("")
    }
    
    override fun onStart() {
        super.onStart()
        // Make dialog wider
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun filterRoutes(query: String?) {
        val filtered = if (query.isNullOrBlank()) {
            allRoutes
        } else {
            allRoutes.filter { route ->
                route.name.contains(query, ignoreCase = true) || route.id.contains(query)
            }
        }.sortedWith(Comparator { a, b ->
            val idA = a.id.toIntOrNull()
            val idB = b.id.toIntOrNull()
            if (idA != null && idB != null) idA - idB else a.name.compareTo(b.name)
        })
        searchAdapter.submitList(filtered)
    }

    // Inner Adapter
    class SearchAdapter(private val onClick: (BusRoute) -> Unit) : RecyclerView.Adapter<SearchAdapter.ViewHolder>() {
        private var items: List<BusRoute> = emptyList()

        fun submitList(newItems: List<BusRoute>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_search_route, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val route = items[position]
            val textView = holder.itemView.findViewById<TextView>(android.R.id.text1)
            textView.text = route.name
            holder.itemView.setOnClickListener { onClick(route) }
        }

        override fun getItemCount() = items.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
