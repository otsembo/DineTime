package com.dinetime.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.dinetime.ui.components.ConnectedToggleGroup
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinetime.AppContainer
import com.dinetime.domain.MealItem
import com.dinetime.domain.MealKind
import com.dinetime.domain.MealPayload
import com.dinetime.domain.MealType
import com.dinetime.ui.leadLabel
import com.dinetime.ui.rememberContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class EditorViewModel(private val c: AppContainer, val plan: Boolean, private val mealId: String?) : ViewModel() {
    var item by mutableStateOf<MealItem?>(null)
    var title by mutableStateOf("")
    var mealType by mutableStateOf(MealType.DINNER)
    var notes by mutableStateOf("")
    var date by mutableStateOf(LocalDate.now().let { if (plan) it.plusDays(1) else it })
    var time by mutableStateOf(LocalTime.now().withSecond(0).withNano(0).let { if (plan) LocalTime.of(19, 0) else it })
    var lead by mutableStateOf<Int?>(if (plan) c.settings.settings.value.defaultLeadMinutes else null)
    var errors by mutableStateOf<Map<String, String>>(emptyMap())
    var loaded by mutableStateOf(mealId == null)

    suspend fun load() {
        val id = mealId ?: return
        val i = c.repository.observeItem(id).first() ?: return
        item = i
        val m = i.meal
        title = m.title; mealType = m.mealType; notes = m.notes; lead = m.reminderLeadMinutes
        val dt = Instant.ofEpochMilli(m.scheduledAt).atZone(ZoneId.systemDefault())
        date = dt.toLocalDate(); time = dt.toLocalTime()
        // Surface server-reported field errors for corrective action.
        errors = i.error?.fields.orEmpty()
        loaded = true
    }

    private fun scheduledAt() = LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Returns true when the command was queued. */
    suspend fun submit(): Boolean {
        val e = buildMap {
            if (title.isBlank()) put("title", "Title is required")
            val at = scheduledAt()
            if (plan && at <= System.currentTimeMillis()) put("scheduledAt", "Planned meals must be in the future")
            if (!plan && at > System.currentTimeMillis() + 60_000) put("scheduledAt", "Logged meals can't be in the future")
            lead?.let { if (it <= 0) put("lead", "Reminder must be at least 1 minute") }
        }
        errors = e
        if (e.isNotEmpty()) return false
        val payload = MealPayload(
            title = title.trim(), mealType = mealType, notes = notes.trim(), scheduledAt = scheduledAt(),
            kind = if (plan) MealKind.PLANNED else MealKind.UNPLANNED, reminderLeadMinutes = if (plan) lead else null,
        )
        withContext(Dispatchers.IO) {
            val existing = item
            if (existing == null) c.repository.createMeal(payload) else c.repository.updateMeal(existing, payload)
        }
        return true
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EditorScreen(plan: Boolean, mealId: String?, onDone: () -> Unit) {
    val container = rememberContainer()
    val vm: EditorViewModel = viewModel(key = "editor-$plan-$mealId") { EditorViewModel(container, plan, mealId) }
    LaunchedEffect(Unit) { vm.load() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var customLead by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(if (mealId != null) "Edit meal" else if (plan) "Plan a meal" else "Log a meal") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.item?.error?.let {
                Text("Server said: ${it.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            OutlinedTextField(
                vm.title, { vm.title = it }, label = { Text("What are you eating?") }, singleLine = true,
                isError = "title" in vm.errors, supportingText = { vm.errors["title"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Meal type", style = MaterialTheme.typography.labelLarge)
            ConnectedToggleGroup(MealType.entries.toList(), vm.mealType, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }, { vm.mealType = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDate = true }) {
                    Icon(Icons.Default.CalendarMonth, null); Text("  ${vm.date}")
                }
                OutlinedButton(onClick = { showTime = true }) {
                    Icon(Icons.Default.AccessTime, null); Text("  ${vm.time}")
                }
            }
            vm.errors["scheduledAt"]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            OutlinedTextField(vm.notes, { vm.notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            if (plan) {
                Text("Reminder", style = MaterialTheme.typography.labelLarge)
                val presets = listOf<Int?>(null, 5, 15, 30, 60, 1440)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { p ->
                        FilterChip(vm.lead == p && !customLead, { vm.lead = p; customLead = false }, { Text(if (p == null) "None" else leadLabel(p)) })
                    }
                    FilterChip(customLead, { customLead = true; if (vm.lead == null) vm.lead = 45 }, { Text("Custom") })
                }
                if (customLead) {
                    OutlinedTextField(
                        vm.lead?.toString().orEmpty(), { vm.lead = it.filter(Char::isDigit).take(5).toIntOrNull() },
                        label = { Text("Minutes before meal") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = "lead" in vm.errors, supportingText = { vm.errors["lead"]?.let { Text(it) } },
                    )
                }
            }

            Button(
                onClick = { scope.launch { if (vm.submit()) onDone() } },
                enabled = vm.loaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.item?.meal?.isTemp == true) "Resubmit" else "Save") }
            Text(
                "Saved on this device first. Syncs when a connection is available.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
            )
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = vm.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { vm.date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(st) }
    }
    if (showTime) {
        val st = rememberTimePickerState(vm.time.hour, vm.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { vm.time = LocalTime.of(st.hour, st.minute); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(st) },
        )
    }
}
