package com.dinetime.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** What the UI renders: merges real docs, temp docs and in-flight commands. */
data class MealItem(
    val meal: MealDoc,
    val status: MealStatus,
    val error: ErrorInfo? = null,
    val retryAttempt: Int = 0,
    val deleting: Boolean = false,
    val commandId: String? = null,
) {
    val id get() = meal.id
    /** Edits are only safe when no command is in flight for the document. */
    val canEdit get() = !deleting && (status == MealStatus.ACTIVE || (meal.isTemp && status == MealStatus.ERROR))
}

object MealItems {
    fun build(meals: List<MealDoc>, commands: List<CommandDoc>): List<MealItem> {
        val temps = meals.filter { it.isTemp }
        val reals = meals.filter { !it.isTemp }
        val overlaid = temps.mapNotNull { it.targetDocId }.toSet()
        val commandsById = commands.associateBy { it.id }
        val openDeletes = commands
            .filter { it.commandType == CommandType.DELETE_MEAL && !it.roundTripDone && it.targetDocId != null }
            .associateBy { it.targetDocId!! }

        val tempItems = temps
            .filter { t -> reals.none { it.lastCommandId != null && it.lastCommandId == t.clientRef } }
            .map { t ->
                val cmd = t.clientRef?.let(commandsById::get)
                MealItem(
                    meal = t,
                    status = t.status,
                    error = t.error,
                    retryAttempt = if (cmd?.state == CommandState.FAILED_RETRYABLE) cmd.attempt else 0,
                    commandId = t.clientRef,
                )
            }

        val realItems = reals.filter { it.id !in overlaid }.map { r ->
            val del = openDeletes[r.id]
            when {
                del == null -> MealItem(r, MealStatus.ACTIVE)
                del.state == CommandState.FAILED_USER_ACTION ->
                    MealItem(r, MealStatus.ERROR, del.error, deleting = true, commandId = del.id)
                else -> MealItem(
                    r,
                    if (del.state == CommandState.SUBMITTED) MealStatus.PENDING else MealStatus.PROCESSING,
                    retryAttempt = if (del.state == CommandState.FAILED_RETRYABLE) del.attempt else 0,
                    deleting = true,
                    commandId = del.id,
                )
            }
        }
        return (tempItems + realItems).sortedBy { it.meal.scheduledAt }
    }

    fun statusFor(cmd: CommandDoc): Pair<MealStatus, ErrorInfo?> = when (cmd.state) {
        CommandState.SUBMITTED -> MealStatus.PENDING to null
        CommandState.ACKNOWLEDGED, CommandState.COMPLETED -> MealStatus.PROCESSING to null
        CommandState.FAILED_RETRYABLE ->
            (if (cmd.ackAt != null) MealStatus.PROCESSING else MealStatus.PENDING) to null
        CommandState.FAILED_USER_ACTION -> MealStatus.ERROR to cmd.error
    }

    fun groupByDay(items: List<MealItem>, zone: ZoneId = ZoneId.systemDefault()): Map<DayOfWeek, List<MealItem>> =
        items.groupBy { Instant.ofEpochMilli(it.meal.scheduledAt).atZone(zone).dayOfWeek }
            .toSortedMap()
            .mapValues { (_, v) -> v.sortedBy { it.meal.scheduledAt } }

    fun weekStart(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)

    fun inWeek(item: MealItem, weekStart: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        val d = Instant.ofEpochMilli(item.meal.scheduledAt).atZone(zone).toLocalDate()
        return !d.isBefore(weekStart) && d.isBefore(weekStart.plusDays(7))
    }
}
