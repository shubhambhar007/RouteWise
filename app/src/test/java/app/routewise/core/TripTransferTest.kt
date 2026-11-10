package app.routewise.core

import app.routewise.data.Destination
import org.junit.Assert.*
import org.junit.Test

class TripTransferTest {
    private fun trip() = TransferTrip(title = "Weekend", days = listOf("City", "Hills"), stops = List(1000) {
        TransferStop(Destination("Stop $it", query = "Museum $it, Jaipur"), day = it % 2)
    })

    @Test fun thousandStopsRoundTripWithoutLoss() {
        assertEquals(trip(), TripTransfer.decode(TripTransfer.encode(trip())))
        val encoded = TripTransfer.encode(trip())
        assertTrue(encoded.contains("\"version\": 4"))
        assertTrue(encoded.contains("\"format\": \"routewise.local-trip\""))
        assertTrue(encoded.contains("OpenStreetMap contributors"))
    }
    @Test fun missingDayIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { TripTransfer.validate(trip().copy(days = emptyList())) }
    }
    @Test fun destinationsCannotMixProviderIdsAndCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            TripTransfer.validateDestination(Destination("Ambiguous", placeId = "opaque", latitude = 20.0, longitude = 75.0))
        }
    }
    @Test fun navigationUsesEncodedNextStopWithoutAnOrigin() {
        val link = MapsLinks.navigation(Destination("Museum & fort", query = "Museum & fort, Jaipur"))
        assertTrue(link.contains("destination=Museum+%26+fort%2C+Jaipur"))
        assertFalse(link.contains("origin="))
        assertTrue(link.contains("dir_action=navigate"))
    }
    @Test fun supportedSharesRequireExplicitDestinationNotViewport() {
        assertEquals("opaque", MapsLinks.parseShared("Visit https://www.google.com/maps/search/?api=1&query=Museum&query_place_id=opaque").placeId)
        assertTrue(MapsLinks.parseShared("https://www.google.com/maps/@20,70,10z").needsSearch)
        assertTrue(MapsLinks.parseShared("https://maps.app.goo.gl/example").needsSearch)
        assertThrows(IllegalArgumentException::class.java) { MapsLinks.parseShared("https://www.google.com.evil.example/maps/?q=test") }
        assertThrows(IllegalArgumentException::class.java) { MapsLinks.parseShared("https://user@www.google.com/maps/?q=test") }
    }
}
