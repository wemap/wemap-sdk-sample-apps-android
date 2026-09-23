package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.Constants
import com.getwemap.example.common.SessionViewModel
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.R
import com.getwemap.example.positioning.ar.databinding.FragmentInitialBinding
import com.getwemap.sdk.core.CoreSession
import com.getwemap.sdk.core.configs.SessionConfig
import com.getwemap.sdk.core.helpers.Logger
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class InitialFragment : Fragment(), MenuProvider {

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
        requireActivity().addMenuProvider(this, viewLifecycleOwner)

        Logger.level = Log.VERBOSE
        mapIdTextView.setText("${Constants.MAP_ID}")

        binding.buttonLoadMap.setOnClickListener {
            loadMap()
        }
    }

    private fun loadMap() {
        val text = mapIdTextView.text.toString()
        val id = text.toIntOrNull()
            ?: return println("Failed to get int ID from - '$text'")

        if (request?.isActive == true)
            return

        request = lifecycleScope.launch {
            runCatching {
                CoreSession.create(requireContext(), id, Constants.TOKEN, SessionConfig())
            }.onSuccess {
                println("Created session - $it")
                sessionViewModel.replace(it)

                findNavController().navigate(R.id.action_InitialFragment_to_SamplesListFragment)
            }.onFailure {
                val str = "Failed to create session with error - ${it.message}"
                Snackbar.make(binding.root, str, Snackbar.LENGTH_LONG).multiline().show()
            }
        }
    }

    override fun onDestroyView() {
        request?.cancel()
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