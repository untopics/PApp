package dev.daniel.minihub.controls

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * Pure (Android-free) layout and hit-testing behind [Dial].
 *
 * Angles are in degrees, 0 = straight up, clockwise (90 = right, -90 = left).
 *  - Full circle ([sweep] >= 360): item 0 sits at [start], the rest evenly around; each item owns
 *    the sector centered on it.
 *  - Arc: the arc from [start] to [start] + [sweep] is split into equal sectors with an item in the
 *    middle of each, so no item sits exactly on an end. A finger slightly past an end
 *    (up to [endTolerance]) still selects the end item; further out selects nothing.
 */
class DialGeometry(
    val count: Int,
    val start: Float = 0f,
    sweep: Float = 360f,
    private val endTolerance: Float = 30f,
) {
    val full = sweep >= 360f
    val sweep = sweep.coerceIn(1f, 360f)
    val sector: Float = if (count <= 0) 0f else this.sweep / count

    /** Angle of item [i]. */
    fun angleOf(i: Int): Float = if (full) start + sector * i else start + sector * (i + 0.5f)

    /** Middle of the arc (for a full circle: straight down, where the label goes). */
    val labelAngle: Float get() = if (full) start + 180f else start + sweep / 2f

    /** Item at [angle] (degrees, 0 = up, clockwise), or -1. */
    fun indexAt(angle: Float): Int {
        if (count <= 0) return -1
        val rel = norm(angle - start)
        if (full) return ((norm(rel + sector / 2f)) / sector).toInt().coerceIn(0, count - 1)
        if (rel <= sweep) return (rel / sector).toInt().coerceIn(0, count - 1)
        val pastEnd = rel - sweep
        val beforeStart = 360f - rel
        return when {
            pastEnd <= beforeStart && pastEnd <= endTolerance -> count - 1
            beforeStart < pastEnd && beforeStart <= endTolerance -> 0
            else -> -1
        }
    }

    /**
     * Ring radius that leaves at least [spacing] between item centers, between [base] and [maxRadius]
     * (all in the same unit, e.g. dp).
     */
    fun radius(base: Float, maxRadius: Float, spacing: Float): Float {
        if (count <= 0) return base
        val needed = spacing * count / sweepRadians()
        return max(base, min(needed, max(base, maxRadius)))
    }

    /** Item size so neighbours on a ring of [radius] don't overlap: at most [largest], at least [smallest]. */
    fun itemSize(radius: Float, largest: Float, smallest: Float, gap: Float): Float {
        if (count <= 0) return largest
        val arcPerItem = radius * sweepRadians() / count
        return (arcPerItem - gap).coerceIn(smallest, largest)
    }

    private fun sweepRadians() = sweep * PI.toFloat() / 180f

    companion object {
        /** Angle of a vector (screen coordinates, y down): 0 = up, clockwise, in [0, 360). */
        fun angleOf(dx: Float, dy: Float): Float = norm((atan2(dx, -dy) * 180f / PI.toFloat()))

        private fun norm(a: Float): Float {
            val r = a % 360f
            return if (r < 0f) r + 360f else r
        }
    }
}
