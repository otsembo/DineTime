package com.dinetime.fakeserver

import com.dinetime.core.SettingsStore
import com.dinetime.data.couchbase.MEALS
import com.dinetime.data.couchbase.MEAL_COMMANDS
import com.dinetime.domain.CommandDoc
import com.dinetime.domain.CommandState
import com.dinetime.domain.CommandType
import com.dinetime.domain.DocJson
import com.dinetime.domain.ErrorInfo
import com.dinetime.domain.Ids
import com.dinetime.domain.MealDoc
import com.dinetime.domain.MealKind
import com.dinetime.domain.MealStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class SimulatorControls(
    val latencyMs: Long = 1200,
    val paused: Boolean = false,
    val fail500Next: Int = 0,
    val fail400Next: Boolean = false,
    val dropNextAck: Boolean = false,
    val persist500: Boolean = false,
    val persist400: Boolean = false,
)

data class SimulatorStatus(val running: Boolean = false, val lastError: String? = null, val foreignIgnored: Int = 0)

/** Failure injection captured when a command is first accepted, so retries and re-polls behave deterministically. */
private class InjectionPlan(var fail500: Int, var fail400: Boolean)

/**
 * Plays the external processing server: consumes commands from the Sync Gateway writes keyspace,
 * acknowledges them, applies business rules, writes real docs to the reads keyspace, and reports the
 * outcome on the command document. Failure/latency behaviour is driven by [controls].
 * Server-side state (retry bookkeeping, idempotency, slots) is in memory only.
 */
class FakeServer(
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val client = SyncGatewayClient { settings.settings.value }
    private val _controls = MutableStateFlow(SimulatorControls())
    val controls: StateFlow<SimulatorControls> = _controls.asStateFlow()
    private val _status = MutableStateFlow(SimulatorStatus())
    val status: StateFlow<SimulatorStatus> = _status.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val inflight: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private val slots = ConcurrentHashMap<String, Long>()
    private val plans = ConcurrentHashMap<String, InjectionPlan>()
    private val foreign: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())
    private var pollJob: Job? = null

    init {
        scope.launch {
            settings.settings.distinctUntilChangedBy { listOf(it.simulatorEnabled, it.sgHost, it.sgAdminPort, it.sgDatabase, it.adminUser, it.adminPassword) }
                .collect { if (it.simulatorEnabled) start() else stop() }
        }
    }

    fun updateControls(f: (SimulatorControls) -> SimulatorControls) = _controls.update(f)

    fun clearLog() { _log.value = emptyList() }

    @Synchronized
    private fun start() {
        pollJob?.cancel()
        _status.value = SimulatorStatus(running = true)
        foreign.clear()
        pollJob = scope.launch(Dispatchers.IO) {
            var since = "0"
            log("Simulator started")
            while (isActive) {
                try {
                    val res = client.changes(MEAL_COMMANDS, since)
                    since = res.lastSeq
                    res.docs.forEach(::consider)
                    _status.value = SimulatorStatus(running = true, foreignIgnored = foreign.size)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _status.value = SimulatorStatus(running = true, lastError = e.message, foreignIgnored = foreign.size)
                    delay(3000)
                }
                delay(1000)
            }
        }
    }

    @Synchronized
    private fun stop() {
        pollJob?.cancel()
        pollJob = null
        _status.value = SimulatorStatus(running = false)
        log("Simulator stopped")
    }

    private fun consider(doc: JsonObject) {
        val cmd = runCatching { DocJson.decodeFromString(CommandDoc.serializer(), doc.toString()) }.getOrNull() ?: return
        val resumable = cmd.state in setOf(CommandState.SUBMITTED, CommandState.ACKNOWLEDGED, CommandState.FAILED_RETRYABLE)
        if (!resumable || cmd.roundTripDone) return
        val mine = cmd.deviceId == settings.deviceId || (cmd.deviceId.isEmpty() && settings.settings.value.processLegacyCommands)
        if (!mine) { foreign.add(cmd.id); return }
        if (!inflight.add(cmd.id)) return
        scope.launch(Dispatchers.IO) {
            try {
                process(cmd)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                log("! ${cmd.id.take(6)} crashed: ${e.message}")
            } finally {
                plans.remove(cmd.id)
                inflight.remove(cmd.id)
            }
        }
    }

    private suspend fun awaitRunning() {
        while (_controls.value.paused) delay(300)
    }

    private fun isTerminal(c: CommandDoc) = c.roundTripDone || c.state == CommandState.COMPLETED || c.state == CommandState.FAILED_USER_ACTION

    private fun fetchLatest(id: String): CommandDoc? =
        client.get(MEAL_COMMANDS, id)?.let { runCatching { DocJson.decodeFromString(CommandDoc.serializer(), it.toString()) }.getOrNull() }

    private suspend fun process(snapshot: CommandDoc) {
        // The changes feed can be stale: always act on the latest stored revision and never revive a finished command.
        var cmd = fetchLatest(snapshot.id)?.takeIf { !isTerminal(it) } ?: return
        val tag = cmd.id.take(6)
        plans.getOrPut(cmd.id) {
            val c = _controls.value
            val plan = InjectionPlan(
                fail500 = c.fail500Next,
                fail400 = c.fail400Next && cmd.commandType != CommandType.DELETE_MEAL,
            )
            updateControls { it.copy(fail500Next = 0, fail400Next = if (plan.fail400) false else it.fail400Next) }
            plan
        }
        log("← $tag ${cmd.commandType} received")
        awaitRunning()
        delay(_controls.value.latencyMs)

        if (cmd.ackAt == null) {
            if (_controls.value.dropNextAck) {
                updateControls { it.copy(dropNextAck = false) }
                log("✗ $tag ack dropped (injected); continuing silently")
            } else {
                cmd = saveCommand(cmd.copy(state = CommandState.ACKNOWLEDGED, ackAt = now())) ?: return
                log("→ $tag ACKNOWLEDGED")
            }
        }

        var attempt = cmd.attempt
        while (true) {
            awaitRunning()
            delay(_controls.value.latencyMs)
            when (val outcome = execute(cmd)) {
                is Outcome.Ok -> {
                    cmd = saveCommand(cmd.copy(state = CommandState.COMPLETED, realDocId = outcome.realId, error = null, attempt = attempt)) ?: return
                    log("✓ $tag COMPLETED${outcome.realId?.let { " → $it" }.orEmpty()}")
                    return
                }
                is Outcome.ClientError -> {
                    saveCommand(cmd.copy(state = CommandState.FAILED_USER_ACTION, error = outcome.error, attempt = attempt)) ?: return
                    log("✗ $tag ${outcome.error.code}: ${outcome.error.message}")
                    return
                }
                is Outcome.ServerError -> {
                    attempt++
                    cmd = saveCommand(cmd.copy(state = CommandState.FAILED_RETRYABLE, attempt = attempt, error = outcome.error)) ?: return
                    val backoff = minOf(1L shl minOf(attempt, 4), 15L) * 1000
                    log("↻ $tag 500, retry #$attempt in ${backoff / 1000}s")
                    delay(backoff)
                }
            }
        }
    }

    private sealed interface Outcome {
        data class Ok(val realId: String?) : Outcome
        data class ClientError(val error: ErrorInfo) : Outcome
        data class ServerError(val error: ErrorInfo) : Outcome
    }

    private fun execute(cmd: CommandDoc): Outcome {
        val c = _controls.value
        val plan = plans[cmd.id]
        if (plan != null && plan.fail500 > 0) {
            plan.fail500--
            return Outcome.ServerError(ErrorInfo(500, "Injected internal server error"))
        }
        if (c.persist500) return Outcome.ServerError(ErrorInfo(500, "Injected internal server error (persistent)"))
        if ((plan?.fail400 == true || c.persist400) && cmd.commandType != CommandType.DELETE_MEAL) {
            plan?.fail400 = false
            return Outcome.ClientError(ErrorInfo(400, "Injected validation failure", mapOf("title" to "Rejected by simulator")))
        }
        return try {
            when (cmd.commandType) {
                CommandType.CREATE_MEAL -> upsertMeal(cmd, Ids.real(cmd.id))
                CommandType.UPDATE_MEAL -> {
                    val target = cmd.targetDocId ?: return Outcome.ClientError(ErrorInfo(400, "Missing target"))
                    if (client.get(MEALS, target) == null) {
                        return Outcome.ClientError(ErrorInfo(404, "This meal no longer exists on the server"))
                    }
                    upsertMeal(cmd, target)
                }
                CommandType.DELETE_MEAL -> {
                    cmd.targetDocId?.let { client.delete(MEALS, it); slots.remove(it) }
                    Outcome.Ok(null)
                }
            }
        } catch (e: SgHttpException) {
            if (e.code in 400..499 && e.code != 409) Outcome.ClientError(ErrorInfo(e.code, e.message ?: "Rejected"))
            else Outcome.ServerError(ErrorInfo(500, e.message ?: "Server error"))
        } catch (e: java.io.IOException) {
            Outcome.ServerError(ErrorInfo(503, e.message ?: "Unavailable"))
        }
    }

    private fun upsertMeal(cmd: CommandDoc, realId: String): Outcome {
        val p = cmd.payload
        val fields = buildMap {
            if (p.title.isBlank()) put("title", "Title is required")
            if (p.kind == MealKind.PLANNED && p.scheduledAt < System.currentTimeMillis()) {
                put("scheduledAt", "Planned meals must be in the future")
            }
            if (slots.any { (id, at) -> id != realId && at == p.scheduledAt }) {
                put("scheduledAt", "Another meal is already scheduled at this time")
            }
        }
        if (fields.isNotEmpty()) return Outcome.ClientError(ErrorInfo(400, "Validation failed", fields))
        val meal = MealDoc(
            id = realId, title = p.title.trim(), mealType = p.mealType, notes = p.notes,
            scheduledAt = p.scheduledAt, kind = p.kind, reminderLeadMinutes = p.reminderLeadMinutes,
            status = MealStatus.ACTIVE, isTemp = false, lastCommandId = cmd.id,
            ownerId = cmd.ownerId, updatedAt = System.currentTimeMillis(),
        )
        val obj = DocJson.parseToJsonElement(DocJson.encodeToString(MealDoc.serializer(), meal)).jsonObject
        client.upsert(MEALS, realId, obj)
        slots[realId] = p.scheduledAt
        return Outcome.Ok(realId)
    }

    /** Conditional save; returns null (and writes nothing) if the stored command already finished. */
    private fun saveCommand(cmd: CommandDoc): CommandDoc? {
        val updated = cmd.copy(updatedAt = now(), handledBy = settings.deviceId.take(6))
        val obj = DocJson.parseToJsonElement(DocJson.encodeToString(CommandDoc.serializer(), updated)).jsonObject
        repeat(3) {
            val stored = client.get(MEAL_COMMANDS, updated.id) ?: return null
            val latest = runCatching { DocJson.decodeFromString(CommandDoc.serializer(), stored.toString()) }.getOrNull()
            if (latest != null && isTerminal(latest)) return null
            val rev = stored["_rev"]?.jsonPrimitive?.content ?: return null
            if (client.putIfRev(MEAL_COMMANDS, updated.id, obj, rev)) return updated
        }
        return null
    }

    private fun now() = System.currentTimeMillis()

    private fun log(msg: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.update { (listOf("$ts $msg") + it).take(100) }
    }
}
