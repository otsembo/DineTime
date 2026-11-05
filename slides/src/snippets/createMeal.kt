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
