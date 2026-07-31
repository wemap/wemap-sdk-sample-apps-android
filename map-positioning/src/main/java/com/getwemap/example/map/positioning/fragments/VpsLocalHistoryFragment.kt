package com.getwemap.example.map.positioning.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getwemap.example.common.Constants
import com.getwemap.example.common.multiline
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.VpsLocalSessionHistory
import com.getwemap.example.map.positioning.databinding.FragmentVpsLocalHistoryBinding
import com.getwemap.example.map.positioning.databinding.ItemVpsLocalSessionBinding
import com.getwemap.sdk.map.WemapMapSDK
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Past offline-VPS scanning sessions for one venue, newest first — how a walk went, reviewed after the fact.
 *
 * This exists because the interesting sessions are the unwatchable ones: on a lanyard or in a pocket the
 * screen cannot be read while the SDK works, and haptics only say "a fix happened", not how many, how often,
 * or where. Each row summarises one session and opens its trace on the venue map.
 */
class VpsLocalHistoryFragment : Fragment() {

    private var _binding: FragmentVpsLocalHistoryBinding? = null
    private val binding get() = _binding!!

    private val mapId: Int by lazy { requireArguments().getInt(ARG_MAP_ID) }
    private var mapDataJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVpsLocalHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.sessionsList.layoutManager = LinearLayoutManager(requireContext())
        binding.deleteAllButton.setOnClickListener {
            VpsLocalSessionHistory.deleteAll(requireContext(), mapId)
            showSessions()
        }
        showSessions()
    }

    private fun showSessions() {
        val sessions = VpsLocalSessionHistory.listSessions(requireContext(), mapId)
        binding.historyHeader.text = getString(R.string.vps_local_history_header, mapId, sessions.size)
        binding.emptyText.isVisible = sessions.isEmpty()
        binding.deleteAllButton.isVisible = sessions.isNotEmpty()
        binding.sessionsList.adapter = SessionsAdapter(sessions) { openTrace(it) }
    }

    /**
     * Opens a session's trace. The venue's [com.getwemap.sdk.core.model.entities.MapData] is fetched here
     * rather than passed down from the launching screen: a session records the map id it belongs to, so
     * history is reachable without having loaded that venue's map first.
     */
    private fun openTrace(session: VpsLocalSessionHistory.Session) {
        if (session.fixes.isEmpty()) {
            Snackbar.make(binding.root, R.string.vps_local_history_no_fixes, Snackbar.LENGTH_LONG)
                .multiline().show()
            return
        }
        mapDataJob?.cancel()
        mapDataJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val mapData = WemapMapSDK.instance.mapData(session.mapId, Constants.TOKEN)
                findNavController().navigate(
                    R.id.action_VpsLocalHistoryFragment_to_VpsLocalHistoryMapFragment,
                    Bundle().apply {
                        putString(VpsLocalHistoryMapFragment.ARG_SESSION_FILE, session.file.absolutePath)
                        putString(VpsLocalHistoryMapFragment.ARG_MAP_DATA, Json.encodeToString(mapData))
                    },
                )
            } catch (e: Exception) {
                Snackbar.make(
                    binding.root,
                    "Failed to receive map data with error - ${e.message}",
                    Snackbar.LENGTH_LONG,
                ).multiline().show()
            }
        }
    }

    override fun onDestroyView() {
        mapDataJob?.cancel()
        super.onDestroyView()
        _binding = null
    }

    private class SessionsAdapter(
        private val sessions: List<VpsLocalSessionHistory.Session>,
        private val onClick: (VpsLocalSessionHistory.Session) -> Unit,
    ) : RecyclerView.Adapter<SessionsAdapter.ViewHolder>() {

        class ViewHolder(val binding: ItemVpsLocalSessionBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemVpsLocalSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val session = sessions[position]
            holder.binding.sessionTitle.text = session.title
            holder.binding.sessionSummary.text = session.summary
            holder.binding.sessionOutcomes.text = session.outcomeBreakdown
            holder.binding.root.setOnClickListener { onClick(session) }
        }

        override fun getItemCount() = sessions.size
    }

    companion object {
        const val ARG_MAP_ID = "mapId"
    }
}
