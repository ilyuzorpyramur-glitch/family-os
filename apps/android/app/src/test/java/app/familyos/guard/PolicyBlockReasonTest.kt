package app.familyos.guard

import app.familyos.guard.data.Policy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalTime

class PolicyBlockReasonTest {

    private fun policy(
        limit: Int = 60,
        used: Int = 0,
        from: String = "21:00",
        to: String = "07:00",
    ) = Policy(
        childName = "Тест",
        limitMinutes = limit,
        usedMinutes = used,
        remainMinutes = (limit - used).coerceAtLeast(0),
        bedtimeStart = from,
        bedtimeEnd = to,
        webFilter = false,
    )

    @Test
    fun limitBlocksEvenDuringDay() {
        assertEquals("limit", policy(limit = 60, used = 60).blockReasonAt(LocalTime.of(12, 0)))
        assertEquals("limit", policy(limit = 60, used = 90).blockReasonAt(LocalTime.of(12, 0)))
    }

    @Test
    fun zeroLimitMeansNoLimit() {
        assertNull(policy(limit = 0, used = 0).blockReasonAt(LocalTime.of(12, 0)))
    }

    @Test
    fun overnightBedtimeBlocksBeforeMidnight() {
        assertEquals("bedtime", policy(from = "21:00", to = "07:00").blockReasonAt(LocalTime.of(21, 55)))
        assertEquals("bedtime", policy(from = "21:00", to = "07:00").blockReasonAt(LocalTime.of(3, 0)))
        assertNull(policy(from = "21:00", to = "07:00").blockReasonAt(LocalTime.of(12, 0)))
    }

    @Test
    fun bedtimeBoundaries() {
        assertEquals("bedtime", policy(from = "21:00", to = "07:00").blockReasonAt(LocalTime.of(21, 0)))
        assertNull(policy(from = "21:00", to = "07:00").blockReasonAt(LocalTime.of(7, 0)))
    }

    @Test
    fun daytimeBedtimeWindow() {
        assertEquals("bedtime", policy(from = "13:00", to = "15:00").blockReasonAt(LocalTime.of(14, 0)))
        assertNull(policy(from = "13:00", to = "15:00").blockReasonAt(LocalTime.of(15, 0)))
    }
}
