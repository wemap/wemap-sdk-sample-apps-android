package com.getwemap.example.map.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.multiline
import com.getwemap.example.map.Config
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.map.WemapMapSDK
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InitialFragment : Fragment() {

    private var request: Disposable? = null

    private var _binding: FragmentInitialBinding? = null
    private val binding get() = _binding!!

    private val spinner get() = binding.spinner
    private val mapIdTextView get() = binding.mapIdTextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapIdTextView.setText("${Constants.mapId}")

        // uncomment if you want to use dev environment
//        WemapCoreSDK.setEnvironment(Environment.Dev())
//        WemapCoreSDK.setItinerariesEnvironment(Environment.Dev())

        ArrayAdapter
            .createFromResource(requireContext(), R.array.location_sources, android.R.layout.simple_spinner_item)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }

        binding.buttonLoadMap.setOnClickListener {
            checkAvailability()
        }

        // if you need to retrieve all points of interest for some map in advance
//        val disp = ServiceFactory
//            .getPointOfInterestService()
//            .pointsOfInterestById(Constants.mapId)
//            .subscribe({
//                println("received pois - $it")
//            }, {
//                println("failed to receive pois with error - $it")
//            })
//        disposeBag.add(disp)
    }

    private fun checkAvailability() {
        when (spinner.selectedItemPosition) {
            0, 5 ->
                if (SimulatorLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            2 ->
                loadMap()
            1, 3 ->
                if (PolestarLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            4 ->
                if (GmsFusedLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            else ->
                throw RuntimeException("Unknown Location Source")
        }
    }

    private fun showUnavailableAlert() {
        AlertDialog.Builder(requireContext())
            .setMessage("Desired location source is unavailable on this device")
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .create()
            .show()
    }

    private fun loadMap() {
        val text = mapIdTextView.text.toString()
        val id = text.toIntOrNull()
        if (id == null) {
            println("Failed to get int ID from - '$text'")
            return
        }

        if (request?.isDisposed == false)
            return

        request = WemapMapSDK.instance
            .mapData(id, Constants.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                println("Received map data - $it")
                val bundle = Bundle()
                bundle.putInt("locationSourceId", spinner.selectedItemPosition)

                bundle.putString("mapData", Json.encodeToString(it))

                Config.applyGlobalOptions(requireContext())
                findNavController().navigate(R.id.action_InitialFragment_to_SamplesListFragment, bundle)
            }, {
                val str = "Failed to receive map data with error - ${it.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            })
    }

    override fun onDestroyView() {
        request?.dispose()
        super.onDestroyView()
        _binding = null
    }
}