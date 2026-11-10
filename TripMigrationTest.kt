package app.routewise.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TripMigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), TripDatabase::class.java.canonicalName!!, FrameworkSQLiteOpenHelperFactory())

    @Test fun versionOneTripsSurviveOpenMapMigration() {
        helper.createDatabase("migration-test", 1).apply {
            execSQL("CREATE UNIQUE INDEX stop_inbox_position ON stop(tripId, position) WHERE dayId IS NULL")
            execSQL("INSERT INTO trip VALUES ('trip', 'Existing trip', 'Asia/Kolkata', 4, 1, 1, 1)")
            execSQL("INSERT INTO stop (id,tripId,dayId,position,destination_label,destination_query,destination_placeId,destination_latitude,destination_longitude,durationMinutes,mustVisit,notes,visitState) VALUES ('stop','trip',NULL,0,'My stop','Museum, Jaipur',NULL,NULL,NULL,60,1,'My note','VISITED')")
            close()
        }
        helper.runMigrationsAndValidate("migration-test", 4, true, TripDatabase.MIGRATION_1_2, TripDatabase.MIGRATION_2_3, TripDatabase.MIGRATION_3_4).apply {
            query("SELECT destination_label, notes, visitState, destination_osmSourceId FROM stop").use {
                assertTrue(it.moveToFirst()); assertEquals("My stop", it.getString(0)); assertEquals("My note", it.getString(1))
                assertEquals("VISITED", it.getString(2)); assertTrue(it.isNull(3))
            }
            close()
        }
    }
}
