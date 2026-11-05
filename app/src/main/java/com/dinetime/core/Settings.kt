package com.dinetime.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val sgHost: String = "10.0.2.2",
    val sgPublicPort: Int = 4984,
    val sgAdminPort: Int = 4985,
    val sgDatabase: String = "dinetime",
    val userName: String = "demo",
    val userPassword: String = "password",
    val adminUser: String = "Administrator",
    val adminPassword: String = "password",
    val defaultLeadMinutes: Int = 30,
    val simulatorEnabled: Boolean = true,
    val syncEnabled: Boolean = true,
    val dynamicColor: Boolean = false,
    val processLegacyCommands: Boolean = false,
) {
    val publicWsUrl get() = "ws://$sgHost:$sgPublicPort/$sgDatabase"
    val adminHttpUrl get() = "http://$sgHost:$sgAdminPort"
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("dinetime_settings", Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    val ownerId: String get() = _settings.value.userName

    val deviceId: String = prefs.getString("deviceId", null)
        ?: java.util.UUID.randomUUID().toString().also { prefs.edit().putString("deviceId", it).apply() }

    fun update(transform: (AppSettings) -> AppSettings) {
        val new = transform(_settings.value)
        _settings.value = new
        prefs.edit()
            .putString("sgHost", new.sgHost)
            .putInt("sgPublicPort", new.sgPublicPort)
            .putInt("sgAdminPort", new.sgAdminPort)
            .putString("sgDatabase", new.sgDatabase)
            .putString("userName", new.userName)
            .putString("userPassword", new.userPassword)
            .putString("adminUser", new.adminUser)
            .putString("adminPassword", new.adminPassword)
            .putInt("defaultLead", new.defaultLeadMinutes)
            .putBoolean("simulator", new.simulatorEnabled)
            .putBoolean("sync", new.syncEnabled)
            .putBoolean("dynamicColor", new.dynamicColor)
            .putBoolean("legacyCmds", new.processLegacyCommands)
            .apply()
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            sgHost = prefs.getString("sgHost", d.sgHost)!!,
            sgPublicPort = prefs.getInt("sgPublicPort", d.sgPublicPort),
            sgAdminPort = prefs.getInt("sgAdminPort", d.sgAdminPort),
            sgDatabase = prefs.getString("sgDatabase", d.sgDatabase)!!,
            userName = prefs.getString("userName", d.userName)!!,
            userPassword = prefs.getString("userPassword", d.userPassword)!!,
            adminUser = prefs.getString("adminUser", d.adminUser)!!,
            adminPassword = prefs.getString("adminPassword", d.adminPassword)!!,
            defaultLeadMinutes = prefs.getInt("defaultLead", d.defaultLeadMinutes),
            simulatorEnabled = prefs.getBoolean("simulator", d.simulatorEnabled),
            syncEnabled = prefs.getBoolean("sync", d.syncEnabled),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            processLegacyCommands = prefs.getBoolean("legacyCmds", d.processLegacyCommands),
        )
    }
}
