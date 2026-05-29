package com.project.lumina.client.util

import android.app.Activity
import android.content.Context
import android.util.Base64
import java.io.File

class SessionManager(private val context: Context) {

    companion object {
        private const val SESSION_FILE = "session_data"
        private const val SESSION_DURATION_MS = 4 * 60 * 60 * 1000L
    }

    fun checkSession(activity: Activity): Boolean {
        return true
    }

    fun validateAndSaveSession(key: String, req: String): Boolean {
        saveSession()
        return true
    }

    private fun hasValidSession(): Boolean {
        return true
    }

    fun saveSession() {
        val sessionFile = File(context.filesDir, SESSION_FILE)
        val timestamp = System.currentTimeMillis().toString()
        val encodedData = Base64.encodeToString(timestamp.toByteArray(), Base64.NO_WRAP)
        sessionFile.writeText(encodedData)
    }

    fun clearSession() {
        val sessionFile = File(context.filesDir, SESSION_FILE)
        if (sessionFile.exists()) sessionFile.delete()
        val reqFile = File(context.filesDir, "req_code")
        if (reqFile.exists()) reqFile.delete()
    }

    fun getStoredReqCode(): String? {
        val reqFile = File(context.filesDir, "req_code")
        return if (reqFile.exists()) reqFile.readText() else null
    }

    fun getRemainingSessionTime(): Long {
        return SESSION_DURATION_MS
    }
}
