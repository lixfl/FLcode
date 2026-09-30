package com.fenglingcode.app.ui.chat

// [T-android-split-chat] Small UI-state toggle methods extracted from
// ChatViewModel as extension functions (verbatim): tool-detail sheet,
// browser sheet, memory sheet, attachment list. The 4 backing state fields
// were flipped private->internal. No logic change.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyListState
import com.fenglingcode.app.agent.Level
import com.fenglingcode.app.agent.ToolLoopDetector
import com.fenglingcode.app.browser.BrowserActionInput
import com.fenglingcode.app.browser.BrowserTabPool
import com.fenglingcode.app.data.db.MessageEntity
import com.fenglingcode.app.data.BPETokenizer
import com.fenglingcode.app.data.ContextOffload
import com.fenglingcode.app.data.ContextPolicy
import com.fenglingcode.app.logging.AppLogger
import com.fenglingcode.app.data.FileMentionIndex
import com.fenglingcode.app.data.db.CompactMarkerEntity
import com.fenglingcode.app.data.model.AgentContentPart
import com.fenglingcode.app.data.model.AgentToolDefinition
import com.fenglingcode.app.data.model.LLMMessage
import com.fenglingcode.app.data.model.LLMModel
import com.fenglingcode.app.data.model.LLMStreamChunk
import com.fenglingcode.app.data.model.LLMUsage
import com.fenglingcode.app.data.model.ModelGroup
import com.fenglingcode.app.data.model.ThinkingLevel
import com.fenglingcode.app.R
import com.fenglingcode.app.data.repository.ChatRepository
import com.fenglingcode.app.data.repository.MemoryRepository
import com.fenglingcode.app.data.repository.ProviderRepository
import com.fenglingcode.app.provider.ImageBudget
import com.fenglingcode.app.provider.LLMProvider
import com.fenglingcode.app.provider.ProviderFactory
import com.fenglingcode.app.sandbox.ExecutionCoordinator
import com.fenglingcode.app.terminal.FenglingOpenUrlBroker
import com.fenglingcode.app.terminal.FenglingUrlMarker
import com.fenglingcode.app.tools.AgentTools
import com.fenglingcode.app.tools.FileEditTool
import com.fenglingcode.app.tools.FileReadTool
import com.fenglingcode.app.tools.FileWriteTool
import com.fenglingcode.app.tools.MemoryTools
import com.fenglingcode.app.tools.ReadImageTool
import com.fenglingcode.app.tools.ToolExecutionResult
import com.fenglingcode.app.offload.OffloadPermissionManager
import com.fenglingcode.app.service.SessionActivityTracker
import com.fenglingcode.app.service.SessionConcurrencyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal fun ChatViewModel.openToolDetail(toolBlockId: String) {
    _selectedToolDetailId.value = toolBlockId
}

internal fun ChatViewModel.closeToolDetail() {
    _selectedToolDetailId.value = null
}

internal fun ChatViewModel.toggleBrowserSheet() {
    val opening = !_showBrowserSheet.value
    if (opening) browserTabPool.ensureTabForUI()
    _showBrowserSheet.value = opening
}

internal fun ChatViewModel.dismissBrowserSheet() {
    _showBrowserSheet.value = false
}

/**
 * Open the session browser sheet, focused on the tab whose URL matches
 * [url]. If no pool tab currently has that URL, a new tab is created and
 * loaded. Used by the tool-call preview's globe button so the agent's
 * existing browser_use page is reused when available instead of spawning
 * a duplicate tab.
 */
internal fun ChatViewModel.openBrowserSheetForUrl(url: String) {
    if (url.isBlank()) {
        browserTabPool.ensureTabForUI()
    } else {
        browserTabPool.selectOrCreateTabForURL(url)
    }
    _showBrowserSheet.value = true
}

internal fun ChatViewModel.toggleMemorySheet() {
    _showMemorySheet.value = !_showMemorySheet.value
}

internal fun ChatViewModel.dismissMemorySheet() {
    _showMemorySheet.value = false
}

internal fun ChatViewModel.addAttachment(attachment: InputAttachment) {
    _attachments.value = _attachments.value + attachment
}

internal fun ChatViewModel.removeAttachment(id: String) {
    _attachments.value = _attachments.value.filter { it.id != id }
}

internal fun ChatViewModel.clearAttachments() {
    _attachments.value = emptyList()
}
