package com.getwemap.example.map.positioning.fragments

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
import com.getwemap.example.map.positioning.Config
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.WemapMapSDK
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GPSLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
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
    }

    private fun checkAvailability() {
        when (spinner.selectedItemPosition) {
            0 -> // VPS
                WemapVPSARCoreLocationSource.checkAvailabilityAsync(requireContext()) { availability ->
                    when (availability) {
                        SUPPORTED_INSTALLED -> loadMap()
                        SUPPORTED_NOT_INSTALLED -> installARCore()
                        else -> showUnavailableAlert()
                    }
                }
            1, 2 -> loadMap() // Simulator, System Default
            3 -> if (GPSLocationSource.isAvailable(requireContext())) loadMap() else showUnavailableAlert()
            4 -> if (GmsFusedLocationSource.isAvailable(requireContext())) loadMap() else showUnavailableAlert()
            5, 6 -> if (PolestarLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            else ->  throw RuntimeException("Unknown Location Source")
        }
    }

    // requestInstall(Activity, true) will triggers installation of
    // Google Play Services for AR if necessary.
    private var userRequestedInstall = true

    private fun installARCore() {
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
                INSTALLED -> loadMap()
                INSTALL_REQUESTED -> userRequestedInstall = false
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            showUnavailableAlert("Failed to install ARCore because user declined installation")
        } catch (_: UnavailableDeviceNotCompatibleException) {
            showUnavailableAlert()
        } catch (error: Exception) {
            showUnavailableAlert("Failed to install ARCore. Unknown error - $error")
        }
    }

    private fun showUnavailableAlert(message: String = "Desired location source is unavailable on this device") {
        AlertDialog.Builder(requireContext())
            .setMessage(message)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun loadMap() {
        val text = mapIdTextView.text.toString()
        val id = text.toIntOrNull()
            ?: return println("Failed to get int ID from - '$text'")

        if (request?.isDisposed == false)
            return

        binding.buttonLoadMap.isEnabled = false

        request = WemapMapSDK.instance
            .mapData(id, Constants.token)
            .doAfterTerminate {
                binding.buttonLoadMap.isEnabled = true
            }
            .subscribe({
                showMap(it)
            }, {
                val str = "Failed to receive map data with error - ${it.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            })
    }

    private fun showMap(mapData: MapData) {
        Config.applyGlobalOptions(requireContext())

        if (spinner.selectedItemPosition == 0 && mapData.extras?.vpsEndpoint == null) { // VPS
            val text = "This map(${mapData.id}) is not compatible with VPS Location Source"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            return
        }

        val bundle = Bundle().apply {
            putInt("locationSourceId", spinner.selectedItemPosition)
            putString("mapData", Json.encodeToString(mapData))
        }

        val destination = if (spinner.selectedItemPosition == 0) {
            R.id.action_InitialFragment_to_MapVPSFragment
        } else {
            R.id.action_InitialFragment_to_MapFragment
        }

        findNavController().navigate(destination, bundle)
    }

    override fun onDestroyView() {
        request?.dispose()
        super.onDestroyView()
        _binding = null
    }
}