package com.dinetime.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class MealItemsTest {
    private fun meal(id: String, at: Long = 1_000, temp: Boolean = false, ref: String? = null, target: String? = null, last: String? = null,
                     status: MealStatus = if (temp) MealStatus.PENDING else MealStatus.ACTIVE) =
        MealDoc(id = id, title = id, scheduledAt = at, kind = MealKind.PLANNED, status = status, isTemp = temp,
            clientRef = ref, targetDocId = target, lastCommandId = last, ownerId = "u", updatedAt = 0)

    private fun cmd(id: String, type: CommandType = CommandType.CREATE_MEAL, state: CommandState = CommandState.SUBMITTED,
                    target: String? = null, attempt: Int = 0, ack: Long? = null) =
        CommandDoc(id = id, commandType = type, state = state, targetDocId = target, attempt = attempt, ackAt = ack,
            ownerId = "u", createdAt = 0, updatedAt = 0)

    @Test fun tempDocShownUntilRealDocArrives() {
        val temp = meal(Ids.temp("c1"), temp = true, ref = "c1")
        assertEquals(listOf(temp.id), MealItems.build(listOf(temp), listOf(cmd("c1"))).map { it.id })

        val real = meal(Ids.real("c1"), last = "c1")
        val items = MealItems.build(listOf(temp, real), listOf(cmd("c1", state = CommandState.COMPLETED)))
        assertEquals(listOf(real.id), items.map { it.id })
        assertEquals(MealStatus.ACTIVE, items.single().status)
    }

    @Test fun updateOverlayHidesRealDoc() {
        val real = meal("meal::a", last = "c0")
        val overlay = meal("tmp::c2", temp = true, ref = "c2", target = "meal::a")
        val items = MealItems.build(listOf(real, overlay), listOf(cmd("c2", CommandType.UPDATE_MEAL, target = "meal::a")))
        assertEquals(listOf("tmp::c2"), items.map { it.id })
    }

    @Test fun openDeleteMarksRealDocDeleting() {
        val real = meal("meal::a")
        val items = MealItems.build(listOf(real), listOf(cmd("d1", CommandType.DELETE_MEAL, CommandState.ACKNOWLEDGED, "meal::a")))
        assertTrue(items.single().deleting)
        assertEquals(MealStatus.PROCESSING, items.single().status)
        assertFalse(items.single().canEdit)
    }

    @Test fun statusDerivation() {
        assertEquals(MealStatus.PENDING, MealItems.statusFor(cmd("a")).first)
        assertEquals(MealStatus.PROCESSING, MealItems.statusFor(cmd("a", state = CommandState.ACKNOWLEDGED)).first)
        assertEquals(MealStatus.PENDING, MealItems.statusFor(cmd("a", state = CommandState.FAILED_RETRYABLE)).first)
        assertEquals(MealStatus.PROCESSING, MealItems.statusFor(cmd("a", state = CommandState.FAILED_RETRYABLE, ack = 1)).first)
        val failed = cmd("a", state = CommandState.FAILED_USER_ACTION).copy(error = ErrorInfo(400, "bad"))
        assertEquals(MealStatus.ERROR to failed.error, MealItems.statusFor(failed))
    }

    @Test fun erroredTempCanBeEdited() {
        val t = meal("tmp::c", temp = true, ref = "c", status = MealStatus.ERROR)
        assertTrue(MealItems.build(listOf(t), listOf(cmd("c", state = CommandState.FAILED_USER_ACTION))).single().canEdit)
    }

    @Test fun groupsByDayOfWeekAndFiltersWeek() {
        val zone = ZoneId.of("UTC")
        val monday = LocalDate.of(2026, 11, 2)
        fun at(d: LocalDate) = d.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val items = listOf(
            MealItem(meal("a", at(monday)), MealStatus.ACTIVE),
            MealItem(meal("b", at(monday.plusDays(2))), MealStatus.ACTIVE),
            MealItem(meal("c", at(monday.plusDays(8))), MealStatus.ACTIVE),
        )
        val week = items.filter { MealItems.inWeek(it, monday, zone) }
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), MealItems.groupByDay(week, zone).keys.toList())
    }
}
