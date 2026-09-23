package com.getwemap.example.common.map

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.toColorInt
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.BuildingManager
import com.getwemap.sdk.map.buildings.Level
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A minimal level rail built from Material components, driven by a [BuildingManager].
 *
 * It exists so the samples have a levels control without depending on anything beyond the map SDK. The
 * manager reports through `Flow` properties rather than a listener interface, so [bind] takes the scope to
 * collect in and [unbind] cancels it.
 */
class MapLevelsSwitcher(context: Context, attrs: AttributeSet?) : MaterialButtonToggleGroup(context, attrs) {

    private var buildingManager: BuildingManager? = null
    private var collectJob: Job? = null
    private var sortedLevels = listOf<Level>()

    init {
        orientation = VERTICAL
        setBackgroundColor("#CCFFFFFF".toColorInt())

        val paddingInDp = 6
        val scale = resources.displayMetrics.density
        val paddingInPx = (paddingInDp * scale).toInt()
        setPadding(paddingInPx, paddingInPx, paddingInPx, paddingInPx)

        addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) {
                return@addOnButtonCheckedListener
            }
            val level = sortedLevels.getOrNull(checkedId)
                ?: return@addOnButtonCheckedListener
            val focusedBuilding = buildingManager?.focusedBuildings?.value
                ?: return@addOnButtonCheckedListener
            focusedBuilding.activeLevel = level
        }
    }

    /**
     * Follows [buildingManager], collecting in [scope] until [unbind].
     *
     * Pass the view's own scope — `viewLifecycleOwner.lifecycleScope` from a fragment — so the collectors die
     * with the view that owns them.
     */
    fun bind(buildingManager: BuildingManager, scope: CoroutineScope) {
        unbind()
        this.buildingManager = buildingManager

        // Deliberately NOT `repeatOnLifecycle`: `activeLevelChanges` replays nothing and `focusedBuildings`
        // conflates equal values, so a level change while the collector was paused would leave the rail on
        // the old level with nothing to re-seed it.
        collectJob = scope.launch {
            launch {
                // A StateFlow, so collecting it seeds the rail with the building already focused.
                buildingManager.focusedBuildings.collect { populateLevels(it) }
            }
            launch {
                buildingManager.activeLevelChanges.collect { (_, level) -> check(sortedLevels.indexOf(level)) }
            }
        }
    }

    fun unbind() {
        collectJob?.cancel()
        collectJob = null
        buildingManager = null
    }

    // region ------ Private ------
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
    // endregion ------ Private ------
}
