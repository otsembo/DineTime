package com.dinetime.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.dinetime.AppContainer
import com.dinetime.DineTimeApp
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable data object HomeKey : NavKey
@Serializable data class EditorKey(val plan: Boolean, val mealId: String? = null) : NavKey
@Serializable data class DetailKey(val mealId: String) : NavKey
@Serializable data object RemindersKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object DevKey : NavKey

@Composable
fun rememberContainer(): AppContainer =
    (LocalContext.current.applicationContext as DineTimeApp).container

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateFmt = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
private val fullFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.getDefault())

fun formatTime(ms: Long): String = timeFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun formatDate(ms: Long): String = dateFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
fun formatFull(ms: Long): String = fullFmt.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

fun leadLabel(minutes: Int?): String = when {
    minutes == null -> "No reminder"
    minutes % 1440 == 0 -> "${minutes / 1440} day(s) before"
    minutes % 60 == 0 -> "${minutes / 60} hour(s) before"
    else -> "$minutes min before"
}
