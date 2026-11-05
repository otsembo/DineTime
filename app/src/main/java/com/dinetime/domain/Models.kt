package com.dinetime.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val DocJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = true
}

@Serializable
enum class MealStatus { PENDING, PROCESSING, ACTIVE, ERROR }

@Serializable
enum class MealKind { UNPLANNED, PLANNED }

@Serializable
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

@Serializable
data class ErrorInfo(
    val code: Int,
    val message: String,
    val fields: Map<String, String> = emptyMap(),
)

/** Document stored in the reads database (temporary client docs and real server docs). */
@Serializable
data class MealDoc(
    val id: String,
    val type: String = "meal",
    val title: String,
    val mealType: MealType = MealType.DINNER,
    val notes: String = "",
    val scheduledAt: Long,
    val kind: MealKind,
    val reminderLeadMinutes: Int? = null,
    val status: MealStatus = MealStatus.ACTIVE,
    val isTemp: Boolean = false,
    /** For temp docs: id of the latest command that backs this doc. */
    val clientRef: String? = null,
    /** For temp docs overlaying an existing real doc (update flow). */
    val targetDocId: String? = null,
    /** For real docs: id of the command that last produced this revision. */
    val lastCommandId: String? = null,
    val error: ErrorInfo? = null,
    val ownerId: String,
    val updatedAt: Long,
)

@Serializable
enum class CommandType { CREATE_MEAL, UPDATE_MEAL, DELETE_MEAL }

@Serializable
enum class CommandState { SUBMITTED, ACKNOWLEDGED, COMPLETED, FAILED_RETRYABLE, FAILED_USER_ACTION }

@Serializable
data class MealPayload(
    val title: String = "",
    val mealType: MealType = MealType.DINNER,
    val notes: String = "",
    val scheduledAt: Long = 0,
    val kind: MealKind = MealKind.UNPLANNED,
    val reminderLeadMinutes: Int? = null,
)

/** Document stored in the writes database. */
@Serializable
data class CommandDoc(
    val id: String,
    val type: String = "command",
    val commandType: CommandType,
    val payload: MealPayload = MealPayload(),
    val tempDocId: String? = null,
    val targetDocId: String? = null,
    val state: CommandState = CommandState.SUBMITTED,
    val ackAt: Long? = null,
    val realDocId: String? = null,
    val error: ErrorInfo? = null,
    val attempt: Int = 0,
    /** Set by the client once the reads document round trip is complete. */
    val roundTripDone: Boolean = false,
    val ownerId: String,
    /** Install that created the command; only that install's simulator may process it. */
    val deviceId: String = "",
    val handledBy: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

object Ids {
    const val TEMP_PREFIX = "tmp::"
    fun temp(commandId: String) = "$TEMP_PREFIX$commandId"
    fun real(commandId: String) = "meal::$commandId"
}
