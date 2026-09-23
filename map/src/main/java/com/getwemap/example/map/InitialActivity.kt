package com.getwemap.example.map

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.graphics.drawable.DrawerArrowDrawable
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.getwemap.example.common.R as CommonR
import com.getwemap.example.map.databinding.ActivityInitialBinding
import com.google.android.material.appbar.AppBarLayout

class InitialActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityInitialBinding
    private lateinit var navController: NavController

    // The elevation the app bar was inflated with, restored when it goes back to painting.
    private var appBarElevation: Float = 0f

    // Whether the app bar is currently floating over a map, which is what the content's insets report.
    private var isAppBarTransparent: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // So a destination that hides the app bar lets the map reach the status bar, the way the iOS samples
        // show it. Always on rather than per destination: toggling `setDecorFitsSystemWindows` on navigation
        // leaves the returning screen a bare band between the status bar and its app bar.
        enableEdgeToEdge()

        binding = ActivityInitialBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        appBarElevation = binding.appBarLayout.elevation

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            binding.appBarLayout.updatePadding(top = systemBars.top)
            insets
        }

        // A transparent app bar is chrome this app floats over the map, not a system bar, so nothing under it
        // knows to keep clear of it. Reporting it as part of the content's own top inset is what makes
        // `safeDrawingPadding()` inside a widget and `insetOverlayBelowTransparentAppBar()` on a screen's
        // overlay container both clear it — the same thing iOS gets for free, where a navigation bar sits
        // outside the container's safe area. The insets have to be handed on with `onApplyWindowInsets`
        // rather than returned, or the children this is for never see them.
        ViewCompat.setOnApplyWindowInsetsListener(binding.content.root) { view, insets ->
            val reported = if (isAppBarTransparent) insets.grownByTopInset(actionBarSize) else insets
            ViewCompat.onApplyWindowInsets(view, reported)
        }

        navController = findNavController(R.id.nav_host_fragment_content_initial)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            setTransparentAppBar(destination.id in MAP_DESTINATIONS)
        }
    }

    /**
     * Floats the app bar over a full-bleed map instead of stacking it above one, the way the iOS samples show
     * it. The bar keeps its back button and its title; it just stops painting.
     *
     * Only the map destinations take it ([MAP_DESTINATIONS]) — over the initial screen or the samples list
     * there is nothing for the bar to be transparent against, and the title would land on the list's first
     * row.
     */
    private fun setTransparentAppBar(isTransparent: Boolean) {

        isAppBarTransparent = isTransparent
        // The content's inset listener reads the flag, and nothing else would make it run again.
        binding.content.root.requestApplyInsets()

        binding.appBarLayout.setBackgroundColor(
            if (isTransparent) Color.TRANSPARENT else getColor(CommonR.color.wemap_blue)
        )

        // A transparent background is not enough on its own: `AppBarLayout` composites Material's elevation
        // overlay over whatever background it has, so a transparent one still washes a band of the map. The
        // state list animator is what would raise the elevation back on the next lift, so it goes too.
        binding.appBarLayout.stateListAnimator = null
        binding.appBarLayout.elevation = if (isTransparent) 0f else appBarElevation

        // The content has to come out from under the bar, or the map still starts below it. Nulling the
        // behaviour is what does it: `ScrollingViewBehavior` offsets the content by the bar's height, and
        // there is no way to ask for the offset of a bar that is only visually absent.
        val content = binding.content.root
        (content.layoutParams as CoordinatorLayout.LayoutParams).behavior =
            if (isTransparent) null else AppBarLayout.ScrollingViewBehavior()
        content.requestLayout()

        // The bar's own contents were white for its blue, and the map is a light style.
        val contentColor = if (isTransparent) getColor(R.color.transparent_bar_content) else Color.WHITE
        binding.toolbar.setTitleTextColor(contentColor)

        // Posted, because `setupActionBarWithNavController`'s own destination listener is what puts the
        // navigation icon there and it has not necessarily run yet. `DrawerArrowDrawable` — which is what
        // NavigationUI uses for the up arrow — paints from its own `color` and ignores `setTint`.
        binding.toolbar.post {
            when (val icon = binding.toolbar.navigationIcon) {
                is DrawerArrowDrawable -> icon.color = contentColor
                else -> icon?.mutate()?.setTint(contentColor)
            }
        }

        WindowInsetsControllerCompat(window, binding.root).isAppearanceLightStatusBars = isTransparent
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}

/** The destinations that show a map, and so take the transparent app bar. */
private val MAP_DESTINATIONS = setOf(
    R.id.LevelsFragment,
    R.id.PoisFragment,
    R.id.NavigationFragment,
    R.id.CustomCreditsFragment,
    R.id.ComposeMapFragment
)

/**
 * The same insets with `extra` added to the top of the system-bars inset.
 *
 * `safeDrawing` and friends are derived from `systemBars`, so growing that one grows every inset a widget or
 * a control is likely to read.
 */
private fun WindowInsetsCompat.grownByTopInset(extra: Int): WindowInsetsCompat {
    val bars = getInsets(WindowInsetsCompat.Type.systemBars())
    val grown = Insets.of(bars.left, bars.top + extra, bars.right, bars.bottom)
    return WindowInsetsCompat.Builder(this)
        .setInsets(WindowInsetsCompat.Type.systemBars(), grown)
        .build()
}
