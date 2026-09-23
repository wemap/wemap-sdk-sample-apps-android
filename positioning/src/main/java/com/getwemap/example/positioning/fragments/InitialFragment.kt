package com.getwemap.example.positioning.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.SessionViewModel
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.R
import com.getwemap.example.positioning.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.CoreSession
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.ArCoreApk
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_INSTALLED
import com.google.ar.core.ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALLED
import com.google.ar.core.ArCoreApk.InstallStatus.INSTALL_REQUESTED
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class InitialFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    private var request: Job? = null

    private var _binding: FragmentInitialBinding? = null
    private val binding get() = _binding!!

    private val mapIdTextView get() = binding.mapIdTextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapIdTextView.setText("${Constants.MAP_ID}")

        binding.buttonLoadMap.setOnClickListener {
            checkAvailability()
        }
    }

    private fun checkAvailability() {
        VpsARCoreLocationSource.checkAvailabilityAsync(requireContext()) { availability ->
            when (availability) {
                SUPPORTED_INSTALLED -> loadMap()
                SUPPORTED_NOT_INSTALLED ->  installARCore()
                else -> showUnavailableAlert()
            }
        }
    }

    // requestInstall(Activity, true) will trigger installation of Google Play Services for AR if necessary
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

        if (request?.isActive == true)
            return

        request = lifecycleScope.launch {
            runCatching {
                CoreSession.create(requireContext(), id, Constants.TOKEN)
            }.onSuccess {
                showMap(it)
            }.onFailure {
                val str = "Failed to receive map data with error - ${it.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            }
        }
    }

    private fun showMap(session: CoreSession) {
        if (!session.isVpsEnabled) {
            val text = "This map(${session.mapId}) is not compatible with VPS Location Source"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            // Nothing takes ownership of a session we never hand over, so release it here.
            session.deinit()
            return
        }

        sessionViewModel.replace(session)

        findNavController().navigate(R.id.action_InitialFragment_to_VpsFragment)
    }

    override fun onDestroyView() {
        request?.cancel()
        super.onDestroyView()
        _binding = null
    }
}