package app.routewise.data

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val timezoneId: String = "Asia/Kolkata",
    val revision: Long = 0,
    val progressRevision: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "trip_day",
    foreignKeys = [ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["tripId", "ordinal"], unique = true)],
)
data class DayEntity(
    @PrimaryKey val id: String = newId(),
    val tripId: String,
    val ordinal: Int,
    val title: String = "",
    val localDate: String? = null,
    @Embedded(prefix = "start_") val start: Destination? = null,
    @Embedded(prefix = "finish_") val finish: Destination? = null,
)

@Serializable
data class Destination(
    val label: String,
    val query: String? = null,
    val placeId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val osmSourceId: String? = null,
    val osmCategory: String? = null,
)

@Entity(
    tableName = "stop",
    foreignKeys = [
        ForeignKey(TripEntity::class, ["id"], ["tripId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(DayEntity::class, ["id"], ["dayId"], onDelete = ForeignKey.NO_ACTION),
    ],
    indices = [Index(value = ["tripId", "dayId", "position"], unique = true), Index("dayId")],
)
data class StopEntity(
    @PrimaryKey val id: String = newId(),
    val tripId: String,
    val dayId: String?,
    val position: Int,
    @Embedded(prefix = "destination_") val destination: Destination,
    val durationMinutes: Int = 60,
    val mustVisit: Boolean = true,
    val notes: String = "",
    val visitState: String = "PLANNED",
)

data class TripSummary(
    @Embedded val trip: TripEntity,
    val stopCount: Int,
    val dayCount: Int,
    val visitedCount: Int,
)

data class TripDetail(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val days: List<DayEntity>,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<StopEntity>,
)

@Dao
interface TripDao {
    @Query("SELECT trip.*, (SELECT COUNT(*) FROM stop WHERE tripId=trip.id) AS stopCount, (SELECT COUNT(*) FROM trip_day WHERE tripId=trip.id) AS dayCount, (SELECT COUNT(*) FROM stop WHERE tripId=trip.id AND visitState='VISITED') AS visitedCount FROM trip ORDER BY updatedAt DESC")
    fun observeTrips(): Flow<List<TripSummary>>

    @Transaction @Query("SELECT * FROM trip WHERE id=:id")
    fun observeTrip(id: String): Flow<TripDetail?>

    @Transaction @Query("SELECT * FROM trip WHERE id=:id")
    suspend fun detail(id: String): TripDetail?

    @Query("SELECT * FROM trip_day WHERE id=:id") suspend fun day(id: String): DayEntity?
    @Query("SELECT * FROM stop WHERE id=:id") suspend fun stop(id: String): StopEntity?
    @Query("SELECT * FROM stop WHERE tripId=:tripId AND dayId IS :dayId ORDER BY position")
    suspend fun ordered(tripId: String, dayId: String?): List<StopEntity>
    @Insert suspend fun insertTrip(trip: TripEntity)
    @Insert suspend fun insertDays(days: List<DayEntity>)
    @Insert suspend fun insertStops(stops: List<StopEntity>)
    @Update suspend fun updateStop(stop: StopEntity)
    @Update suspend fun updateDay(day: DayEntity)
    @Query("UPDATE stop SET position=position+:offset WHERE tripId=:tripId AND dayId IS :dayId")
    suspend fun offsetPositions(tripId: String, dayId: String?, offset: Int)
    @Query("UPDATE trip SET revision=revision+1, updatedAt=:now WHERE id=:id")
    suspend fun touch(id: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE trip SET progressRevision=progressRevision+1, updatedAt=:now WHERE id=:id")
    suspend fun touchProgress(id: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE trip SET title=:title, revision=revision+1, updatedAt=:now WHERE id=:id")
    suspend fun rename(id: String, title: String, now: Long = System.currentTimeMillis())
    @Query("DELETE FROM stop WHERE id=:id") suspend fun deleteStop(id: String)
    @Query("DELETE FROM trip_day WHERE id=:id") suspend fun deleteDay(id: String)
    @Query("DELETE FROM trip WHERE id=:id") suspend fun deleteTrip(id: String)
}

@Database(entities = [TripEntity::class, DayEntity::class, StopEntity::class], version = 4, exportSchema = true)
abstract class TripDatabase : RoomDatabase() {
    abstract fun trips(): TripDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS stop_inbox_position")
                database.execSQL("ALTER TABLE stop ADD COLUMN destination_osmCategory TEXT")
                database.execSQL("ALTER TABLE trip_day ADD COLUMN start_osmCategory TEXT")
                database.execSQL("ALTER TABLE trip_day ADD COLUMN finish_osmCategory TEXT")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS stop_inbox_position")
                database.execSQL("ALTER TABLE trip_day ADD COLUMN localDate TEXT")
                for (prefix in listOf("start", "finish")) {
                    for (field in listOf("label", "query", "placeId", "osmSourceId")) {
                        database.execSQL("ALTER TABLE trip_day ADD COLUMN ${prefix}_${field} TEXT")
                    }
                    for (field in listOf("latitude", "longitude")) {
                        database.execSQL("ALTER TABLE trip_day ADD COLUMN ${prefix}_${field} REAL")
                    }
                }
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP INDEX IF EXISTS stop_inbox_position")
                database.execSQL("ALTER TABLE stop ADD COLUMN destination_osmSourceId TEXT")
            }
        }
        val INBOX_CONSTRAINT = object : Callback() {
            override fun onOpen(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS stop_inbox_position ON stop(tripId, position) WHERE dayId IS NULL")
            }
        }
    }
}
