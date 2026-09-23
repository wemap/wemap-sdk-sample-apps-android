package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getwemap.example.common.SessionViewModel
import com.getwemap.example.positioning.ar.LocationSourceType
import com.getwemap.example.positioning.ar.R
import com.getwemap.example.positioning.ar.databinding.FragmentItemBinding
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource
import com.google.android.material.snackbar.Snackbar

class SamplesListFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false) as RecyclerView

        with(view) {
            layoutManager = LinearLayoutManager(context)
            adapter = SamplesRecyclerViewAdapter(listener)
        }
        return view
    }

    private val listener by lazy {
        OnRecyclerViewClickListener { _, position ->
            // Every row but the last IS its location source. The Compose sample is a rendering variant rather
            // than a source, so it picks the simulator: it needs no fix and no location permission, which
            // keeps the smallest AR sample runnable anywhere.
            val isComposeSample = position == LocationSourceType.entries.size
            val source =
                if (isComposeSample) LocationSourceType.SIMULATOR else LocationSourceType.entries[position]

            if (!isAvailable(source))
                return@OnRecyclerViewClickListener

            val navId = if (isComposeSample) {
                R.id.action_SamplesListFragment_to_ComposeARFragment
            } else {
                when (source) {
                    LocationSourceType.SIMULATOR -> R.id.action_SamplesListFragment_to_SimulatorLSFragment
                    LocationSourceType.VPS -> R.id.action_SamplesListFragment_to_VpsLSFragment
                    LocationSourceType.ANDROID_FUSED_ADAPTIVE ->
                        R.id.action_SamplesListFragment_to_AndroidFusedAdaptiveLSFragment
                    LocationSourceType.FUSED_GMS -> R.id.action_SamplesListFragment_to_FusedGMSLSFragment
                    LocationSourceType.GPS -> R.id.action_SamplesListFragment_to_GpsLSFragment
                }
            }
            findNavController().navigate(navId, source.putInto(Bundle()))
        }
    }

    private fun isAvailable(source: LocationSourceType): Boolean {
        return when (source) {
            LocationSourceType.VPS -> {
                if (VpsARCoreLocationSource.checkAvailability(requireContext()).isUnsupported) {
                    val text = "VPS location source is not supported on this device"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                val session = sessionViewModel.session!!
                if (!session.isVpsEnabled) {
                    val text = "This map(${session.mapId}) is not compatible with VPS Location Source"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                true
            }
            LocationSourceType.FUSED_GMS -> {
                if (!GmsFusedLocationSource.isAvailable(requireContext())) {
                    val text = "Fused GMS location source is not supported on this device"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
                    return false
                }
                true
            }
            // else -> all other LocationSources are always available
            else -> true
        }
    }
}

class SamplesRecyclerViewAdapter(
    private val listener: OnRecyclerViewClickListener
) : RecyclerView.Adapter<SamplesRecyclerViewAdapter.ViewHolder>() {

    // The source rows come from the enum, so a source added there appears here with no second edit. The
    // Compose row is appended because it is not a source — see the listener above.
    private val items by lazy {
        LocationSourceType.entries.map { SamplesItem(it.title, it.details) } +
            SamplesItem(
                "AR in Compose",
                "The AR scene on its own in Compose, driven by the simulator — the smallest screen that uses " +
                    "the Compose AR SDK"
            )
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

/**
 * The one row whose position is not a location source id — see the listener.
 */

fun interface OnRecyclerViewClickListener {
    fun onClick(view: View, position: Int)
}