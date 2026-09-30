package com.fenglingcode.app.ui.chat

import androidx.compose.ui.graphics.Color
import com.fenglingcode.app.ui.theme.AppIcons

// [T-android-split-chat] Pure tool-label / duration / timestamp formatting
// helpers extracted verbatim from ChatScreen.kt. `internal` so the rest of the
// chat package (still in ChatScreen.kt) can call them across the file boundary.
// No logic change — code moved as-is.

internal val stepTimestampFormatter: java.text.SimpleDateFormat =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)

internal fun formatStepTimestamp(epochMs: Long): String =
    stepTimestampFormatter.format(java.util.Date(epochMs))

// [T-step-timestamp v2 aa8b1128] Short "elapsed-or-final duration"
// label for the tool detail header. Cross-platform format contract:
//   < 60s   → "3s"
//   < 1h    → "2m30s" (drops the seconds suffix when seconds == 0)
//   ≥ 1h    → "1h12m"
// When `stillRunning` is true the result is suffixed "…" so the
// header reads "12s…" while the tool is in flight.
// Negative / non-positive values clamp to 0.
internal fun formatStepDuration(seconds: Long, stillRunning: Boolean): String {
    val safe = seconds.coerceAtLeast(0L)
    val base = when {
        safe < 60L -> "${safe}s"
        safe < 3600L -> {
            val m = safe / 60L
            val s = safe % 60L
            if (s == 0L) "${m}m" else "${m}m${s}s"
        }
        else -> {
            val h = safe / 3600L
            val m = (safe % 3600L) / 60L
            if (m == 0L) "${h}h" else "${h}h${m}m"
        }
    }
    return if (stillRunning) "$base…" else base
}

// Helper: tool accent color
internal fun toolAccentColor(toolName: String): Color = when (toolName) {
    "shell_execute" -> Color(0xFF34C759)
    "file_read" -> Color(0xFF32ADE6)
    "file_write" -> Color(0xFF007AFF)
    "file_edit" -> Color(0xFFFF9500)
    "browser_use" -> Color(0xFF007AFF)
    "read_image" -> Color(0xFFAF52DE)
    "memory_write", "memory_get" -> Color(0xFFFF2D55)
    "web_search" -> Color(0xFF32ADE6)    // iOS: .cyan for search
    else -> Color(0xFF8E8E93)
}

// Helper: tool icon (iOS: distinct SF Symbols per tool type)
internal fun toolIconFor(toolName: String) = when (toolName) {
    "shell_execute" -> AppIcons.Terminal
    "file_read" -> AppIcons.Description         // iOS: doc.text
    "file_write" -> AppIcons.NoteAdd   // iOS: doc.text.fill (filled variant)
    "file_edit" -> AppIcons.EditNote             // iOS: square.and.pencil
    "browser_use" -> AppIcons.Language            // iOS: globe
    "read_image" -> AppIcons.Image                // iOS: photo
    "memory_write", "memory_get" -> AppIcons.Psychology // iOS: brain.head.profile
    "web_search" -> AppIcons.Search               // iOS: magnifyingglass
    else -> AppIcons.Build
}

// Helper: tool display name for "风铃code is using X"
internal fun toolDisplayName(toolName: String): String = when (toolName) {
    "shell_execute" -> "terminal"
    "file_read" -> "file reader"
    "file_write" -> "file writer"
    "file_edit" -> "file editor"
    "browser_use" -> "browser"
    "read_image" -> "image viewer"
    "memory_write" -> "memory"
    "memory_get" -> "memory"
    "web_search" -> "search"
    else -> toolName
}

/**
 * Full "风铃code is …" label shown in the tool detail sheet's bottom bar.
 * Mirrors iOS ToolLiveSheet.toolTitle so the wording matches per tool.
 */
internal fun toolTitleLabel(toolName: String): String = when (toolName) {
    "shell_execute" -> "风铃code is using Shell"
    "file_read" -> "风铃code is reading File"
    "file_write" -> "风铃code is using Editor"
    "file_edit" -> "风铃code is editing File"
    "browser_use" -> "风铃code is using Browser"
    "read_image" -> "风铃code is reading Image"
    "memory_write", "memory_get" -> "风铃code is using Memory"
    "web_search" -> "风铃code is using Search"
    else -> "风铃code is using ${toolDisplayName(toolName)}"
}

// Helper: format duration (iOS: < 1s → "0.1s", < 60s → "45s", >= 60s → "2m 10s")
internal fun formatToolDuration(ms: Long): String {
    val seconds = ms / 1000.0
    return when {
        seconds < 1 -> String.format("%.1fs", seconds)
        seconds < 60 -> String.format("%.0fs", seconds)
        else -> {
            val m = (seconds / 60).toInt()
            val s = (seconds % 60).toInt()
            "${m}m ${s}s"
        }
    }
}
