package app.routewise.core

import app.routewise.data.Destination

object JaipurSimulation {
    private val junction = Destination(
        label = "Jaipur Junction",
        latitude = 26.9207888,
        longitude = 75.7866539,
        osmSourceId = "N/2669084994",
        osmCategory = "railway:station",
    )

    fun trip() = TransferTrip(
        title = "Jaipur 3-stop simulation",
        timezoneId = "Asia/Kolkata",
        days = listOf("Jaipur Old City"),
        dayDetails = listOf(TransferDayDetails(start = junction, finish = junction)),
        stops = listOf(
            TransferStop(
                Destination("Hawa Mahal", latitude = 26.923932, longitude = 75.8268652,
                    osmSourceId = "N/542886858", osmCategory = "tourism:attraction"),
                day = 0, durationMinutes = 60,
            ),
            TransferStop(
                Destination("City Palace", latitude = 26.9262096, longitude = 75.8238346,
                    osmSourceId = "W/455720627", osmCategory = "historic:castle"),
                day = 0, durationMinutes = 120,
            ),
            TransferStop(
                Destination("Jantar Mantar", latitude = 26.9247694, longitude = 75.8245002,
                    osmSourceId = "W/102879535", osmCategory = "tourism:attraction"),
                day = 0, durationMinutes = 60,
            ),
        ),
    )
}
