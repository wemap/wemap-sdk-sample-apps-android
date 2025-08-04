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
import com.getwemap.sdk.map.WemapMapSDK
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
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
            0 ->
                WemapVPSARCoreLocationSource.checkAvailabilityAsync(requireContext()) { availability ->
                    when (availability) {
                        SUPPORTED_INSTALLED ->
                            loadMap()
                        SUPPORTED_NOT_INSTALLED ->
                            installARCore()
                        else ->
                            showUnavailableAlert()
                    }
                }
            1 ->
                if (GmsFusedLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            2, 3 ->
                if (PolestarLocationSource.isAvailable) loadMap() else showUnavailableAlert()
            else ->
                throw RuntimeException("Unknown Location Source")
        }
    }

    // requestInstall(Activity, true) will triggers installation of
    // Google Play Services for AR if necessary.
    private var userRequestedInstall = true

    private fun installARCore() {
        try {
            when (ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)) {
                ArCoreApk.InstallStatus.INSTALLED ->
                    loadMap()
                ArCoreApk.InstallStatus.INSTALL_REQUESTED ->
                    userRequestedInstall = false
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
                bundle.putString("mapData", Json.encodeToString(it))

                Config.applyGlobalOptions(requireContext())
                if (spinner.selectedItemPosition == 0) {
                    findNavController().navigate(R.id.action_InitialFragment_to_MapVPSFragment, bundle)
                } else {
                    bundle.putInt("locationSourceId", spinner.selectedItemPosition)
                    findNavController().navigate(R.id.action_InitialFragment_to_MapFragment, bundle)
                }
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