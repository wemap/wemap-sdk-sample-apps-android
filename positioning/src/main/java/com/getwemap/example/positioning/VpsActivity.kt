package com.getwemap.example.positioning

import android.Manifest.permission
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.getwemap.example.positioning.databinding.ActivityVpsBinding
import com.getwemap.sdk.core.LocationSourceListener
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.locationsources.vps.WemapVPSARCoreLocationSource
import com.getwemap.sdk.locationsources.vps.WemapVPSARCoreLocationSourceError
import com.getwemap.sdk.locationsources.vps.WemapVPSARCoreLocationSourceListener
import com.getwemap.sdk.locationsources.vps.WemapVPSARCoreLocationSourceObserver

@RequiresApi(Build.VERSION_CODES.M)
class VpsActivity : AppCompatActivity() {

    private lateinit var vpsProvider: WemapVPSARCoreLocationSource
    private lateinit var binding: ActivityVpsBinding
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        vpsProvider = WemapVPSARCoreLocationSource(applicationContext, Constants.vpsEndpoint)

        binding = ActivityVpsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vpsProvider.bind(binding.surfaceview)
        vpsProvider.observers.add(vpsObserver)
        vpsProvider.listeners.add(vpsListener)
        vpsProvider.listener = locationListener

        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
                if (isGranted) {
                    internalStart()
                }
            }

        binding.startButton.setOnClickListener { requestPermissionsAndStart() }
        binding.stopButton.setOnClickListener { internalStop() }
        binding.startScanButton.setOnClickListener { vpsProvider.startScan() }
        binding.stopScanButton.setOnClickListener { vpsProvider.stopScan() }
    }

    private fun internalStart() {
        vpsProvider.start()
        binding.debugTextCoordinate.text = "Started, waiting info"
        binding.debugTextAttitude.text = ""
        binding.startButton.isEnabled = false
        binding.stopButton.isEnabled = true
        binding.scanButtons.visibility = View.VISIBLE
    }

    private fun internalStop() {
        vpsProvider.stop()
        binding.debugTextCoordinate.text = "Stopped"
        binding.debugTextAttitude.text = ""
        binding.imageview.setBackgroundResource(R.drawable.vps_idle_background)
        binding.imageview.setImageBitmap(null)
        binding.startButton.isEnabled = true
        binding.stopButton.isEnabled = false
        binding.scanButtons.visibility = View.GONE
    }

    private fun requestPermissionsAndStart() {
        when {
            ContextCompat.checkSelfPermission(applicationContext, permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED -> {
                internalStart()
            }
            !shouldShowRequestPermissionRationale("location") -> {
                requestPermissionLauncher.launch(permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        vpsProvider.unbind()
    }

    private val locationListener by lazy {
        object : LocationSourceListener {
            override fun onLocationChanged(coordinate: Coordinate) {
                runOnUiThread {
                    val text = "lat: ${String.format("%.7f", coordinate.latitude)} / " +
                            "lng: ${String.format("%.7f", coordinate.longitude)} / " +
                            "lvl: ${coordinate.levels} / " +
                            "alt: ${String.format("%.2f", coordinate.altitude)} / " +
                            "acc: ${String.format("%.2f", coordinate.accuracy)}"

                    binding.debugTextCoordinate.text = text
                }
            }

            override fun onAttitudeChanged(attitude: Attitude) {
                runOnUiThread {
                    val q = attitude.quaternion
                    val text = "\n" +
                            "q: [" +
                            "${String.format("%.2f", q.w)}, " +
                            "${String.format("%.2f", q.x)}, " +
                            "${String.format("%.2f", q.y)}, " +
                            String.format("%.2f", q.z) +
                            "]"

                    binding.debugTextAttitude.text = text
                }
            }
        }
    }

    private val vpsObserver by lazy {
        object : WemapVPSARCoreLocationSourceObserver {
            override fun onImageSend(bitmap: Bitmap) {
                binding.imageview.setImageBitmap(bitmap)
                binding.imageview.setBackgroundResource(R.drawable.vps_idle_background)
            }
        }
    }

    private val vpsListener by lazy {
        object : WemapVPSARCoreLocationSourceListener {

            override fun onScanStatusChanged(status: WemapVPSARCoreLocationSource.ScanStatus) {
                when(status) {
                    WemapVPSARCoreLocationSource.ScanStatus.STARTED -> {
                        binding.startScanButton.isEnabled = false
                        binding.stopScanButton.isEnabled = true
                    }
                    WemapVPSARCoreLocationSource.ScanStatus.STOPPED -> {
                        binding.startScanButton.isEnabled = true
                        binding.stopScanButton.isEnabled = false
                    }
                }
            }

            override fun onStateChanged(state: WemapVPSARCoreLocationSource.State) {
                val text = when(state) {
                    WemapVPSARCoreLocationSource.State.NORMAL -> "NORMAL"
                    WemapVPSARCoreLocationSource.State.SCAN_REQUIRED -> "SCAN_REQUIRED"
                    WemapVPSARCoreLocationSource.State.LIMITED_CORRECTION -> "LIMITED_CORRECTION"
                    WemapVPSARCoreLocationSource.State.NO_TRACKING -> "NO_TRACKING"
                }
                Toast.makeText(this@VpsActivity, text, Toast.LENGTH_LONG).show()
            }

            override fun onError(error: WemapVPSARCoreLocationSourceError) {
                Toast.makeText(this@VpsActivity, "Error occurred - $error", Toast.LENGTH_LONG).show()
            }
        }
    }
}