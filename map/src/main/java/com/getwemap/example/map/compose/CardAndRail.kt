package com.getwemap.example.map.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp

/**
 * A card along the top edge and a rail centred on the end edge, laid out so that the two never overlap.
 *
 * The rail stays where it is. The card takes the full width it is given, and gives up the rail's column — the
 * rail's width plus [spacing] — only when, at that width, its bottom would reach the rail's top. That happens on
 * a short screen in either orientation: on a 360×640dp phone the itinerary form's card reaches the levels rail
 * at rest, and on a 320dp-wide one as soon as a focused field grows it. On a large phone nothing moves.
 *
 * This is the sample's arrangement, not the SDK's — the SDK ships no container for its widgets, and a screen
 * with other chrome would choose differently.
 *
 * @param card the card, centred at the top of the width it is left. Nothing is placed when it emits nothing.
 * @param rail the rail, centred on the end edge. A rail that measures zero wide leaves the card its full width.
 * @param spacing the gap kept between the card and the rail's column when the card has to give it up.
 * @param modifier the modifier to apply to the layout, which is the area both are placed in.
 */
@Composable
fun CardAndRail(
    card: @Composable () -> Unit,
    rail: @Composable () -> Unit,
    spacing: Dp,
    modifier: Modifier = Modifier
) {
    Layout(contents = listOf(card, rail), modifier = modifier) { (cardMeasurables, railMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placedRail = railMeasurables.firstOrNull()?.measure(loose)
        val railWidth = placedRail?.width ?: 0
        val railTop = placedRail?.let { (constraints.maxHeight - it.height) / 2 } ?: constraints.maxHeight
        val column = railWidth + spacing.roundToPx()

        // Asked rather than measured, because a measurable is measured once per pass and this answer decides the
        // width the card is measured at. The rail cannot be asked the same — `LazyColumn` has no intrinsics —
        // which is why it is the one measured first.
        val cardMeasurable = cardMeasurables.firstOrNull()
        val reachesRail = railWidth > 0 &&
            cardMeasurable != null &&
            cardMeasurable.maxIntrinsicHeight(constraints.maxWidth) + spacing.roundToPx() > railTop
        val cardWidth = if (reachesRail) constraints.maxWidth - column else constraints.maxWidth
        val placedCard = cardMeasurable?.measure(loose.copy(maxWidth = cardWidth))

        layout(constraints.maxWidth, constraints.maxHeight) {
            placedCard?.placeRelative((cardWidth - placedCard.width) / 2, 0)
            placedRail?.placeRelative(constraints.maxWidth - railWidth, railTop)
        }
    }
}
