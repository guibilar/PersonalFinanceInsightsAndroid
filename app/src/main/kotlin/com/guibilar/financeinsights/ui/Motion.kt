package com.guibilar.financeinsights.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination

/**
 * How the app moves between pages (Material motion):
 * - **Deeper and back** (a list to an item, Back): the shared X axis. The new page slides in from the side it comes
 *   from while the old one slides the other way; the old page fades out *before* the new one fades in, so two pages
 *   never show through each other.
 * - **Sideways** (another tab of the bar, or a page of another tab): fade through. The old page fades out quickly, then
 *   the new one fades in and grows slightly into place.
 *
 * Back with the predictive gesture plays the "back" half under the finger.
 */
object Motion {
    /** Material's emphasized easing: quick off the mark, gentle on arrival. */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    const val AXIS_MS = 300
    const val OUT_MS = 90
    const val THROUGH_IN_MS = 210

    /** How far a page travels on the X axis. */
    val AxisOffset = 48.dp
}

/** Which tab of the bar a page belongs to (an index), or -1 for none. */
internal typealias TabOf = (NavDestination) -> Int

private fun Density.axis(): Int = with(this) { Motion.AxisOffset.roundToPx() }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.sideways(tabOf: TabOf): Boolean =
    tabOf(initialState.destination) != tabOf(targetState.destination)

private fun fadeThroughIn(): EnterTransition =
    fadeIn(tween(Motion.THROUGH_IN_MS, delayMillis = Motion.OUT_MS, easing = Motion.EmphasizedDecelerate)) +
        scaleIn(tween(Motion.THROUGH_IN_MS, delayMillis = Motion.OUT_MS, easing = Motion.EmphasizedDecelerate), initialScale = 0.94f)

private fun fadeThroughOut(): ExitTransition = fadeOut(tween(Motion.OUT_MS, easing = Motion.EmphasizedAccelerate))

/** The four transitions of the navigation host, from [tabOf] and the screen's [density]. */
internal class PageTransitions(private val tabOf: TabOf, private val density: Density) {
    private fun axisIn(fromRight: Boolean): EnterTransition {
        val offset = density.axis()
        return slideInHorizontally(tween(Motion.AXIS_MS, easing = Motion.EmphasizedDecelerate)) { if (fromRight) offset else -offset } +
            fadeIn(tween(Motion.AXIS_MS - Motion.OUT_MS, delayMillis = Motion.OUT_MS, easing = Motion.EmphasizedDecelerate))
    }

    private fun axisOut(toLeft: Boolean): ExitTransition {
        val offset = density.axis()
        return slideOutHorizontally(tween(Motion.AXIS_MS, easing = Motion.EmphasizedDecelerate)) { if (toLeft) -offset else offset } +
            fadeOut(tween(Motion.OUT_MS, easing = Motion.EmphasizedAccelerate))
    }

    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (sideways(tabOf)) fadeThroughIn() else axisIn(fromRight = true)
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (sideways(tabOf)) fadeThroughOut() else axisOut(toLeft = true)
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (sideways(tabOf)) fadeThroughIn() else axisIn(fromRight = false)
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (sideways(tabOf)) fadeThroughOut() else axisOut(toLeft = false)
    }
}
