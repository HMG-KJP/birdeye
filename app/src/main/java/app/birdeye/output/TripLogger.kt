package app.birdeye.output

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import app.birdeye.core.EntryKind
import app.birdeye.core.TripEntry
import app.birdeye.core.TripSummary

@Entity(tableName = "trips")
data class TripRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long? = null,
    val distanceMeters: Double = 0.0,
)

@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = TripRow::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId")],
)
data class EntryRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val kind: String,
    val at: Long,
    val text: String,
    val place: String?,
)

@Dao
abstract class TripDao {
    @Insert
    abstract suspend fun insertTrip(trip: TripRow): Long

    @Insert
    abstract suspend fun insertEntry(entry: EntryRow)

    @Query("SELECT * FROM trips ORDER BY id DESC LIMIT 1")
    abstract suspend fun latestTrip(): TripRow?

    @Query("SELECT * FROM entries WHERE tripId = :tripId ORDER BY at, id")
    abstract suspend fun entriesOf(tripId: Long): List<EntryRow>

    @Query("UPDATE trips SET endedAt = :endedAt, distanceMeters = :distanceMeters WHERE id = :tripId")
    abstract suspend fun finishTrip(tripId: Long, endedAt: Long, distanceMeters: Double)

    @Query("DELETE FROM trips")
    abstract suspend fun clearTrips()

    @Query("SELECT COUNT(*) FROM trips")
    abstract suspend fun tripCount(): Int

    /** The recap only ever covers the latest trip, so older ones are not kept. */
    @Transaction
    open suspend fun replaceWithNewTrip(startedAt: Long): Long {
        clearTrips()
        return insertTrip(TripRow(startedAt = startedAt))
    }
}

@Database(entities = [TripRow::class, EntryRow::class], version = 1, exportSchema = false)
abstract class TripDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
}

/** Keeps the latest trip across app restarts so the recap survives them. */
class TripLogger(private val dao: TripDao) {
    suspend fun tripStarted(at: Long) {
        dao.replaceWithNewTrip(at)
    }

    suspend fun log(entry: TripEntry) {
        val trip = dao.latestTrip() ?: return
        if (trip.endedAt != null) return
        dao.insertEntry(EntryRow(tripId = trip.id, kind = entry.kind.name, at = entry.at, text = entry.text, place = entry.place))
    }

    suspend fun tripEnded(at: Long, distanceMeters: Double) {
        val trip = dao.latestTrip() ?: return
        dao.finishTrip(trip.id, at, distanceMeters)
    }

    /**
     * A trip cut short by the app being killed has no end time; its last recorded moment stands in
     * for one so the recap's duration does not keep growing.
     */
    suspend fun restoreLastTrip(): TripSummary? {
        val trip = dao.latestTrip() ?: return null
        val entries = dao.entriesOf(trip.id).map { row ->
            TripEntry(EntryKind.valueOf(row.kind), row.at, row.text, row.place)
        }
        return TripSummary(
            startedAt = trip.startedAt,
            endedAt = trip.endedAt ?: entries.lastOrNull()?.at ?: trip.startedAt,
            active = false,
            distanceMeters = trip.distanceMeters,
            entries = entries,
        )
    }
}
