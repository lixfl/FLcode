package com.fenglingcode.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LLMUsage(
    val inputTokens: Int,
    val outputTokens: Int,
    val cacheCreationInputTokens: Int? = null,
    val cacheReadInputTokens: Int? = null,
    /**
     * The total input token count reported by the API for this call.
     * Represents how much of the context window has been consumed.
     * Used by dynamicMaxTokens() to compute remaining space.
     * Mirrors iOS's latestContextTokens.
     */
    val latestContextTokens: Int = 0,
    /** First-token latency in milliseconds (time from stream start to first text chunk). */
    val firstTokenMs: Long? = null,
    /** Total streaming duration in seconds for this turn. */
    val streamSeconds: Double? = null,
)
