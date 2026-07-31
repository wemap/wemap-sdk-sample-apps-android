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
import androidx.core.widget.doAfterTextChanged
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
    private val datasetSpinner get() = binding.vpsLocalDatasetSpinner

    /** Map id currently typed in [mapIdTextView], or `null` when it is empty / not a number. */
    private val enteredMapId: Int? get() = mapIdTextView.text.toString().toIntOrNull()

    /** Guards the two-way sync between [datasetSpinner] and [mapIdTextView] against feedback loops. */
    private var isSyncingVpsLocalMapId = false

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

        setupVpsLocalDatasetSpinner()

        // Availability is per map id, so the offline VPS UI follows whatever is typed in the field.
        mapIdTextView.doAfterTextChanged {
            if (spinner.selectedItemPosition != LOCATION_SOURCE_VPS_LOCAL)
                return@doAfterTextChanged

            selectDatasetOf(enteredMapId)
            updateVpsLocalStatus()
        }

        binding.vpsLocalDownloadButton.setOnClickListener {
            downloadVpsLocalDatabase()
        }

        // History is keyed off the map id in the field, like everything else in this section, and needs no
        // downloaded dataset — a session recorded earlier is reviewable even after the database is deleted.
        binding.vpsLocalHistoryButton.setOnClickListener {
            val mapId = enteredMapId
            if (mapId == null) {
                Snackbar.make(binding.root, "Enter a map id first", Snackbar.LENGTH_LONG).multiline().show()
                return@setOnClickListener
            }
            findNavController().navigate(
                R.id.action_InitialFragment_to_VpsLocalHistoryFragment,
                Bundle().apply { putInt(VpsLocalHistoryFragment.ARG_MAP_ID, mapId) },
            )
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
            LOCATION_SOURCE_VPS_LOCAL -> { // VPS Local (offline)
                val mapId = enteredMapId
                if (mapId != null && VpsLocalMapDownloader.isAvailable(requireContext(), mapId))
                    loadMap()
                else
                    showUnavailableAlert("Offline VPS map database of map $mapId is not downloaded yet")
            }
            else ->  throw IllegalArgumentException("Unknown Location Source")
        }
    }

    // region ------ VPS Local (offline) ------

    /**
     * Fills the dataset spinner with the venues [VpsLocalMapDownloader] knows about. Picking one only
     * writes its map id into [mapIdTextView] — the text watcher takes it from there, so a hand-typed
     * id and a picked dataset go through exactly the same path.
     */
    private fun setupVpsLocalDatasetSpinner() {
        val titles = VpsLocalMapDownloader.DATASETS.map { "${it.name} (${it.mapId})" }
        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, titles)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                datasetSpinner.adapter = adapter
            }

        datasetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isSyncingVpsLocalMapId)
                    return

                val dataset = VpsLocalMapDownloader.DATASETS.getOrNull(position) ?: return
                // An unconfigured placeholder entry has no real map id to offer — leave the field alone
                // so the user can type one instead of seeing the placeholder value appear.
                if (!dataset.isConfigured)
                    return

                mapIdTextView.setText("${dataset.mapId}")
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Moves the dataset spinner onto [mapId], when a dataset is registered for it. */
    private fun selectDatasetOf(mapId: Int?) {
        val index = VpsLocalMapDownloader.DATASETS.indexOfFirst { it.mapId == mapId }
        if (index < 0 || index == datasetSpinner.selectedItemPosition)
            return

        isSyncingVpsLocalMapId = true
        datasetSpinner.setSelection(index)
        isSyncingVpsLocalMapId = false
    }

    private fun updateVpsLocalUi() {
        val isVpsLocal = spinner.selectedItemPosition == LOCATION_SOURCE_VPS_LOCAL
        binding.vpsLocalLayout.isVisible = isVpsLocal
        if (!isVpsLocal)
            return

        // Prefill the map id of the selected dataset, unless the typed one already matches a dataset.
        if (enteredMapId?.let { VpsLocalMapDownloader.datasetFor(it) } == null) {
            val selected = VpsLocalMapDownloader.DATASETS.getOrNull(datasetSpinner.selectedItemPosition)
            if (selected != null && selected.isConfigured)
                mapIdTextView.setText("${selected.mapId}")
        }

        selectDatasetOf(enteredMapId)
        updateVpsLocalStatus()
    }

    /**
     * Reflects the state of the map database of the currently entered map id. Every venue lives in its
     * own directory, so an already-downloaded one is reported as ready without touching the others.
     */
    private fun updateVpsLocalStatus() {
        if (downloadJob?.isActive == true)
            return

        val mapId = enteredMapId
        val dataset = mapId?.let { VpsLocalMapDownloader.datasetFor(it) }
        val downloadButton = binding.vpsLocalDownloadButton

        when {
            dataset == null || !dataset.isConfigured -> {
                binding.vpsLocalStatus.text =
                    "No offline VPS dataset configured for map id ${mapId ?: "?"}. Add the map id and " +
                            "dataset URL Wemap sent you to VpsLocalMapDownloader.DATASETS"
                downloadButton.isEnabled = false
                downloadButton.text = "Download"
            }
            VpsLocalMapDownloader.isAvailable(requireContext(), dataset.mapId) -> {
                binding.vpsLocalStatus.text = "Offline VPS map database of ${dataset.name} is ready"
                downloadButton.isEnabled = true
                downloadButton.text = "Re-download"
            }
            else -> {
                binding.vpsLocalStatus.text = "Offline VPS map database of ${dataset.name} is not downloaded"
                downloadButton.isEnabled = true
                downloadButton.text = "Download"
            }
        }
    }

    private fun downloadVpsLocalDatabase() {
        if (downloadJob?.isActive == true)
            return

        val mapId = enteredMapId
        if (mapId == null) {
            binding.vpsLocalStatus.text = "Enter the map id of the venue to download"
            return
        }

        val ids = try {
            // Only a re-download of an already-complete dataset refetches everything; otherwise the
            // files already on the device are kept and only the missing ones are downloaded.
            val forceAll = VpsLocalMapDownloader.isAvailable(requireContext(), mapId)
            VpsLocalMapDownloader.enqueueDownloads(requireContext(), mapId, forceAll)
        } catch (e: IllegalStateException) {
            // Raised by VpsLocalMapDownloader when no dataset is configured for this map id.
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
                        // Clear the job first — updateVpsLocalStatus skips while a download runs.
                        downloadJob = null
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

        val position = spinner.selectedItemPosition

        if (position == 0 && mapData.extras?.vpsEndpoint == null) { // VPS
            val text = "This map(${mapData.id}) is not compatible with VPS Location Source"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            return
        }

        val bundle = Bundle().apply {
            putInt("locationSourceId", position)
            putString("mapData", Json.encodeToString(mapData))
            if (spinner.selectedItemPosition == LOCATION_SOURCE_VPS_LOCAL) {
                putString("mapDir", VpsLocalMapDownloader.mapDir(requireContext(), mapData.id).absolutePath)
            }
        }

        val destination = when (position) {
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
                // Pass the selected source so Settings shows only the relevant preference categories.
                val bundle = Bundle().apply {
                    putInt("locationSourceId", spinner.selectedItemPosition)
                }
                findNavController().navigate(R.id.action_Anywhere_to_SettingsFragment, bundle)
                true
            }
            else -> false
        }
    }
}