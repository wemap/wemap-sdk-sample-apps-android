package com.getwemap.example.map.fragments

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Environment.DIRECTORY_DOCUMENTS
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.common.multiline
import com.getwemap.example.map.Config
import com.getwemap.example.map.LocationSourceType
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.configs.Environment
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.offline.Packdata
import com.getwemap.sdk.map.offline.PackdataService
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class InitialFragment : Fragment(), MenuProvider {

    // region Packdata UI
    private val packdataLabel: TextView get() = binding.packdataLabel
    private val packdataLayout: LinearLayout get() = binding.packdataLayout
    private val checkAndDownloadButton: Button get() = binding.checkAndDownloadButton
    // endregion Packdata UI

    // region Common UI
    private var _binding: FragmentInitialBinding? = null
    private val binding: FragmentInitialBinding get() = _binding!!

    private val spinner: Spinner get() = binding.spinner
    private val onlineSwitch: SwitchCompat get() = binding.onlineSwitch
    private val mapIdTextView: EditText get() = binding.mapIdTextView
    private val loadMapButton: Button get() = binding.buttonLoadMap
    private val envSwitch: SwitchCompat get() = binding.envSwitch
    // endregion Common UI

    private val sessionViewModel: SessionViewModel by activityViewModels()

    private var packdataService: PackdataService? = null
    private val userPreferences: SharedPreferences by lazy {
        requireContext().getSharedPreferences("wemap_prefs", Context.MODE_PRIVATE)
    }
    private var packdata: Packdata? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        updateSwitchText()

        mapIdTextView.setText("${Constants.MAP_ID}")

        ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, LocationSourceType.titles)
            .also { adapter ->
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinner.adapter = adapter
            }

        loadMapButton.setOnClickListener {
            showMap()
        }

        onlineSwitch.setOnClickListener {
            updateSwitchText()
        }

        envSwitch.setOnClickListener {
            envSwitch.text = if (envSwitch.isChecked) "Prod" else "Dev"
            mapIdTextView.setText("${Constants.MAP_ID}")
        }

        checkAndDownloadButton.setOnClickListener {
            if (checkAndDownloadButton.isSelected) {
                downloadNewPackdata()
            } else {
                checkForUpdates()
            }
        }

        // if you need to retrieve all points of interest for some map in advance
//        lifecycleScope.launch {
//            runCatching {
//                MapSession.create(requireContext(), Constants.MAP_ID, Constants.TOKEN)
//                    .pointOfInterestService
//                    .pointsOfInterest()
//            }.onSuccess {
//                println("received pois - $it")
//            }.onFailure {
//                println("failed to receive pois with error - $it")
//            }
//        }

        lifecycleScope.launch {
            packdata = loadPackdataIfAvailable()
            if (packdata != null) {
                packdataLabel.text = "Offline packdata (v${packdata!!.version})"
                loadMapButton.isEnabled = true
            } else {
                checkAndDownloadButton.text = "Download"
                checkAndDownloadButton.isSelected = true
            }
        }
    }

    override fun onDestroyView() {
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

    // region Private
    private fun updateSwitchText() {
        onlineSwitch.text = if (onlineSwitch.isChecked) "Online" else "Offline"
        packdataLayout.isVisible = !onlineSwitch.isChecked
        loadMapButton.isEnabled = onlineSwitch.isChecked || packdata != null
    }

    private fun showMap() {
        val message = "Desired location source is unavailable on this device"
        when (spinner.selectedItemPosition) {
            0, 3 -> if (SimulatorLocationSource.isAvailable) loadMap() else showAlert(message)
            1 -> loadMap()
            2 -> if (GmsFusedLocationSource.isAvailable(requireContext())) loadMap() else showAlert(message)
            else -> throw IllegalArgumentException("Unknown Location Source")
        }
    }

    private fun showAlert(message: String) {
        AlertDialog.Builder(context)
            .setMessage(message)
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun getMapId(): Int? {
        val text = mapIdTextView.text.toString()
        return text.toIntOrNull() ?: run {
            val text = "Failed to get int ID from - $text"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
            null
        }
    }

    private fun loadMap() {
        loadMapButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val session = (if (onlineSwitch.isChecked) createRemoteSession() else createLocalSession())
                    ?: throw IllegalArgumentException()

                showMap(session)
            } catch(e: Exception) {
                val text = "Failed to load map with error - $e"
                Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
            } finally {
                loadMapButton.isEnabled = true
            }
        }
    }

    private suspend fun createRemoteSession(): MapSession? {
        val id = getMapId()
            ?: return null

        return MapSession.create(requireContext(), id, Constants.TOKEN, Config.makeSessionConfig(requireContext()))
    }

    private fun showMap(session: MapSession) {

        // A session is not serializable, so hand it to the view fragments via the activity-scoped ViewModel.
        // replace(), not a bare assignment: coming back here and loading again would otherwise drop the previous
        // session without deiniting it.
        sessionViewModel.replace(session)

        val bundle = LocationSourceType.entries[spinner.selectedItemPosition].putInto(Bundle())

        Config.applyAppOptions(requireContext())
        findNavController().navigate(R.id.action_InitialFragment_to_SamplesListFragment, bundle)
    }
    // endregion Private

    // region Packdata
    private suspend fun createLocalSession(): MapSession? {
        val packdataItem = packdata
            ?: return null

        val documentsDir = requireContext().getExternalFilesDir(DIRECTORY_DOCUMENTS)
        val packdataFile = File(documentsDir, packdataItem.fileName)

        return MapSession.create(requireContext(), packdataFile, Config.makeSessionConfig(requireContext()))
    }

    private fun downloadNewPackdata() {
        val id = getMapId()
            ?: return

        checkAndDownloadButton.isEnabled = false

        lifecycleScope.launch {
            val service = getPackdataService(id)
            runCatching {
                service.downloadPackdata()
            }.onSuccess { packdataItem ->
                if (storePackdata(packdataItem)) {
                    checkAndDownloadButton.isSelected = false
                    checkAndDownloadButton.text = "Downloaded (v${packdataItem.version})"
                    loadMapButton.isEnabled = true
                }
            }.onFailure { error ->
                val text = "Failed to download packdata with error: $error"
                Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
                checkAndDownloadButton.isEnabled = true
            }
        }
    }

    private fun checkForUpdates() {
        val id = getMapId()
            ?: return

        val eTag = getETag()
            ?: return

        checkAndDownloadButton.isEnabled = false

        lifecycleScope.launch {
            val service = getPackdataService(id)
            runCatching {
                service.isNewPackdataAvailable(eTag)
            }.onSuccess { available ->
                checkAndDownloadButton.isSelected = available
                val title = if (available) "Download new packdata" else "Check for updates"
                checkAndDownloadButton.text = title
                if (!available) {
                    showAlert("No new packdata available yet")
                }
                checkAndDownloadButton.isEnabled = true
            }.onFailure { error ->
                val text = "Failed to check for packdata updates with error: $error"
                Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
                checkAndDownloadButton.isEnabled = true
            }
        }
    }

    private suspend fun loadPackdataIfAvailable(): Packdata? {
        // Off-main: the first prefs access initializes the lazy (dir check) and blocks on the XML load.
        val data = withContext(Dispatchers.IO) { userPreferences.getString("packdata", null) }
            ?: return null

        return try {
            Json.decodeFromString(data)
        } catch (e: Exception) {
            val text = "Failed to decode packdata from string with error: $e"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
            null
        }
    }

    private suspend fun storePackdata(packdata: Packdata): Boolean {
        return try {
            // Off-main: resolves the external files dir, copies the packdata file, and writes prefs.
            withContext(Dispatchers.IO) {
                val documentsDir = requireContext().getExternalFilesDir(DIRECTORY_DOCUMENTS)
                val destinationFile = File(documentsDir, packdata.fileName)

                if (destinationFile.exists()) {
                    destinationFile.delete()
                }
                File(packdata.filePath).copyTo(destinationFile)

                val encoded = Json.encodeToString(packdata)
                userPreferences.edit(commit = true) {
                    putString("packdata", encoded)
                }
            }
            this.packdata = packdata
            true
        } catch (e: Exception) {
            val text = "Failed to save downloaded packdata with error: ${e.message}"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
            false
        }
    }

    private fun getETag(): String? {
        val eTag = packdata?.eTag ?: run {
            val text = "Failed to get ETag from user defaults"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).multiline().show()
            return null
        }
        return eTag
    }

    private suspend fun getPackdataService(mapId: Int): PackdataService {
        return packdataService ?: MapSession.createPackdataService(mapId, Environment.PROD).also {
            packdataService = it
        }
    }

    // endregion Packdata

}