package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentItemBinding

class SamplesListFragment : Fragment() {

    private val listener by lazy {
        OnRecyclerViewClickListener { view, position ->
            val navId = when (position) {
                0 -> R.id.action_SamplesListFragment_to_LevelsFragment
                1 -> R.id.action_SamplesListFragment_to_POIsFragment
                2 -> R.id.action_SamplesListFragment_to_NavigationFragment
                else -> throw Exception("Unsupported transition")
            }
            findNavController().navigate(navId, requireArguments())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false) as RecyclerView

        with(view) {
            layoutManager = LinearLayoutManager(context)
            adapter = SamplesRecyclerViewAdapter(listener)
        }
        return view
    }
}

class SamplesRecyclerViewAdapter(
    private val listener: OnRecyclerViewClickListener
) : RecyclerView.Adapter<SamplesRecyclerViewAdapter.ViewHolder>() {

    private val items by lazy {
        listOf(
            Pair(
                "Levels",
                "Shows how to switch between levels and perform POI selection on different levels"
            ),
            Pair(
                "Points of interest",
                "Shows how to hide/show and select/unselect POIs"
            ),
            Pair(
                "Navigation",
                "Shows how to start/stop navigation to user-created annotations"
            )
        ).map {
            SamplesItem(it.first, it.second)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FragmentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener { listener.onClick(it, holder.id) }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.contentView.text = item.content
        holder.detailedView.text = item.details
        holder.id = position
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(binding: FragmentItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val contentView = binding.content
        val detailedView = binding.detailed
        var id = -1
    }

    data class SamplesItem(val content: String, val details: String)
}

fun interface OnRecyclerViewClickListener {
    fun onClick(view: View, position: Int)
}