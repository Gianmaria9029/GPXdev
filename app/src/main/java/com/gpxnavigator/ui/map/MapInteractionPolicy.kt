package com.gpxnavigator.ui.map

internal enum class MapGesture { MOVE, SCALE, ROTATE, SHOVE }
internal data class MapInteraction(val zoomed: Boolean, val pauseFollow: Boolean)

/** Native gesture detectors may overlap during a pinch; decide only after they all finish. */
internal class MapInteractionPolicy {
    private val active = mutableSetOf<MapGesture>()
    private val observed = mutableSetOf<MapGesture>()
    val inProgress: Boolean get() = active.isNotEmpty()

    fun begin(gesture: MapGesture) { active += gesture; observed += gesture }
    fun end(gesture: MapGesture) { active -= gesture }

    fun finish(): MapInteraction? {
        if (inProgress || observed.isEmpty()) return null
        val zoomed = MapGesture.SCALE in observed
        val result = MapInteraction(zoomed, pauseFollow = !zoomed)
        observed.clear()
        return result
    }
}
