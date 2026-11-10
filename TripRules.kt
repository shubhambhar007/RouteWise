package app.routewise.core

object TripRules {
    fun <T> move(items: List<T>, from: Int, to: Int): List<T> {
        require(from in items.indices && to in items.indices)
        return items.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun <T> routeBatches(orderedNodes: List<T>): List<List<T>> = buildList {
        var first = 0
        while (first < orderedNodes.lastIndex) {
            val last = minOf(first + 26, orderedNodes.lastIndex)
            add(orderedNodes.subList(first, last + 1).toList())
            first = last
        }
    }

    fun validCoordinates(latitude: Double, longitude: Double): Boolean =
        latitude.isFinite() && longitude.isFinite() &&
            latitude in -90.0..90.0 && longitude in -180.0..180.0
}
