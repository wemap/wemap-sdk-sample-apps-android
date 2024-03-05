package com.getwemap.example.map.positioning

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.map.positioning.databinding.FragmentInitialBinding
import com.getwemap.sdk.map.WemapMapSDK
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class InitialFragment : Fragment() {

    private val disposeBag by lazy { CompositeDisposable() }

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
            loadMap()
        }
    }

    private fun loadMap() {
        val text = mapIdTextView.text.toString()
        val id = text.toIntOrNull()
        if (id == null) {
            println("Failed to get int ID from - '$text'")
            return
        }

        val disposable = WemapMapSDK.instance
            .mapData(id, Constants.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                println("Received map data - $it")
                val bundle = Bundle()
                bundle.putInt("locationSourceId", spinner.selectedItemPosition)
                bundle.putString("mapData", Json.encodeToString(it))

                if (spinner.selectedItemPosition == 3)
                    findNavController().navigate(R.id.action_InitialFragment_to_MapVPSFragment, bundle)
                else
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