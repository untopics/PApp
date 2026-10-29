package dev.daniel.minihub.core

import dev.daniel.minihub.apps.calories.CalorieApp
import dev.daniel.minihub.apps.sports.SportsApp
import dev.daniel.minihub.launcher.GridLauncher
import dev.daniel.minihub.launcher.RingLauncher

/** Add new mini apps / launchers here. Order = display order. */
object Registry {
    val apps: List<MiniApp> = listOf(CalorieApp, SportsApp)
    val launchers: List<Launcher> = listOf(GridLauncher, RingLauncher)
}
