package com.dinetime.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dinetime.AppContainer
import com.dinetime.data.couchbase.SyncState
import com.dinetime.domain.MealItem
import com.dinetime.domain.MealItems
import com.dinetime.domain.MealKind
import com.dinetime.domain.MealStatus
import com.dinetime.ui.components.ConnectedToggleGroup
import com.dinetime.ui.components.StatusBadge
import com.dinetime.ui.components.SyncBar
import com.dinetime.ui.formatTime
import com.dinetime.ui.leadLabel
import com.dinetime.ui.rememberContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

data class HomeState(
    val weekStart: LocalDate = MealItems.weekStart(LocalDate.now()),
    val filter: MealKind? = null,
    val days: List<Pair<DayOfWeek, List<MealItem>>> = emptyList(),
    val sync: SyncState = SyncState(),
    val inFlight: Int = 0,
)

class HomeViewModel(
    c: AppContainer,
) : ViewModel() {
    private val week = MutableStateFlow(MealItems.weekStart(LocalDate.now()))
    private val filter = MutableStateFlow<MealKind?>(null)

    val state: StateFlow<HomeState> =
        combine(
            c.repository.observeItems(),
            week,
            filter,
            c.replicators.state,
        ) { items, w, f, sync ->
            val inWeek = items.filter { MealItems.inWeek(it, w) && (f == null || it.meal.kind == f) }
            HomeState(
                weekStart = w,
                filter = f,
                days = MealItems.groupByDay(inWeek).toList(),
                sync = sync,
                inFlight = items.count { it.status == MealStatus.PENDING || it.status == MealStatus.PROCESSING },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    fun shiftWeek(delta: Long) {
        week.value = week.value.plusWeeks(delta)
    }

    fun setFilter(kind: MealKind?) {
        filter.value = kind
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    onLog: () -> Unit,
    onPlan: () -> Unit,
    onOpen: (String) -> Unit,
    onReminders: () -> Unit,
    onSettings: () -> Unit,
    onDev: () -> Unit,
) {
    val container = rememberContainer()
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                LargeFlexibleTopAppBar(
                    scrollBehavior = scrollBehavior,
                    title = { Text("DineTime") },
                    actions = {
                        IconButton(onClick = onReminders) { Icon(Icons.Default.Notifications, "Reminders") }
                        IconButton(onClick = onDev) { Icon(Icons.Default.BugReport, "Dev panel") }
                        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                    },
                )
                SyncBar(state.sync, state.inFlight)
            }
        },
        floatingActionButton = {
            var expanded by remember { mutableStateOf(false) }
            FloatingActionButtonMenu(
                expanded = expanded,
                button = {
                    ToggleFloatingActionButton(checked = expanded, onCheckedChange = { expanded = it }) {
                        Icon(if (expanded) Icons.Default.Close else Icons.Default.Add, null)
                    }
                },
            ) {
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        onPlan()
                    },
                    icon = { Icon(Icons.Default.Event, null) },
                    text = { Text("Plan meal") },
                )
                FloatingActionButtonMenuItem(
                    onClick = {
                        expanded = false
                        onLog()
                    },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("Log meal") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.shiftWeek(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous week") }
                Text(
                    "${state.weekStart.dayOfMonth} ${state.weekStart.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} – " +
                        "${state.weekStart.plusDays(
                            6,
                        ).dayOfMonth} ${state.weekStart.plusDays(6).month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                IconButton(onClick = { vm.shiftWeek(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next week") }
            }
            ConnectedToggleGroup(
                listOf<MealKind?>(null, MealKind.PLANNED, MealKind.UNPLANNED),
                state.filter,
                {
                    when (it) {
                        null -> "All"
                        MealKind.PLANNED -> "Planned"
                        else -> "Unplanned"
                    }
                },
                { vm.setFilter(it) },
                Modifier.padding(horizontal = 16.dp),
            )
            if (state.days.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Default.Restaurant, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                    Text("No meals this week", style = MaterialTheme.typography.titleMedium)
                    Text("Log what you ate or plan an upcoming meal.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding =
                        androidx.compose.foundation.layout
                            .PaddingValues(bottom = 160.dp),
                ) {
                    state.days.forEach { (day, meals) ->
                        stickyHeader(key = "h-$day") {
                            val date = Instant.ofEpochMilli(meals.first().meal.scheduledAt).atZone(ZoneId.systemDefault()).toLocalDate()
                            Text(
                                "${day.getDisplayName(TextStyle.FULL, Locale.getDefault())} · ${date.dayOfMonth}/${date.monthValue}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                        ).padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                        items(meals, key = { it.id }) { MealCard(it, onClick = { onOpen(it.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
fun MealCard(
    item: MealItem,
    onClick: () -> Unit,
) {
    val m = item.meal
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (item.status ==
                        MealStatus.ERROR
                    ) {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (item.deleting) "${m.title} (deleting)" else m.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                StatusBadge(item.status, item.retryAttempt)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatTime(m.scheduledAt)} · ${m.mealType.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (m.kind ==
                        MealKind.PLANNED
                    ) {
                        "Planned"
                    } else {
                        "Logged"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (m.reminderLeadMinutes != null) {
                    Icon(Icons.Default.NotificationsActive, null, Modifier.size(14.dp))
                    Text(leadLabel(m.reminderLeadMinutes), style = MaterialTheme.typography.labelSmall)
                }
            }
            item.error?.let {
                Text(
                    "${it.message}${it.fields.values.firstOrNull()?.let { f ->
                        ": $f"
                    }.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
