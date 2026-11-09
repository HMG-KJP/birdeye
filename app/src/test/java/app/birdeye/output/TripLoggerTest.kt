package app.birdeye.output

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.birdeye.core.EntryKind
import app.birdeye.core.TripEntry
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripLoggerTest {
    private lateinit var db: TripDatabase
    private lateinit var logger: TripLogger

    private val siren = TripEntry(EntryKind.SIREN, 100, "뒤쪽에서 사이렌 · 약 20초", "테헤란로")
    private val talk = TripEntry(EntryKind.TALK, 200, "조수석: \"저기\"", null)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TripDatabase::class.java).allowMainThreadQueries().build()
        logger = TripLogger(db.tripDao())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `nothing is restored when no trip has happened`() = runTest {
        assertNull(logger.restoreLastTrip())
    }

    @Test
    fun `a trip and its entries come back in order after a restart`() = runTest {
        logger.tripStarted(10)
        logger.log(siren)
        logger.log(talk)

        val trip = logger.restoreLastTrip()

        assertEquals(10L, trip?.startedAt)
        assertEquals(listOf(siren, talk), trip?.entries)
    }

    @Test
    fun `a trip cut short ends at its last recorded moment so its length stops growing`() = runTest {
        logger.tripStarted(10)
        logger.log(siren)

        val trip = logger.restoreLastTrip()

        assertEquals(100L, trip?.endedAt)
        assertEquals(false, trip?.active)
    }

    @Test
    fun `the end time and the distance are kept`() = runTest {
        logger.tripStarted(10)
        logger.tripEnded(500, distanceMeters = 2_100.0)

        val trip = logger.restoreLastTrip()

        assertEquals(500L, trip?.endedAt)
        assertEquals(2_100.0, trip?.distanceMeters ?: 0.0, 0.001)
    }

    @Test
    fun `only the latest trip is restored and older ones are cleared`() = runTest {
        logger.tripStarted(10)
        logger.log(siren)
        logger.tripEnded(50, 0.0)
        logger.tripStarted(100)
        logger.log(talk)

        assertEquals(listOf(talk), logger.restoreLastTrip()?.entries)
        assertEquals(1, db.tripDao().tripCount())
    }

    @Test
    fun `an entry with no trip open is ignored`() = runTest {
        logger.log(siren)

        assertNull(logger.restoreLastTrip())
    }
}
