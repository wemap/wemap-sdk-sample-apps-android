package com.getwemap.example.map.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentItemBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.poi.PointOfInterestWithInfo
import com.getwemap.sdk.map.poi.IMapPointOfInterestManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable

class PoisViewModel: ViewModel() {
    lateinit var poiManager: IMapPointOfInterestManager
    lateinit var userCoordinate: Coordinate
    lateinit var mapData: MapData
}

class PoisListFragment : BottomSheetDialogFragment() {

    private val viewModel: PoisViewModel by activityViewModels()
    private val disposeBag = CompositeDisposable()

    private var _poisAdapter: PoisRecyclerViewAdapter? = null
    private val poisAdapter get() = _poisAdapter!!

    private val listener by lazy {
        object : OnRecyclerViewClickListener {
            override fun onClick(view: View, position: Int) {
                val item = poisAdapter.pois[position]
                viewModel.poiManager.selectPOI(item.first)
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_item_list, container, false) as RecyclerView
        val poisWithDistance = viewModel.poiManager.getPOIs().map { PointOfInterestWithInfo(it, null) }
        _poisAdapter = PoisRecyclerViewAdapter(listener, poisWithDistance)
        with(view) {
            layoutManager = LinearLayoutManager(context)
            adapter = poisAdapter
        }
        return view
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.poiManager
            .sortPOIsByGraphDistance(viewModel.userCoordinate)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                poisAdapter.pois = it
                poisAdapter.notifyDataSetChanged()
            }, {
                println("Failed to sort POIs with error - $it")
            })
            .disposedBy(disposeBag)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposeBag.clear()
        _poisAdapter = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeBag.dispose()
    }
}

class PoisRecyclerViewAdapter(
    private val listener: OnRecyclerViewClickListener,
    var pois: List<PointOfInterestWithInfo>
) : RecyclerView.Adapter<PoisRecyclerViewAdapter.ViewHolder>() {

    private val items: List<SamplesItem> get() {
        return pois.map {
            val poi = it.first
            val info = it.second
            SamplesItem(
                poi.name,
                "id - ${poi.id}\nlevel - ${poi.coordinate.levels.firstOrNull() ?: "ground"}\n" +
                        "address - ${poi.address}\ndistance - ${info?.distance ?: Double.MAX_VALUE}\n" +
                        "duration - ${info?.duration ?: Float.MAX_VALUE}"
            )
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = FragmentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        binding.root.setOnClickListener { listener.onClick(it, holder.id) }
        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.contentView.text = item.content
        holder.detailedView.text = item.details
        holder.id = position
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(binding: FragmentItemBinding) : RecyclerView.ViewHolder(binding.root) {
        val contentView = binding.content
        val detailedView = binding.detailed
        var id = -1
    }

    data class SamplesItem(val content: String, val details: String)
}