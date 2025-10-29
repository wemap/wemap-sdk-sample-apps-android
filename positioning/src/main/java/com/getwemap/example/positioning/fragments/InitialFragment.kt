package com.getwemap.example.positioning.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.Config
import com.getwemap.example.positioning.R
import com.getwemap.example.positioning.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.model.ServiceFactory
import com.getwemap.sdk.core.model.entities.MapData
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

        binding.buttonLoadMap.setOnClickListener {
            checkAvailability()
        }
    }

    private fun checkAvailability() {
        WemapVPSARCoreLocationSource.checkAvailabilityAsync(requireContext()) { availability ->
            when (availability) {
                SUPPORTED_INSTALLED -> loadMap()
                SUPPORTED_NOT_INSTALLED ->  installARCore()
                else -> showUnavailableAlert()
            }
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

        request = ServiceFactory
            .getMapService()
            .mapById(id, Constants.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                showMap(it)
            }, {
                val str = "Failed to receive map data with error - ${it.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            })
    }

    private fun showMap(mapData: MapData) {
        Config.applyGlobalOptions(requireContext())

        if (mapData.extras?.vpsEndpoint == null) {
            val text = "This map(${mapData.id}) is not compatible with VPS Location Source"
            Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).show()
            return
        }

        val bundle = Bundle().apply {
            putString("mapData", Json.encodeToString(mapData))
        }

        findNavController().navigate(R.id.action_InitialFragment_to_VPSFragment, bundle)
    }

    override fun onDestroyView() {
        request?.dispose()
        super.onDestroyView()
        _binding = null
    }
}