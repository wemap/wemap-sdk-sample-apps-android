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
        OnRecyclerViewClickListener { _, position ->
            val navId = when (position) {
                0 -> R.id.action_SamplesListFragment_to_LevelsFragment
                1 -> R.id.action_SamplesListFragment_to_PoisFragment
                2 -> R.id.action_SamplesListFragment_to_NavigationFragment
                3 -> R.id.action_SamplesListFragment_to_ComposeMapFragment
                4 -> R.id.action_SamplesListFragment_to_CustomCreditsFragment
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
                "Switches indoor levels, outlines a 100 m radius with a style layer, and restores map " +
                    "state across recreation"
            ),
            Pair(
                "Points of interest",
                "Filters POIs by tag, hides and shows them, selects them, and lists them by distance or " +
                    "travel time"
            ),
            Pair(
                "Navigation",
                "Navigates between two points long-pressed on the map, reporting progress along the way"
            ),
            Pair(
                "Map in Compose",
                "The map on its own in Compose — camera state that survives rotation, and the user's position " +
                    "read off the loaded view. The only sample that needs no other Wemap module"
            ),
            Pair(
                "Custom credits",
                "Overrides the credits bottom sheet, restyles the credits button, and sets up accessibility"
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
