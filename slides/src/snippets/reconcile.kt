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
