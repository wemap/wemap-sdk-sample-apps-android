package com.getwemap.example.map.positioning.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.multiline
import com.getwemap.example.map.positioning.Config
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.VpsLocalMapDownloader
import com.getwemap.example.map.positioning.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.WemapMapSDK
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GPSLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val LOCATION_SOURCE_VPS_LOCAL = 5

class InitialFragment : Fragment(), MenuProvider {

    private var requestJob: Job? = null
    private var downloadJob: Job? = null

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
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

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

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateVpsLocalUi()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        binding.vpsLocalDownloadButton.setOnClickListener {
            downloadVpsLocalDatabase()
        }

        binding.buttonLoadMap.setOnClickListener {
            checkAvailability()
        }

        updateVpsLocalUi()
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
            LOCATION_SOURCE_VPS_LOCAL -> // VPS Local (offline)
                if (VpsLocalMapDownloader.isAvailable(requireContext()))
                    loadMap()
                else
                    showUnavailableAlert("Offline VPS map database is not downloaded yet")
            else ->  throw IllegalArgumentException("Unknown Location Source")
        }
    }

    // region ------ VPS Local (offline) ------
    private fun updateVpsLocalUi() {
        val isVpsLocal = spinner.selectedItemPosition == LOCATION_SOURCE_VPS_LOCAL
        binding.vpsLocalLayout.isVisible = isVpsLocal
        if (!isVpsLocal)
            return

        // Prefill the map id that matches the offline dataset, when it is configured.
        if (VpsLocalMapDownloader.MAP_ID >= 0)
            mapIdTextView.setText("${VpsLocalMapDownloader.MAP_ID}")

        if (VpsLocalMapDownloader.isAvailable(requireContext())) {
            binding.vpsLocalStatus.text = "Offline VPS map database ready"
            binding.vpsLocalDownloadButton.text = "Re-download"
        } else {
            binding.vpsLocalStatus.text = "Offline VPS map database not downloaded"
            binding.vpsLocalDownloadButton.text = "Download"
        }
    }

    private fun downloadVpsLocalDatabase() {
        if (downloadJob?.isActive == true)
            return

        val ids = try {
            val forceAll = VpsLocalMapDownloader.isAvailable(requireContext())
            VpsLocalMapDownloader.enqueueDownloads(requireContext(), forceAll)
        } catch (e: IllegalStateException) {
            // Raised by VpsLocalMapDownloader when MAP_ID / MAP_NAME are still placeholders.
            binding.vpsLocalStatus.text = e.message
            return
        }
        if (ids.isEmpty()) {
            updateVpsLocalUi()
            return
        }

        binding.vpsLocalDownloadButton.isEnabled = false

        downloadJob = lifecycleScope.launch {
            while (isActive) {
                val progress = VpsLocalMapDownloader.queryProgress(requireContext(), ids)
                val downloaded = progress.sumOf { it.downloaded }
                val total = progress.sumOf { it.total }.coerceAtLeast(1)

                when {
                    progress.any { it.failed } -> {
                        binding.vpsLocalStatus.text = "Download failed"
                        binding.vpsLocalDownloadButton.isEnabled = true
                        break
                    }
                    progress.isNotEmpty() && progress.all { it.done } -> {
                        binding.vpsLocalDownloadButton.isEnabled = true
                        updateVpsLocalUi()
                        break
                    }
                    else -> {
                        binding.vpsLocalStatus.text =
                            "Downloading… ${downloaded / 1_000_000}MB / ${total / 1_000_000}MB"
                    }
                }
                delay(500)
            }
        }
    }
    // endregion ------ VPS Local (offline) ------

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

        if (requestJob?.isActive == true)
            return

        binding.buttonLoadMap.isEnabled = false

        requestJob = lifecycleScope.launch {
            try {
                val mapData = WemapMapSDK.instance.mapData(id, Constants.TOKEN)
                showMap(mapData)
            } catch (e: Exception) {
                val str = "Failed to receive map data with error - ${e.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            } finally {
                binding.buttonLoadMap.isEnabled = true
            }
        }
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
            if (spinner.selectedItemPosition == LOCATION_SOURCE_VPS_LOCAL) {
                putString("mapDir", VpsLocalMapDownloader.mapDir(requireContext()).absolutePath)
            }
        }

        val destination = when (spinner.selectedItemPosition) {
            0 -> R.id.action_InitialFragment_to_MapVPSFragment
            LOCATION_SOURCE_VPS_LOCAL -> R.id.action_InitialFragment_to_MapVPSLocalFragment
            else -> R.id.action_InitialFragment_to_MapFragment
        }

        findNavController().navigate(destination, bundle)
    }

    override fun onDestroyView() {
        requestJob?.cancel()
        downloadJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.settings_menu, menu)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.preferences -> {
                findNavController().navigate(R.id.action_Anywhere_to_SettingsFragment)
                true
            }
            else -> false
        }
    }
}