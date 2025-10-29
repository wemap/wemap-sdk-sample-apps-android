package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getwemap.example.positioning.ar.R
import com.getwemap.example.positioning.ar.databinding.FragmentItemBinding
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.serialization.json.Json

class SamplesListFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false) as RecyclerView

        with(view) {
            layoutManager = LinearLayoutManager(context)
            adapter = SamplesRecyclerViewAdapter(listener)
        }
        return view
    }

    private val listener by lazy {
        OnRecyclerViewClickListener { view, position ->
            if (!isLocationSourceAvailable(position, requireArguments()))
                return@OnRecyclerViewClickListener

            val navId = when (position) {
                0 -> R.id.action_SamplesListFragment_to_SimulatorLSFragment
                1 -> R.id.action_SamplesListFragment_to_VPSLSFragment
                2 -> R.id.action_SamplesListFragment_to_AndroidFusedAdaptiveLSFragment
                3 -> R.id.action_SamplesListFragment_to_FusedGMSLSFragment
                4 -> R.id.action_SamplesListFragment_to_GPSLSFragment
                else -> throw Exception("Unsupported transition")
            }
            requireArguments().putInt("locationSourceId", position)
            findNavController().navigate(navId, arguments)
        }
    }

    private fun isLocationSourceAvailable(position: Int, bundle: Bundle): Boolean {
        return when (position) {
            1 -> {
                if (WemapVPSARCoreLocationSource.checkAvailability(requireContext()).isUnsupported) {
                    val text = "VPS location source is not supported on this device"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                val mapDataString = bundle.getString("mapData")!!
                val mapData: MapData = Json.decodeFromString(mapDataString)
                if (mapData.extras?.vpsEndpoint == null) {
                    val text = "This map(${mapData.id}) is not compatible with VPS Location Source"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                return true
            }
            3 -> {
                if (!GmsFusedLocationSource.isAvailable(requireContext())) {
                    val text = "Fused GMS location source is not supported on this device"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                return true
            }
            // all other LocationSources are always available
            else -> true
        }
    }
}

class SamplesRecyclerViewAdapter(
    private val listener: OnRecyclerViewClickListener
) : RecyclerView.Adapter<SamplesRecyclerViewAdapter.ViewHolder>() {

    private val items by lazy {
        listOf(
            Pair(
                "Simulator Location Source",
                "Shows how to simulate user movements using simulator location source in AR"
            ),
            Pair(
                "VPS Location Source",
                "Shows how to track user movements using VPS location source in AR"
            ),
            Pair(
                "Android Fused Adaptive Location Source",
                "Shows how to track user movements using Android Fused Adaptive location source in AR"
            ),
            Pair(
                "Fused GMS Location Source",
                "Shows how to track user movements using GMS Fused location source in AR"
            ),
            Pair(
                "GPS Location Source",
                "Shows how to track user movements using GPS location source in AR"
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