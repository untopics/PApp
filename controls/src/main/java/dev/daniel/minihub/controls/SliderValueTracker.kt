package dev.daniel.minihub.controls

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.math.sign

/**
 * Pure (Android-free) value logic behind [ValueSlider].
 *
 * The shown value follows the finger directly (no hysteresis, no lag), at a finer resolution
 * than the step: [displayStep], e.g. 10.01 → 10.02 → 10.03 for a step of 0.1. The value that
 * gets committed is the shown value rounded to the nearest multiple of [step] (half away from
 * zero): 10.03 → 10.0, 10.05 → 10.1. So where the finger stops always predicts the result.
 *
 * Release grace: lifting a finger rolls/shifts it a little. On release the value that was on
 * screen [releaseGraceMs] before the lift is committed, not whatever the last touch event says.
 * 0 disables it.
 *
 * A `null` raw value is the cancel zone (finger still on the button); it shows/commits `null`.
 */
internal class SliderValueTracker(
    step: Float,
    displayStep: Float,
    private val releaseGraceMs: Long,
) {
    private val step = step.toDouble()
    private val fine = displayStep.toDouble()

    /** Display ticks per step when the step is a whole multiple of the display step, else 0. */
    private val ratio: Long = if (this.step > 0.0 && fine > 0.0) {
        val r = this.step / fine
        r.roundToLong().takeIf { it > 0 && abs(r - it) < 1e-3 } ?: 0L
    } else 0L

    /** Fine value on screen; `null` = cancel zone. */
    var shown: Float? = null
        private set

    /** [shown] rounded to the step: what a lift right now would commit. */
    var committed: Float? = null
        private set

    private var shownKey: Long? = null
    private var started = false

    /** Committed values in order of appearance, as (timeMs, value). */
    private val history = ArrayDeque<Pair<Long, Float?>>()

    /** Feeds the raw value under the finger (`null` = cancel zone). */
    fun update(raw: Float?, timeMs: Long) {
        val key: Long?
        val fineValue: Double?
        if (raw == null) {
            key = null
            fineValue = null
        } else if (fine > 0.0) {
            val ticks = (raw / fine).roundToLong()
            key = ticks
            fineValue = ticks * fine
        } else {
            key = raw.toRawBits().toLong()
            fineValue = raw.toDouble()
        }
        if (started && key == shownKey) return
        started = true
        shownKey = key
        shown = fineValue?.toFloat()
        committed = when {
            fineValue == null -> null
            ratio > 0L -> stepFromTicks(key!!)
            else -> toStep(fineValue)
        }
        history.addLast(timeMs to committed)
        trim(timeMs)
    }

    /** Value to commit for a lift at [timeMs]; `null` = cancelled. */
    fun release(timeMs: Long): Float? {
        val cutoff = timeMs - releaseGraceMs
        return (history.lastOrNull { it.first <= cutoff } ?: history.firstOrNull())?.second
    }

    /** Exact half-away-from-zero rounding on integer ticks: 1005 ticks of 0.01 → 101 steps of 0.1. */
    private fun stepFromTicks(ticks: Long): Float {
        val n = (2 * abs(ticks) + ratio) / (2 * ratio)
        return (sign(ticks.toDouble()) * n * step).toFloat()
    }

    private fun toStep(v: Double): Float {
        if (step <= 0.0) return v.toFloat()
        // Epsilon so an exact half (10.05 / 0.1 = 100.4999…) rounds up as displayed.
        val n = floor(abs(v) / step + 0.5 + 1e-6)
        return (sign(v) * n * step).toFloat()
    }

    private fun trim(now: Long) {
        // Keep everything inside the grace window plus the last entry before it.
        while (history.size > 1 && history[1].first <= now - releaseGraceMs) history.removeFirst()
    }

    companion object {
        /** One decimal place finer than [step]: 0.1 → 0.01, 1 → 0.1, 25 → 1. 0 for step 0. */
        fun defaultDisplayStep(step: Float): Float =
            if (step <= 0f) 0f else 10.0.pow(floor(log10(step.toDouble()) + 1e-6) - 1).toFloat()

        /** Decimal places needed to show values on a [displayStep] grid. */
        fun decimalsFor(displayStep: Float): Int =
            if (displayStep <= 0f) 2 else ceil(-log10(displayStep.toDouble()) - 1e-6).toInt().coerceAtLeast(0)
    }
}
