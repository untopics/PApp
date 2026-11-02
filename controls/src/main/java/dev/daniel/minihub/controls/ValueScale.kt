package dev.daniel.minihub.controls

import kotlin.math.pow

/**
 * Maps slider progress (0 = slider start, 1 = slider end) to a value.
 * Defines both the range and how the value grows along the slider.
 *
 * Must be non-decreasing in progress; the fuzzy-release handling in [ValueSlider] relies on it.
 */
fun interface ValueScale {
    fun valueAt(progress: Float): Float

    companion object {
        /** Even growth from [min] to [max]. */
        fun linear(min: Float, max: Float) = ValueScale { p -> min + (max - min) * p }

        /** Log-scaled track: each equal distance multiplies the value by the same factor. [min] must be > 0. */
        fun logarithmic(min: Float, max: Float): ValueScale {
            require(min > 0f && max > min) { "logarithmic scale needs 0 < min < max" }
            val ratio = max / min
            return ValueScale { p -> min * ratio.pow(p) }
        }

        /** [exponent] > 1: fine control at the start, fast at the end. < 1: the opposite. */
        fun power(min: Float, max: Float, exponent: Float) =
            ValueScale { p -> min + (max - min) * p.pow(exponent) }

        /**
         * Linear segments between (progress, value) points, e.g.
         * `piecewise(0f to 100f, 0.25f to 400f, 1f to 2000f)` = 100→400 over the first quarter,
         * then 400→2000 over the remaining three quarters.
         */
        fun piecewise(vararg points: Pair<Float, Float>): ValueScale {
            require(points.size >= 2) { "piecewise scale needs at least two points" }
            val pts = points.sortedBy { it.first }
            return ValueScale { p ->
                when {
                    p <= pts.first().first -> pts.first().second
                    p >= pts.last().first -> pts.last().second
                    else -> {
                        val i = pts.indexOfFirst { it.first >= p }
                        val (p0, v0) = pts[i - 1]
                        val (p1, v1) = pts[i]
                        v0 + (v1 - v0) * (p - p0) / (p1 - p0)
                    }
                }
            }
        }
    }
}
