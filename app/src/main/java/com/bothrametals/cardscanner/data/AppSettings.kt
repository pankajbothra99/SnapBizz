package com.bothrametals.cardscanner.data

import android.content.Context
import com.bothrametals.cardscanner.ai.GeminiProtocol
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** App settings stored privately on this phone. */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_API, v.trim()).apply()

    var aiEnabled: Boolean
        get() = prefs.getBoolean(KEY_AI_ON, true)
        set(v) = prefs.edit().putBoolean(KEY_AI_ON, v).apply()

    var userName: String
        get() = prefs.getString(KEY_USER, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_USER, v.trim()).apply()

    var models: List<String>
        get() = prefs.getString(KEY_MODELS, null)
            ?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.ifEmpty { null } ?: GeminiProtocol.DEFAULT_MODELS
        set(v) = prefs.edit().putString(KEY_MODELS, v.joinToString(",")).apply()

    val aiReady: Boolean get() = aiEnabled && apiKey.isNotBlank()

    /** The event new scans go into ("" = no event / all cards). */
    var currentEventId: String
        get() = prefs.getString(KEY_EVENT, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_EVENT, v).apply()

    /** Copy every card photo scanned on this phone into the Gallery album "Card Scanner". */
    var saveToGallery: Boolean
        get() = prefs.getBoolean(KEY_GALLERY, true)
        set(v) = prefs.edit().putBoolean(KEY_GALLERY, v).apply()

    /** The three welcome pages have been seen (shown once after install). */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(v) = prefs.edit().putBoolean(KEY_WELCOME, v).apply()

    /** Read each company's website in the background and add a short note (needs AI reading). */
    var autoWebNotes: Boolean
        get() = prefs.getBoolean(KEY_WEB_AUTO, true)
        set(v) = prefs.edit().putBoolean(KEY_WEB_AUTO, v).apply()

    /**
     * Cards scanned from this time on get their website read automatically. Older cards (from before
     * this feature) are done only when asked, in Settings. Set the first time it's needed.
     */
    val webNotesSince: Long
        get() {
            val t = prefs.getLong(KEY_WEB_SINCE, 0L)
            if (t > 0) return t
            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_WEB_SINCE, now).apply()
            return now
        }

    /** Number of cards read by AI today on this phone. */
    val aiReadsToday: Int
        get() = if (prefs.getString(KEY_DAY, "") == today()) prefs.getInt(KEY_COUNT, 0) else 0

    fun countAiRead() {
        val day = today()
        val current = if (prefs.getString(KEY_DAY, "") == day) prefs.getInt(KEY_COUNT, 0) else 0
        prefs.edit().putString(KEY_DAY, day).putInt(KEY_COUNT, current + 1).apply()
    }

    /** Text placed in the team-setup QR code. */
    fun setupCode(): String = "$SETUP_PREFIX|$apiKey|${models.joinToString(",")}"

    /** Applies a scanned setup code. Returns false if it isn't one of ours. */
    fun applySetupCode(code: String): Boolean {
        val parts = code.trim().split('|')
        if (parts.size < 2 || parts[0] != SETUP_PREFIX || parts[1].isBlank()) return false
        apiKey = parts[1]
        if (parts.size >= 3 && parts[2].isNotBlank()) models = parts[2].split(',')
        aiEnabled = true
        return true
    }

    // ------------------------------------------------------------ cloud / login (cached so the app opens offline)

    /** Server time of the newest card change received (seconds, nanos); 0 = never synced. */
    var lastSyncSeconds: Long
        get() = prefs.getLong(KEY_SYNC_S, 0L)
        set(v) = prefs.edit().putLong(KEY_SYNC_S, v).apply()
    var lastSyncNanos: Int
        get() = prefs.getInt(KEY_SYNC_N, 0)
        set(v) = prefs.edit().putInt(KEY_SYNC_N, v).apply()

    /** Last confirmed team membership on this phone: "email|admin(true/false)|company name". */
    var cachedMembership: String
        get() = prefs.getString(KEY_MEMBER, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_MEMBER, v).apply()

    /** The AI key came from the company account (not typed on this phone). */
    var aiKeyFromTeam: Boolean
        get() = prefs.getBoolean(KEY_TEAM_KEY, false)
        set(v) = prefs.edit().putBoolean(KEY_TEAM_KEY, v).apply()

    /** Email whose login the phone's older (pre-login) cards were assigned to. */
    var migratedFor: String
        get() = prefs.getString(KEY_MIGRATED, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_MIGRATED, v).apply()

    fun clearCloud() {
        prefs.edit()
            .remove(KEY_SYNC_S).remove(KEY_SYNC_N).remove(KEY_MEMBER).remove(KEY_MIGRATED).remove(KEY_EVENT)
            .apply()
        if (aiKeyFromTeam) {
            apiKey = ""
            aiKeyFromTeam = false
        }
    }

    private fun today() = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH).format(Date())

    companion object {
        private const val KEY_API = "gemini_api_key"
        private const val KEY_AI_ON = "ai_enabled"
        private const val KEY_USER = "user_name"
        private const val KEY_MODELS = "gemini_models"
        private const val KEY_DAY = "ai_count_day"
        private const val KEY_COUNT = "ai_count"
        private const val KEY_GALLERY = "save_to_gallery"
        private const val KEY_EVENT = "current_event"
        private const val KEY_SYNC_S = "sync_s"
        private const val KEY_SYNC_N = "sync_n"
        private const val KEY_MEMBER = "member_cache"
        private const val KEY_TEAM_KEY = "ai_key_from_team"
        private const val KEY_MIGRATED = "migrated_for"
        private const val KEY_WELCOME = "welcome_seen"
        private const val KEY_WEB_AUTO = "web_notes_auto"
        private const val KEY_WEB_SINCE = "web_notes_since"
        const val SETUP_PREFIX = "CARDSCANNER1"
    }
}
