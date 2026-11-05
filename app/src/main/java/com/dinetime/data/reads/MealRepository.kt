package com.dinetime.data.reads

import com.dinetime.core.SettingsStore
import com.dinetime.data.couchbase.CouchbaseProvider
import com.dinetime.data.couchbase.deleteById
import com.dinetime.data.couchbase.getCommand
import com.dinetime.data.couchbase.getMeal
import com.dinetime.data.couchbase.observeAll
import com.dinetime.data.couchbase.saveCommand
import com.dinetime.data.couchbase.saveMeal
import com.dinetime.domain.CommandDoc
import com.dinetime.domain.CommandState
import com.dinetime.domain.CommandType
import com.dinetime.domain.DocJson
import com.dinetime.domain.Ids
import com.dinetime.domain.MealDoc
import com.dinetime.domain.MealItem
import com.dinetime.domain.MealItems
import com.dinetime.domain.MealPayload
import com.dinetime.domain.MealStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * CQRS facade. Reads come from the reads DB only; every mutation is a command written to the
 * writes DB plus a temporary document in the reads DB so the UI can reflect it offline.
 */
class MealRepository(
    private val db: CouchbaseProvider,
    private val settings: SettingsStore,
) {
    private fun mealsFlow(): Flow<List<MealDoc>> = db.meals.observeAll { json ->
        runCatching { DocJson.decodeFromString(MealDoc.serializer(), json) }.getOrNull()
    }

    fun commandsFlow(): Flow<List<CommandDoc>> = db.commands.observeAll { json ->
        runCatching { DocJson.decodeFromString(CommandDoc.serializer(), json) }.getOrNull()
    }

    fun rawMeals() = mealsFlow()

    fun observeItems(): Flow<List<MealItem>> =
        combine(mealsFlow(), commandsFlow()) { m, c -> MealItems.build(m, c) }

    /** Resolves a meal across its lifecycle: temp id -> overlay temp -> real doc. */
    fun observeItem(id: String): Flow<MealItem?> = observeItems().map { list ->
        list.firstOrNull { it.id == id }
            ?: list.firstOrNull { it.meal.targetDocId == id }
            ?: list.firstOrNull { id.startsWith(Ids.TEMP_PREFIX) && it.meal.lastCommandId == id.removePrefix(Ids.TEMP_PREFIX) }
    }

    fun observeCommands(): Flow<List<CommandDoc>> = commandsFlow().map { it.sortedByDescending { c -> c.createdAt } }

    fun createMeal(payload: MealPayload): String {
        val cmdId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val owner = settings.ownerId
        val temp = tempFrom(Ids.temp(cmdId), payload, owner, cmdId, null, now)
        db.meals.saveMeal(temp)
        db.commands.saveCommand(
            CommandDoc(
                id = cmdId, commandType = CommandType.CREATE_MEAL, payload = payload,
                tempDocId = temp.id, ownerId = owner, deviceId = settings.deviceId, createdAt = now, updatedAt = now,
            ),
        )
        return temp.id
    }

    /** Updates an ACTIVE meal (overlay temp doc) or re-submits a temp doc in ERROR. */
    fun updateMeal(item: MealItem, payload: MealPayload) {
        require(item.canEdit) { "Meal has a request in flight" }
        val cmdId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val owner = settings.ownerId
        if (item.meal.isTemp) {
            item.commandId?.let { old -> db.commands.getCommand(old)?.let { db.commands.saveCommand(it.copy(roundTripDone = true, updatedAt = now)) } }
            val target = item.meal.targetDocId
            val temp = tempFrom(item.meal.id, payload, owner, cmdId, target, now)
            db.meals.saveMeal(temp)
            db.commands.saveCommand(
                CommandDoc(
                    id = cmdId,
                    commandType = if (target != null) CommandType.UPDATE_MEAL else CommandType.CREATE_MEAL,
                    payload = payload, tempDocId = temp.id, targetDocId = target,
                    ownerId = owner, deviceId = settings.deviceId, createdAt = now, updatedAt = now,
                ),
            )
        } else {
            val temp = tempFrom(Ids.temp(cmdId), payload, owner, cmdId, item.meal.id, now)
            db.meals.saveMeal(temp)
            db.commands.saveCommand(
                CommandDoc(
                    id = cmdId, commandType = CommandType.UPDATE_MEAL, payload = payload,
                    tempDocId = temp.id, targetDocId = item.meal.id,
                    ownerId = owner, deviceId = settings.deviceId, createdAt = now, updatedAt = now,
                ),
            )
        }
    }

    fun deleteMeal(item: MealItem) {
        if (item.meal.isTemp) {
            discard(item)
            return
        }
        require(item.canEdit || item.status == MealStatus.ERROR)
        val now = System.currentTimeMillis()
        // A failed delete is superseded by the new attempt.
        item.commandId?.let { dismiss(it) }
        val cmdId = UUID.randomUUID().toString()
        db.commands.saveCommand(
            CommandDoc(
                id = cmdId, commandType = CommandType.DELETE_MEAL, targetDocId = item.meal.id,
                ownerId = settings.ownerId, deviceId = settings.deviceId, createdAt = now, updatedAt = now,
            ),
        )
    }

    /** Drops an errored temp doc (reverting to the real doc, if any) and closes its command. */
    fun discard(item: MealItem) {
        if (item.meal.isTemp) db.meals.deleteById(item.meal.id)
        item.commandId?.let { dismiss(it) }
    }

    fun dismiss(commandId: String) {
        db.commands.getCommand(commandId)?.let {
            db.commands.saveCommand(it.copy(roundTripDone = true, updatedAt = System.currentTimeMillis()))
        }
    }

    fun get(id: String) = db.meals.getMeal(id)

    private fun tempFrom(id: String, p: MealPayload, owner: String, cmdId: String, target: String?, now: Long) = MealDoc(
        id = id, title = p.title, mealType = p.mealType, notes = p.notes, scheduledAt = p.scheduledAt,
        kind = p.kind, reminderLeadMinutes = p.reminderLeadMinutes, status = MealStatus.PENDING,
        isTemp = true, clientRef = cmdId, targetDocId = target, ownerId = owner, updatedAt = now,
    )

    // Used by the reconciler.
    internal val provider get() = db
    internal fun isFinal(state: CommandState) = state == CommandState.COMPLETED || state == CommandState.FAILED_USER_ACTION
}
