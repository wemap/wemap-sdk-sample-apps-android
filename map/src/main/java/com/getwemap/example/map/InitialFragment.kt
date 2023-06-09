package com.getwemap.example.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.map.databinding.FragmentInitialBinding
import com.getwemap.sdk.map.WemapMapSDK
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class InitialFragment : Fragment() {

    private val disposeBag by lazy { CompositeDisposable() }

    private var _binding: FragmentInitialBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInitialBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.buttonLoadMap.setOnClickListener {
            loadMap()
        }

        // if you need to retrieve all points of interest for some map in advance
//        val service = ServiceFactory.createService(
//            IPointOfInterestService::class.java,
//            CoreConstants.API_BASE_URL
//        )
//        val disp = service
//            .pointsOfInterestById(Constants.mapId)
//            .subscribe({
//                println("received pois - $it")
//            }, {
//                println("failed to receive pois with error - $it")
//            })
//        disposeBag.add(disp)
    }

    private fun loadMap() {
        // uncomment if you want to use dev environment
//        WemapMapSDK.setEnvironment(Environment.Dev())

        val disposable = WemapMapSDK.instance
            .map(Constants.mapId, Constants.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                println("Received map data - $it")
                val bundle = Bundle()
                bundle.putInt("id", it.id)
                bundle.putString("styleUrl", it.style)
                bundle.putDouble("minZoom", it.minZoom)
                bundle.putDouble("maxZoom", it.maxZoom)
                bundle.putDouble("latitudeNorth", it.bounds.latitudeNorth)
                bundle.putDouble("longitudeEast", it.bounds.longitudeEast)
                bundle.putDouble("latitudeSouth", it.bounds.latitudeSouth)
                bundle.putDouble("longitudeWest", it.bounds.longitudeWest)
                it.maxBounds?.let { maxBounds ->
                    bundle.putBoolean("hasMaxBounds", true)
                    bundle.putDouble("maxLatitudeNorth", maxBounds.latitudeNorth)
                    bundle.putDouble("maxLongitudeEast", maxBounds.longitudeEast)
                    bundle.putDouble("maxLatitudeSouth", maxBounds.latitudeSouth)
                    bundle.putDouble("maxLongitudeWest", maxBounds.longitudeWest)
                }
                findNavController().navigate(R.id.action_InitialFragment_to_MapFragment, bundle)
            }, {
                println("Failed to receive map data with error - ${it.message}")
            })
        disposeBag.add(disposable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}