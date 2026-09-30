package com.fenglingcode.app.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fenglingcode.app.R
import com.fenglingcode.app.data.repository.ChatRepository
import com.fenglingcode.app.ui.settings.SoulIconGlyph
import com.fenglingcode.app.ui.theme.ChatColors

// [风铃code] Empty new-chat welcome overlay (B+C), extracted out of the
// 8k-line ChatScreen.kt so the chat composer screen stays navigable. Pure
// presentation + two callbacks (fill composer, switch session); no shared
// mutable state, so this file compiles and evolves independently.
//
// The gate that decides WHETHER to show it (session loaded + no messages +
// not streaming) deliberately stays at the call site in ChatScreen — that
// logic reads messages/isStreaming, which belong to the screen, not here.

@Composable
internal fun ChatWelcomeOverlay(
    chatRepository: ChatRepository,
    viewModel: ChatViewModel,
    sessionId: String,
    onSessionSelected: (String) -> Unit,
    inputFocusRequester: FocusRequester,
) {
    val soulMeta by com.fenglingcode.app.agent.SoulStore
        .cachedMetadata.collectAsState()
    // [风铃code] Recent sessions for the "最近" strip. Scoped INSIDE the
    // empty-state overlay (not at ChatScreen top level) so an open, non-empty
    // chat doesn't keep a live observeSessionsSorted subscription alive purely
    // for a screen that isn't showing. Reuses the same pinned-first,
    // updated_at DESC ordering as the drawer, so the top of the list is exactly
    // the last-touched chats.
    val welcomeRecentSessions by chatRepository.dao.observeSessionsSorted()
        .collectAsState(initial = emptyList())
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        SoulIconGlyph(
            icon = soulMeta.icon,
            sizeDp = 60.dp,
            emojiSp = 40.sp,
            sparkleTint = Brush.linearGradient(
                listOf(SparkleColor1, SparkleColor2),
            ),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.welcome_greeting, soulMeta.name),
            style = MaterialTheme.typography.titleLarge,
            color = ChatColors.primaryText,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = ChatColors.secondaryText,
        )
        Spacer(modifier = Modifier.height(28.dp))
        listOf(
            R.string.welcome_prompt_1,
            R.string.welcome_prompt_2,
            R.string.welcome_prompt_3,
            R.string.welcome_prompt_4,
        ).forEach { resId ->
            val label = stringResource(resId)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        ChatColors.secondaryBg.copy(alpha = 0.6f),
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        BorderStroke(0.5.dp, ChatColors.toolBorder),
                        RoundedCornerShape(14.dp),
                    )
                    .clickable {
                        viewModel.setInputText(label)
                        try {
                            inputFocusRequester.requestFocus()
                        } catch (_: IllegalStateException) {
                        }
                        keyboardController?.show()
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChatColors.primaryText,
                )
            }
        }
        // [风铃code] Recent strip: exclude the current draft session;
        // observeSessionsSorted already orders pinned first then updated_at
        // DESC, so first 3 = the chats the user just touched.
        val recents = welcomeRecentSessions
            .filter { it.id != sessionId }
            .take(3)
        if (recents.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.welcome_recent_title),
                style = MaterialTheme.typography.labelMedium,
                color = ChatColors.tertiaryText,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
            )
            recents.forEach { s ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSessionSelected(s.id) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = s.title?.takeIf { it.isNotBlank() }
                            ?: stringResource(R.string.new_chat),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChatColors.secondaryText,
                    )
                }
            }
        }
    }
}
