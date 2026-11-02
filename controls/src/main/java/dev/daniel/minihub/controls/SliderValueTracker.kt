package dev.daniel.minihub.controls

import kotlin.math.roundToLong

/**
 * Pure (Android-free) value logic behind [ValueSlider].
 *
 * Fuzzy-finger handling:
 * - Hysteresis: the shown value only changes once the finger is [hysteresis] (in progress units)
 *   past the point where the neighbouring value would start. Jitter at a step boundary can't flip it.
 * - Release grace: lifting a finger rolls/shifts it a little. On release the value that was on
 *   screen [releaseGraceMs] before the lift is committed, not whatever the last touch event says.
 *
 * Progress < 0 is the cancel zone (finger still on the button); it shows/commits `null`.
 */
internal class SliderValueTracker(
    private val scale: ValueScale,
    private val step: Float,
    private val hysteresis: Float,
    private val releaseGraceMs: Long,
) {
    var current: Float? = null
        private set

    /** Shown values in order of appearance, as (timeMs, value). */
    private val history = ArrayDeque<Pair<Long, Float?>>()

    private fun valueAt(progress: Float): Float? {
        if (progress < 0f) return null
        val raw = scale.valueAt(progress.coerceAtMost(1f))
        return if (step > 0f) (raw / step).roundToLong() * step else raw
    }

    /** Feeds a finger position; returns the value to show. */
    fun update(progress: Float, timeMs: Long): Float? {
        val c = current
        val lo = valueAt(progress - hysteresis)
        val hi = valueAt(progress + hysteresis)
        val keep = history.isNotEmpty() && (
            c == lo || c == hi || (c != null && lo != null && hi != null && c in lo..hi)
            )
        if (!keep) {
            // Advance to the nearest value outside the band rather than jumping to the finger,
            // so steps finer than the band are still shown one by one instead of skipped.
            val next = when {
                lo != null && (c == null || lo > c) -> lo
                c != null && hi != null && hi < c -> hi
                else -> valueAt(progress)
            }
            if (history.isEmpty() || next != c) {
                current = next
                history.addLast(timeMs to next)
                trim(timeMs)
            }
        }
        return current
    }

    /** Value to commit for a lift at [timeMs]; `null` = cancelled. */
    fun release(timeMs: Long): Float? {
        val cutoff = timeMs - releaseGraceMs
        return (history.lastOrNull { it.first <= cutoff } ?: history.firstOrNull())?.second
    }

    private fun trim(now: Long) {
        // Keep everything inside the grace window plus the last entry before it.
        while (history.size > 1 && history[1].first <= now - releaseGraceMs) history.removeFirst()
    }
}
