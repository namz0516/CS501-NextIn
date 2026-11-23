package com.example.nextin.notifications

import android.content.Context
import androidx.core.content.edit

class AlertPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun sentReminders(): Set<String> =
        prefs.getStringSet(KEY_SENT_REMINDERS, emptySet()).orEmpty().toSet()

    fun saveSentReminders(keys: Set<String>) {
        prefs.edit { putStringSet(KEY_SENT_REMINDERS, keys) }
    }

    fun sentExpiries(): Set<String> =
        prefs.getStringSet(KEY_SENT_EXPIRIES, emptySet()).orEmpty().toSet()

    fun markExpirySent(key: String) {
        val recent = (sentExpiries().toList() + key).distinct().takeLast(MAX_SAVED_EXPIRIES).toSet()
        prefs.edit { putStringSet(KEY_SENT_EXPIRIES, recent) }
    }

    fun scheduledAlarms(): Map<String, String> =
        prefs.getStringSet(KEY_SCHEDULED_ALARMS, emptySet()).orEmpty()
            .mapNotNull { value ->
                val split = value.indexOf('|')
                if (split <= 0) null else value.substring(0, split) to value.substring(split + 1)
            }
            .toMap()

    fun saveScheduledAlarms(alarms: Map<String, String>) {
        prefs.edit { putStringSet(KEY_SCHEDULED_ALARMS, alarms.map { "${it.key}|${it.value}" }.toSet()) }
    }

    fun promptDismissedFor(entryId: String): Boolean =
        prefs.getString(KEY_PROMPT_DISMISSED_ENTRY, null) == entryId

    fun dismissPrompt(entryId: String) {
        prefs.edit { putString(KEY_PROMPT_DISMISSED_ENTRY, entryId) }
    }

    fun alarmPromptDismissedFor(entryId: String): Boolean =
        prefs.getString(KEY_ALARM_PROMPT_DISMISSED_ENTRY, null) == entryId

    fun dismissAlarmPrompt(entryId: String) {
        prefs.edit { putString(KEY_ALARM_PROMPT_DISMISSED_ENTRY, entryId) }
    }

    fun permissionAsked(): Boolean = prefs.getBoolean(KEY_PERMISSION_ASKED, false)

    fun markPermissionAsked() {
        prefs.edit { putBoolean(KEY_PERMISSION_ASKED, true) }
    }

    private companion object {
        const val FILE_NAME = "queue_alerts"
        const val KEY_SENT_REMINDERS = "sent_reminders"
        const val KEY_PROMPT_DISMISSED_ENTRY = "prompt_dismissed_entry"
        const val KEY_PERMISSION_ASKED = "permission_asked"
        const val KEY_ALARM_PROMPT_DISMISSED_ENTRY = "alarm_prompt_dismissed_entry"
        const val KEY_SENT_EXPIRIES = "sent_expiries"
        const val KEY_SCHEDULED_ALARMS = "scheduled_alarms"
        const val MAX_SAVED_EXPIRIES = 20
    }
}
