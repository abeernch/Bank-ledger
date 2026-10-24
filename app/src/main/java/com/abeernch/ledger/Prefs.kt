package com.abeernch.ledger

import android.content.Context
import android.content.SharedPreferences

/** Small settings kept outside the database. */
class Prefs(context: Context) {

    private val p: SharedPreferences = context.getSharedPreferences("ledger", Context.MODE_PRIVATE)

    var rulesSeeded: Boolean
        get() = p.getBoolean("rules_seeded", false)
        set(v) = p.edit().putBoolean("rules_seeded", v).apply()

    var historyImported: Boolean
        get() = p.getBoolean("history_imported", false)
        set(v) = p.edit().putBoolean("history_imported", v).apply()

    /** SYSTEM, LIGHT or DARK */
    var theme: String
        get() = p.getString("theme", "SYSTEM") ?: "SYSTEM"
        set(v) = p.edit().putString("theme", v).apply()

    var appLock: Boolean
        get() = p.getBoolean("app_lock", false)
        set(v) = p.edit().putBoolean("app_lock", v).apply()

    /** Your name as banks write it. Payments to this name are own transfers. */
    var myName: String
        get() = p.getString("my_name", "") ?: ""
        set(v) = p.edit().putString("my_name", v.trim()).apply()

    var budgetAlerts: Boolean
        get() = p.getBoolean("budget_alerts", true)
        set(v) = p.edit().putBoolean("budget_alerts", v).apply()

    var billReminders: Boolean
        get() = p.getBoolean("bill_reminders", true)
        set(v) = p.edit().putBoolean("bill_reminders", v).apply()

    /** Remembers which alerts were already shown, e.g. "Food|2026-10|80". */
    fun alertShown(key: String): Boolean = p.getBoolean("alert:$key", false)

    fun markAlertShown(key: String) = p.edit().putBoolean("alert:$key", true).apply()
}
