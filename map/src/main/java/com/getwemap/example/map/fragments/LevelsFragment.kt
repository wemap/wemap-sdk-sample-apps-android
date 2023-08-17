package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.map.ConsumerData
import com.getwemap.example.map.databinding.FragmentLevelsBinding
import com.getwemap.sdk.core.model.ServiceFactory
import com.google.android.material.snackbar.Snackbar
import com.mapbox.mapboxsdk.Mapbox

class LevelsFragment : MapFragment() {
    override val mapView get() = binding.mapView
    override val levelToggle get() = binding.levelToggle

    private val buttonFirstPOI get() = binding.firstPOI
    private val buttonSecondPOI get() = binding.secondPOI
    private val pointOfInterestManager get() = mapView.pointOfInterestManager

    private lateinit var binding: FragmentLevelsBinding

    private val consumerData by lazy {
        val json = requireContext().assets.open("consumer_data.json").bufferedReader().use { it.readText() }
        val consumerData: List<ConsumerData> = ServiceFactory.jsonConverter.decodeFromString(json)
        consumerData
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonFirstPOI.setOnClickListener {
            firstPOIClicked()
        }

        buttonSecondPOI.setOnClickListener {
            secondPOIClicked()
        }
    }

    private fun firstPOIClicked() {
        selectPOI(consumerData.first())
    }

    private fun secondPOIClicked() {
        selectPOI(consumerData[1])
    }

    private fun selectPOI(consumerData: ConsumerData) {
        val desiredPOI = pointOfInterestManager.getPOIs().firstOrNull { it.customerID == consumerData.externalID }
        if (desiredPOI == null) {
            println("POI with id - ${consumerData.externalID} has not been found in POIs")
            return
        }

        pointOfInterestManager.selectPOI(desiredPOI)

        showSnackbar(desiredPOI.id) {
            pointOfInterestManager.unselectPOI(desiredPOI)
        }
    }

    private fun showSnackbar(id: Int, onDismissed: (() -> Unit)? = null) {
        Snackbar
            .make(mapView, "POI selected with id $id", Snackbar.LENGTH_LONG)
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    onDismissed?.invoke()
                    super.onDismissed(transientBottomBar, event)
                }
            })
            .show()
    }
}