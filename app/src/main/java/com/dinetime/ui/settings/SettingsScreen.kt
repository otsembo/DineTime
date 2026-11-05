package com.dinetime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dinetime.domain.MealItem
import com.dinetime.reminders.reminderTimeOf
import com.dinetime.ui.formatFull
import com.dinetime.ui.leadLabel
import com.dinetime.ui.rememberContainer

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RemindersScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val c = rememberContainer()
    val items by remember { c.repository.observeItems() }.collectAsStateWithLifecycle(initialValue = emptyList<MealItem>())
    val now = System.currentTimeMillis()
    val upcoming = items.filter { reminderTimeOf(it)?.let { t -> t > now } == true }
        .sortedBy { reminderTimeOf(it) }
    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text("Upcoming reminders") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        if (upcoming.isEmpty()) {
            Text("No upcoming reminders. Plan a meal and choose a reminder time.", Modifier.padding(pad).padding(16.dp))
        } else {
            LazyColumn(Modifier.padding(pad), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                items(upcoming, key = { it.id }) { item ->
                    Card(onClick = { onOpen(item.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.meal.title, style = MaterialTheme.typography.titleMedium)
                            Text("Meal: ${formatFull(item.meal.scheduledAt)}", style = MaterialTheme.typography.bodySmall)
                            Text("Reminder: ${formatFull(reminderTimeOf(item)!!)} (${leadLabel(item.meal.reminderLeadMinutes)})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val c = rememberContainer()
    val s by c.settings.settings.collectAsStateWithLifecycle()
    var host by remember(s.sgHost) { mutableStateOf(s.sgHost) }
    var pub by remember(s.sgPublicPort) { mutableStateOf(s.sgPublicPort.toString()) }
    var admin by remember(s.sgAdminPort) { mutableStateOf(s.sgAdminPort.toString()) }
    var db by remember(s.sgDatabase) { mutableStateOf(s.sgDatabase) }
    var user by remember(s.userName) { mutableStateOf(s.userName) }
    var pass by remember(s.userPassword) { mutableStateOf(s.userPassword) }
    var adminUser by remember(s.adminUser) { mutableStateOf(s.adminUser) }
    var adminPass by remember(s.adminPassword) { mutableStateOf(s.adminPassword) }
    var lead by remember(s.defaultLeadMinutes) { mutableStateOf(s.defaultLeadMinutes.toString()) }

    Scaffold(topBar = {
        LargeFlexibleTopAppBar(title = { Text("Settings") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(lead, { lead = it.filter(Char::isDigit) }, label = { Text("Default reminder lead (minutes)") }, singleLine = true)
            Text("Sync Gateway", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(host, { host = it }, label = { Text("Host") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(pub, { pub = it.filter(Char::isDigit) }, label = { Text("Public port") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(admin, { admin = it.filter(Char::isDigit) }, label = { Text("Admin port") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(db, { db = it }, label = { Text("Database") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it }, label = { Text("User") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("Simulator admin credentials", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(adminUser, { adminUser = it }, label = { Text("Admin") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(adminPass, { adminPass = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Button(onClick = {
                c.settings.update {
                    it.copy(
                        sgHost = host.trim(), sgPublicPort = pub.toIntOrNull() ?: it.sgPublicPort,
                        sgAdminPort = admin.toIntOrNull() ?: it.sgAdminPort, sgDatabase = db.trim(),
                        userName = user.trim(), userPassword = pass, adminUser = adminUser.trim(), adminPassword = adminPass,
                        defaultLeadMinutes = lead.toIntOrNull() ?: it.defaultLeadMinutes,
                    )
                }
            }) { Text("Save") }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(s.syncEnabled, { v -> c.settings.update { it.copy(syncEnabled = v) } })
                Text("Replication enabled (off = airplane mode)")
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Switch(s.dynamicColor, { v -> c.settings.update { it.copy(dynamicColor = v) } })
                Text("Use wallpaper colours (Android 12+)")
            }
        }
    }
}
