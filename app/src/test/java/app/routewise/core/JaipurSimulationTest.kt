package app.routewise.core

import org.junit.Assert.*
import org.junit.Test
import app.routewise.maps.MapPoint
import app.routewise.maps.RouteNode

class JaipurSimulationTest {
    @Test fun demoUsesThreeVerifiedAttractionsAndRoundTripAnchor() {
        val trip = JaipurSimulation.trip()
        TripTransfer.validate(trip)
        assertEquals("Jaipur 3-stop simulation", trip.title)
        assertEquals(1, trip.days.size)
        assertEquals(3, trip.stops.size)
        assertTrue(trip.stops.all { it.day == 0 && it.destination.latitude != null && it.destination.longitude != null })
        assertEquals(listOf("Hawa Mahal", "City Palace", "Jantar Mantar"), trip.stops.map { it.destination.label })
        assertTrue(trip.stops.all { it.destination.osmSourceId != null })
        assertEquals("Jaipur Junction", trip.dayDetails.single().start?.label)
        assertEquals(trip.dayDetails.single().start, trip.dayDetails.single().finish)
        val destinations = listOf(trip.dayDetails.single().start!!) + trip.stops.map { it.destination } + trip.dayDetails.single().finish!!
        val nodes = destinations.mapIndexed { index, place -> RouteNode("node-$index", MapPoint(place.latitude!!, place.longitude!!)) }
        assertEquals(5, nodes.size)
        assertEquals(4, TripRules.routeBatches(nodes).sumOf { it.size - 1 })
    }
}
