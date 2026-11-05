@Serializable data object HomeKey : NavKey
@Serializable data class EditorKey(val plan: Boolean, val mealId: String? = null) : NavKey
@Serializable data class DetailKey(val mealId: String) : NavKey
@Serializable data object RemindersKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object DevKey : NavKey
