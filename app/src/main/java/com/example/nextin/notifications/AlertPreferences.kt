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

    fun promptDismissedFor(entryId: String): Boolean =
        prefs.getString(KEY_PROMPT_DISMISSED_ENTRY, null) == entryId

    fun dismissPrompt(entryId: String) {
        prefs.edit { putString(KEY_PROMPT_DISMISSED_ENTRY, entryId) }
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
    }
}
