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
