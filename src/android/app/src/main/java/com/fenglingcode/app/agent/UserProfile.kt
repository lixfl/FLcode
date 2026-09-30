package com.fenglingcode.app.agent

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [风铃code] User identity (nickname + avatar), editable in
 * Settings → 灵魂 alongside the assistant's SOUL profile. Persisted via
 * SharedPreferences rather than SOUL.md because SOUL.md is a shared,
 * synced text file — stuffing a base64 image URI there would inflate
 * every sync and could clobber an iOS-authored file that doesn't know
 * the field.
 *
 * The nickname is injected into the agent's system prompt (see
 * [SystemPromptBuilder.identitySection]) so the model can address the
 * user by name. The avatar string is stored for UI rendering only —
 * a data URI can't be read as a text prompt, and re-encoding the
 * image every turn would blow the token budget.
 */
object UserProfile {
    const val DEFAULT_NICKNAME = "用户"

    private const val PREFS = "fengling_user_profile"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR = "avatar"
    // [风铃code] The session the user most recently OPENED (not the most
    // recently updated — browsing an old chat without sending should still
    // bring you back to it). The launch resolver reads this so reopening the
    // app lands on the last screen, matching "进去显示上次会话的界面".
    private const val KEY_LAST_OPENED = "last_opened_session"

    private val _nickname = MutableStateFlow(DEFAULT_NICKNAME)
    val nickname: StateFlow<String> = _nickname.asStateFlow()

    private val _avatar = MutableStateFlow("")
    val avatar: StateFlow<String> = _avatar.asStateFlow()

    /** Load persisted values into the StateFlows. Safe to call repeatedly. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _nickname.value = p.getString(KEY_NICKNAME, DEFAULT_NICKNAME)?.takeIf { it.isNotBlank() }
            ?: DEFAULT_NICKNAME
        _avatar.value = p.getString(KEY_AVATAR, "").orEmpty()
    }

    fun setNickname(context: Context, value: String) {
        val v = value.trim().ifBlank { DEFAULT_NICKNAME }
        _nickname.value = v
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_NICKNAME, v).apply()
    }

    fun setAvatar(context: Context, dataUri: String) {
        _avatar.value = dataUri
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_AVATAR, dataUri).apply()
    }

    /** Synchronous read for the prompt-build path (which cannot suspend). */
    fun cachedNickname(): String = _nickname.value

    // ── Last-opened session ────────────────────────────────────────────
    fun recordOpenedSession(context: Context, sessionId: String) {
        // Ignore draft ids — they never match a persisted row, so restoring
        // one on the next launch would flash an empty chat.
        if (sessionId.isEmpty() || sessionId.startsWith("__new__")) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LAST_OPENED, sessionId).apply()
    }

    fun lastOpenedSession(context: Context): String? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_OPENED, null)?.takeIf {
                it.isNotEmpty() && !it.startsWith("__new__")
            }
}
