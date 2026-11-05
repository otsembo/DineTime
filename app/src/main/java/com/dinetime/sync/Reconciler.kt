package com.dinetime.sync

import android.util.Log
import com.dinetime.core.SettingsStore
import com.dinetime.data.couchbase.CouchbaseProvider
import com.dinetime.data.couchbase.deleteById
import com.dinetime.data.couchbase.saveCommand
import com.dinetime.data.couchbase.saveMeal
import com.dinetime.data.reads.MealRepository
import com.dinetime.domain.CommandDoc
import com.dinetime.domain.CommandState
import com.dinetime.domain.CommandType
import com.dinetime.domain.MealDoc
import com.dinetime.domain.MealItems
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Keeps temp reads docs in step with the command lifecycle:
 * PENDING -> PROCESSING (ack replicated back) -> temp deleted when the real doc is replicated.
 * Idempotent, so it also recovers state after app restarts.
 */
class Reconciler(
    private val repo: MealRepository,
    private val db: CouchbaseProvider,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    init {
        scope.launch(dispatcher) {
            combine(repo.rawMeals(), repo.commandsFlow()) { m, c -> m to c }
                .flowOn(dispatcher)
                .collect { (m, c) ->
                    runCatching { reconcile(m, c) }.onFailure { Log.e("Reconciler", "failed", it) }
                }
        }
    }

    internal fun reconcile(meals: List<MealDoc>, commands: List<CommandDoc>) {
        val owner = settings.ownerId
        val byId = meals.associateBy { it.id }
        val reals = meals.filter { !it.isTemp }
        for (c in commands) {
            if (c.roundTripDone || c.ownerId != owner) continue
            when (c.commandType) {
                CommandType.CREATE_MEAL, CommandType.UPDATE_MEAL -> reconcileUpsert(c, byId, reals)
                CommandType.DELETE_MEAL ->
                    if (c.state == CommandState.COMPLETED && reals.none { it.id == c.targetDocId }) markDone(c)
            }
        }
    }

    private fun reconcileUpsert(c: CommandDoc, byId: Map<String, MealDoc>, reals: List<MealDoc>) {
        val temp = c.tempDocId?.let(byId::get)
        if (temp == null || temp.clientRef != c.id) {
            if (repo.isFinal(c.state)) markDone(c)
            return
        }
        val real = reals.firstOrNull { it.lastCommandId == c.id }
        if (real != null && c.state == CommandState.COMPLETED) {
            db.meals.deleteById(temp.id)
            markDone(c)
            return
        }
        val (status, error) = MealItems.statusFor(c)
        if (temp.status != status || temp.error != error) {
            db.meals.saveMeal(temp.copy(status = status, error = error))
        }
    }

    private fun markDone(c: CommandDoc) {
        db.commands.saveCommand(c.copy(roundTripDone = true, updatedAt = System.currentTimeMillis()))
    }
}
