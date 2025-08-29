package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.R
import com.getwemap.example.positioning.ar.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.model.ServiceFactory
import com.google.android.material.snackbar.Snackbar
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

        mapIdTextView.setText("22418")

        // uncomment if you want to use dev environment
//        WemapCoreSDK.setEnvironment(Environment.Dev())
//        WemapCoreSDK.setItinerariesEnvironment(Environment.Dev())

        binding.buttonLoadMap.setOnClickListener {
            loadMap()
        }
    }

    private fun loadMap() {
        val text = mapIdTextView.text.toString()
        val id = text.toIntOrNull()
            ?: return println("Failed to get int ID from - '$text'")

        if (request?.isDisposed == false)
            return

        request = ServiceFactory.getMapService()
            .mapById(id, Constants.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                println("Received map data - $it")
                val bundle = Bundle()
                bundle.putString("mapData", Json.encodeToString(it))

                findNavController().navigate(R.id.action_InitialFragment_to_SamplesListFragment, bundle)
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