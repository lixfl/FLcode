package com.fenglingcode.app.data

import android.content.Context
import com.fenglingcode.app.data.db.ChatSessionEntity
import com.fenglingcode.app.data.db.MessageEntity
import com.fenglingcode.app.data.repository.ChatRepository
import com.fenglingcode.app.data.repository.SkillRepository
import com.fenglingcode.app.logging.AppLogger
import java.io.File

/**
 * Clone a session (or its skill/memory artifacts) into a fresh local session.
 * Mirrors iOS `SessionForkManager`. Android MVP only implements the **local**
 * branch of the iOS API — `forkSession` (from iCloud-mirrored remote devices)
 * is deferred until we ship a cross-device sync layer; see spec §7.8.
 *
 * The returned session carries "(Copy)" suffix and entirely new message ids,
 * so the duplicate and its source are independent. Compact markers ARE cloned
 * (T-session-duplicate-compact-marker-android, iOS parity e8ac8b82) with their
 * message-id references remapped onto the duplicated messages, so the compact
 * divider/shadow is preserved on the copy.
 *
 * [forkSessionAt] adds the message-level branch ("从此处分叉"): the same clone
 * machinery, truncated at a cutoff row so the new session keeps only the
 * conversation up to and including that point.
 */
class SessionForkManager(
    private val chatRepository: ChatRepository,
    private val skillRepository: SkillRepository? = null,
    private val filesDir: File,
) {
    companion object {
        private const val TAG = "SessionForkManager"

        /**
         * Prefixes carried by an existing title after repeated copies/branches.
         * Stripped before appending the fresh suffix so the bracket chain never
         * compounds into "(Copy) (Copy) (Branch)" titles.
         */
        private val SUFFIX_PREFIXES = listOf("Copy", "Branch")
    }

    /**
     * [T-android-session-fork-at] Result of [forkSessionAt].
     * @param sessionId id of the newly created branch session, or null when the
     *   cutoff message could not be resolved (caller should tell the user).
     * @param keptCount how many source rows actually landed in the branch.
     */
    data class ForkResult(val sessionId: String?, val keptCount: Int)

    /**
     * Deep-copy a local session: creates a new session with the same model id
     * and copies every message's payload. Returns the new session's id or
     * null if [sessionId] doesn't exist.
     *
     * Carries over user-meaningful session metadata via
     * [carryOverSourceSettings] so the duplicate looks and behaves like the
     * source (category, modelBinding, memoryEnabled, thinkingOverride).
     *
     * Deliberately NOT copied (the duplicate is a new session at creation
     * time, not a clone of the source's lifecycle):
     *   - `pinnedAt` (the copy starts unpinned)
     *   - `source` (origin tag like "shortcut" — the copy was made via the
     *     duplicate button, not via the original entry point)
     *   - `editCount` (starts at 0)
     *   - `lastMessage` (createSession seeds it; refreshed by appendMessage)
     *   - `id` / `createdAt` / `updatedAt` (new identity, current timestamps)
     */
    suspend fun duplicateSession(sessionId: String): String? {
        val source = chatRepository.getSession(sessionId) ?: run {
            AppLogger.warning(TAG, "duplicateSession: $sessionId not found")
            return null
        }
        val messages = chatRepository.loadMessages(sessionId)
        val dupTitle = "${source.title ?: "Chat"} (Copy)"
        val new = chatRepository.createSession(
            modelId = source.modelId,
            title = dupTitle,
            memoryEnabled = source.memoryEnabled != 0,
        )
        carryOverSourceSettings(source, new.id, dupTitle)
        cloneMessagesAndMarkers(source, messages, new.id)
        AppLogger.info(
            TAG,
            "duplicated session $sessionId → ${new.id} (${messages.size} msgs, " +
                "category=${source.category}, modelBinding=${source.modelBinding}, " +
                "memoryEnabled=${source.memoryEnabled})",
        )
        return new.id
    }

    /**
     * [T-android-session-fork-at] "从此处分叉" — create a branch session that
     * keeps only the conversation up to and including DB row
     * [keepThroughSortOrder], then continues independently. Mirrors how iOS
     * branches a chat from a turn; the source session is never touched.
     *
     * [keepThroughSortOrder] comes from the caller's user-turn ordinal
     * anchoring (UI bubbles and DB rows are not 1:1 — see ChatViewModel's
     * resolveForkCutoffSortOrder). A value before the first row yields an
     * empty branch (still a valid continuation point); a value at or after
     * the last row yields a full copy.
     */
    suspend fun forkSessionAt(
        sessionId: String,
        keepThroughSortOrder: Int,
        titleSuffix: String = "Branch",
    ): ForkResult {
        val source = chatRepository.getSession(sessionId) ?: run {
            AppLogger.warning(TAG, "forkSessionAt: $sessionId not found")
            return ForkResult(null, 0)
        }
        val all = chatRepository.loadMessages(sessionId)
        val kept = all.filter { it.sortOrder <= keepThroughSortOrder }
        val branchTitle = branchTitle(source.title, titleSuffix)
        val new = chatRepository.createSession(
            modelId = source.modelId,
            title = branchTitle,
            memoryEnabled = source.memoryEnabled != 0,
        )
        carryOverSourceSettings(source, new.id, branchTitle)
        cloneMessagesAndMarkers(source, kept, new.id)
        // Mirror deleteFromMessage's tail: clone rows bypass appendMessage's
        // preview side-effect, so refresh the last-message preview from the
        // branch's own tail (a no-op when it stays empty).
        kept.lastOrNull()?.let {
            runCatching { chatRepository.updateSessionPreview(new.id, it.partsJson) }
        }
        AppLogger.info(
            TAG,
            "forkSessionAt: session $sessionId → ${new.id} keepThroughSortOrder=$keepThroughSortOrder " +
                "(${kept.size}/${all.size} msgs, title=$branchTitle)",
        )
        return ForkResult(new.id, kept.size)
    }

    /**
     * Copy the per-session settings that make a duplicate/branch behave like
     * the source: category, pinned-model binding, memory toggle, thinking
     * override. [newTitle] is re-pushed alongside the category because
     * [ChatRepository.updateSessionTitleAndCategory] writes both columns.
     */
    private suspend fun carryOverSourceSettings(
        source: ChatSessionEntity,
        newId: String,
        newTitle: String,
    ) {
        // Mirror iOS: carry over the source's category onto the copy so the
        // session-list icon + colour stay consistent with the original.
        if (source.category != null) {
            chatRepository.updateSessionTitleAndCategory(newId, newTitle, source.category)
        }
        // Carry over the user's per-session model binding (the "pinned model"
        // override) so duplicating a coding session pinned to GPT-5 stays
        // pinned to GPT-5 in the copy.
        if (source.modelBinding != null) {
            chatRepository.updateSessionBinding(newId, source.modelBinding, source.modelId)
        }
        // Carry over the per-session memory toggle. createSession already got
        // the value via the memoryEnabled param; this push keeps the write
        // path explicit (and harmless) for rows whose default differs.
        if (source.memoryEnabled == 0) {
            chatRepository.dao.updateMemoryEnabled(newId, 0)
        }
        // T239: carry over the per-session thinking-mode override so a
        // duplicate of a "tuned to MEDIUM" session opens at MEDIUM rather
        // than reverting to the default (parity with memoryEnabled).
        source.thinkingOverride?.let { override ->
            chatRepository.dao.updateThinkingOverride(newId, override)
        }
    }

    /**
     * [newTitle] is the exact title the new session row carries, so repeated
     * branches keep one suffix: "Chat (Copy) (Branch)" → "Chat (Branch)",
     * "Chat (Branch) (Branch)" → "Chat (Branch)".
     */
    private fun branchTitle(sourceTitle: String?, suffix: String): String {
        var base = (sourceTitle ?: "Chat").trim()
        var changed = true
        while (changed) {
            changed = false
            for (p in SUFFIX_PREFIXES) {
                val marker = " ($p)"
                if (base.endsWith(marker)) {
                    base = base.removeSuffix(marker).trimEnd()
                    changed = true
                }
            }
        }
        return "$base ($suffix)"
    }

    /**
     * Deep-copy [messages] into [newSessionId] with fresh ids and remapped
     * compact markers (T-session-duplicate-compact-marker-android). Extracted
     * verbatim from duplicateSession so [forkSessionAt] shares the exact same
     * copy pipeline; the loop relies on `loadMessages` (ORDER BY sort_order
     * ASC) feeding appendMessage's nextSortOrder allocator monotonically.
     */
    private suspend fun cloneMessagesAndMarkers(
        source: ChatSessionEntity,
        messages: List<MessageEntity>,
        newSessionId: String,
    ) {
        // [T-session-duplicate-compact-marker-android] Track old→new message id
        // and old→new sort_order so copied compact markers (which reference DB
        // message ids + legacy sort_orders) can be remapped onto the freshly
        // minted duplicate messages. appendMessage returns the new MessageEntity
        // (id + sortOrder), so no re-query is needed. Aligns iOS e8ac8b82.
        val oldToNewId = HashMap<String, String>()
        val oldToNewSort = HashMap<String, Int>()
        for (msg in messages) {
            val newMsg = chatRepository.appendMessage(
                sessionId = newSessionId,
                role = msg.role,
                partsJson = msg.partsJson,
                tokenUsage = msg.tokenUsage,
                reasoningContent = msg.reasoningContent,
            )
            oldToNewId[msg.id] = newMsg.id
            oldToNewSort[msg.id] = newMsg.sortOrder
        }

        // [T-session-duplicate-compact-marker-android] Copy compact markers
        // AFTER all messages are appended (so the new sort_orders exist). A
        // session with zero markers makes this a clean no-op — the normal
        // duplicate path is untouched. Each marker is remapped independently so
        // multi-compact sessions copy all dividers, in created_at order
        // (listCompactMarkers returns ASC by created_at).
        val markers = chatRepository.dao.listCompactMarkers(source.id)
        if (markers.isNotEmpty()) {
            // Remap a referenced message id through the copy map. null → null.
            fun remapId(id: String?): String? = id?.let { oldToNewId[it] }
            // Resolve a legacy sort_order fallback to the NEW message's
            // sort_order via the referenced id; keep the original when the id
            // is null or can't be resolved (legacy markers without ids).
            fun remapSort(messageId: String?, original: Int): Int =
                messageId?.let { oldToNewSort[it] } ?: original
            var copied = 0
            for (marker in markers) {
                // A marker whose referenced message didn't survive into the
                // copy (never for duplicateSession, which copies every row;
                // possible for forkSessionAt when the cutoff slices the
                // marker's range away) would point at nothing on the new
                // session — skip it rather than write a dangling copy.
                val firstKeptNew = remapId(marker.firstKeptMessageId)
                val lastCompactedNew = remapId(marker.lastCompactedMessageId)
                val boundaryNew = remapId(marker.boundaryMessageId)
                if ((marker.firstKeptMessageId != null && firstKeptNew == null) ||
                    (marker.lastCompactedMessageId != null && lastCompactedNew == null) ||
                    (marker.boundaryMessageId != null && boundaryNew == null)
                ) {
                    AppLogger.warning(
                        TAG,
                        "clone: skipping compact marker ${marker.id} — a referenced message id is not in the copy map (dangling source marker or cut off by fork)",
                    )
                    continue
                }
                val copy = marker.copy(
                    id = java.util.UUID.randomUUID().toString(),
                    sessionId = newSessionId,
                    firstKeptSortOrder = remapSort(marker.firstKeptMessageId, marker.firstKeptSortOrder),
                    uiBoundarySortOrder = marker.uiBoundarySortOrder?.let {
                        remapSort(marker.boundaryMessageId, it)
                    },
                    boundaryMessageId = boundaryNew,
                    firstKeptMessageId = firstKeptNew,
                    lastCompactedMessageId = lastCompactedNew,
                    // summary / compactedCount / createdAt / version verbatim.
                )
                chatRepository.dao.insertCompactMarker(copy)
                copied++
            }
            AppLogger.info(TAG, "clone: copied $copied/${markers.size} compact marker(s) to $newSessionId")
        }
    }

    /**
     * Clone a skill into the local library. When [skillRepository] is wired,
     * reuses `importFromContent` so the skill lands in DB + `SKILL.md` just
     * like a fresh import. Returns whether the import succeeded.
     */
    fun copySkill(content: String, source: SkillRepository.ImportSource = SkillRepository.ImportSource.FILE): Boolean {
        val repo = skillRepository ?: run {
            AppLogger.warning(TAG, "copySkill: SkillRepository not injected")
            return false
        }
        return repo.importFromContent(content, source) != null
    }

    /**
     * Persist a memory note (plain Markdown / text) under
     * `<filesDir>/fengling-global/memory/<fileName>`. Mirrors iOS
     * `SessionForkManager.copyRemoteMemory` which writes under
     * `minisMemoryPersistentDir`. Overwrites if the file already exists.
     */
    fun copyMemory(fileName: String, content: String): Boolean {
        if (fileName.contains("/") || fileName.contains("..")) {
            AppLogger.warning(TAG, "copyMemory: rejecting unsafe fileName '$fileName'")
            return false
        }
        val dir = File(filesDir, "fengling-global/memory").apply { mkdirs() }
        return try {
            File(dir, fileName).writeText(content)
            true
        } catch (e: Exception) {
            AppLogger.warning(TAG, "copyMemory: write failed: ${e.message}")
            false
        }
    }
}

/** Convenience: default-wire against application context. */
fun SessionForkManager(
    context: Context,
    chatRepository: ChatRepository,
    skillRepository: SkillRepository? = null,
): SessionForkManager = SessionForkManager(
    chatRepository = chatRepository,
    skillRepository = skillRepository,
    filesDir = context.filesDir,
)
