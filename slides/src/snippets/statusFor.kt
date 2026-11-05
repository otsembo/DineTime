fun statusFor(cmd: CommandDoc): Pair<MealStatus, ErrorInfo?> = when (cmd.state) {
    CommandState.SUBMITTED -> MealStatus.PENDING to null
    CommandState.ACKNOWLEDGED, CommandState.COMPLETED -> MealStatus.PROCESSING to null
    CommandState.FAILED_RETRYABLE ->
        (if (cmd.ackAt != null) MealStatus.PROCESSING else MealStatus.PENDING) to null
    CommandState.FAILED_USER_ACTION -> MealStatus.ERROR to cmd.error
}
