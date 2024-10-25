package com.getwemap.example.common.map

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.view.ContextThemeWrapper
import com.getwemap.sdk.core.model.entities.Level
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.BuildingManagerListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup

class MapLevelsSwitcher(context: Context, attrs: AttributeSet?) :
    MaterialButtonToggleGroup(context, attrs) {

    private var wemapMapView: WemapMapView? = null
    private val buildingManager get() = wemapMapView?.buildingManager
    private var sortedLevels = listOf<Level>()

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#CCFFFFFF"))

        val paddingInDp = 6
        val scale = resources.displayMetrics.density
        val paddingInPx = (paddingInDp * scale).toInt()
        setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

        addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            val level = sortedLevels[checkedId]
            val focusedBuilding = buildingManager?.focusedBuilding ?: return@addOnButtonCheckedListener
            focusedBuilding.activeLevel = level
        }
    }

    private val buildingFocusChangeListener = object : BuildingManagerListener {
        override fun onFocusedBuildingChanged(building: Building?) {
            populateLevels(building)
        }

        override fun onActiveLevelChanged(building: Building, level: Level) {
            check(sortedLevels.indexOf(level))
        }
    }

    fun bind(wemapMapView: WemapMapView) {
        buildingManager?.removeListener(buildingFocusChangeListener)
        this.wemapMapView = wemapMapView
        wemapMapView.getMapViewAsync { _, _, _, _ ->
            populateLevels(wemapMapView.buildingManager.focusedBuilding)
            wemapMapView.buildingManager.addListener(buildingFocusChangeListener)
        }
    }

    private fun populateLevels(building: Building?) {
        if (building == null) {
            visibility = INVISIBLE
            sortedLevels = emptyList()
            return
        }

        clearChecked()
        removeAllViews()
        visibility = VISIBLE

        val layout = LayoutParams(150, LayoutParams.WRAP_CONTENT)

        sortedLevels = building.levels.sortedBy { it.id }.reversed()
        sortedLevels
            .mapIndexed { index, level ->
                MaterialButton(
                    ContextThemeWrapper(context, R.style.Widget_Button_LevelSelectorButton),
                    null, 0
                ).apply {
                    id = index
                    text = level.shortName
                    layoutParams = layout
                }
            }
            .forEach { addView(it) }

        check(sortedLevels.indexOf(building.activeLevel))
    }


}