package app.routewise.core

import org.junit.Assert.*
import org.junit.Test

class TripRulesTest {
    @Test fun everyLegAppearsExactlyOnceAcrossBatchBoundaries() {
        for (size in 0..1002) {
            val nodes = (0 until size).toList()
            val batches = TripRules.routeBatches(nodes)
            assertEquals(if (size < 2) 0 else (size - 1 + 25) / 26, batches.size)
            assertTrue(batches.all { it.size in 2..27 })
            assertEquals(nodes.zipWithNext(), batches.flatMap { it.zipWithNext() })
        }
    }

    @Test fun movingOneOfAThousandStopsPreservesEveryVisit() {
        val original = (0 until 1000).toList()
        val moved = TripRules.move(original, 999, 0)
        assertEquals(999, moved.first())
        assertEquals(original.toSet(), moved.toSet())
        assertEquals(original, TripRules.move(moved, 0, 999))
        assertEquals(original, (0 until 1000).toList())
    }

    @Test fun coordinatesRejectNonFiniteAndOutOfRangeValues() {
        assertTrue(TripRules.validCoordinates(26.9, 75.8))
        assertTrue(TripRules.validCoordinates(-90.0, 180.0))
        assertFalse(TripRules.validCoordinates(Double.NaN, 0.0))
        assertFalse(TripRules.validCoordinates(0.0, Double.POSITIVE_INFINITY))
        assertFalse(TripRules.validCoordinates(90.1, 0.0))
    }
}
