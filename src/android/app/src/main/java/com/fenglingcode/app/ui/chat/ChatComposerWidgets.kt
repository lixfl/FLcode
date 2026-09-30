package com.fenglingcode.app.ui.chat

// [T-android-split-chat] Composer/input widgets + tool preview/status bar
// extracted verbatim from ChatScreen.kt: AttachmentChip, InputCircleButton,
// MicButton, FloatingToolStatusBar, ThinkingLevelPicker.
// Full import block copied (unused=warnings); externally-called ones internal.

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import com.fenglingcode.app.ui.theme.AppIcons
import java.io.File
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import com.fenglingcode.app.BuildConfig
import com.fenglingcode.app.R
import com.fenglingcode.app.data.FileMentionIndex
import com.fenglingcode.app.logging.AppLogger
import com.fenglingcode.app.ui.components.FenglingAlertDialog
import com.fenglingcode.app.ui.components.FenglingMenu
import com.fenglingcode.app.ui.components.FenglingMenuDivider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.produceState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.fenglingcode.app.offload.OffloadPermissionManager
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import com.fenglingcode.app.data.model.LLMModel
import com.fenglingcode.app.data.model.ModelEntry
import com.fenglingcode.app.data.model.ModelGroup
import com.fenglingcode.app.data.model.ProviderConfig
import com.fenglingcode.app.data.model.ProviderType
import com.fenglingcode.app.data.model.RoutingStrategy
import com.fenglingcode.app.data.model.ThinkingLevel
import com.fenglingcode.app.data.repository.ChatRepository
import com.fenglingcode.app.data.repository.MemoryRepository
import com.fenglingcode.app.data.repository.ProviderRepository
import com.fenglingcode.app.ui.browser.BrowserSheet
import com.fenglingcode.app.ui.theme.ChatColors
import com.fenglingcode.app.ui.components.FenglingTextButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AttachmentChip(
    attachment: InputAttachment,
    onRemove: () -> Unit,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
) {
    // iOS UserAttachmentList parity: 64dp chip + xmark.circle.fill remove
    // badge at the top-right that sits HALF on the chip and HALF outside
    // (a 20dp circle offset by -6dp / -6dp). The outer Box is sized 72dp
    // so the badge isn't clipped; the chip itself stays exactly 64dp,
    // padded into the Box at TopStart.
    val chipShape = RoundedCornerShape(8.dp)
    // Outer Box gives the badge room to "spill out" past the chip's
    // top-right corner without being clipped: chip is 64dp at TopStart,
    // badge is 20dp at TopEnd, so the outer needs 64 + half(badge) ≈ 72dp
    // wide and ≈ 70dp tall.
    Box(modifier = Modifier.size(width = 72.dp, height = 70.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .size(64.dp)
                // Tap the chip body (NOT the remove badge — that lives in
                // the outer Box) to preview the attachment, mirroring iOS
                // InputAttachmentTile.onTapGesture in AIChatView.swift:3699.
                .clip(chipShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            if (attachment.isImage) {
                AsyncImage(
                    model = attachment.uri,
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(chipShape)
                        .border(1.dp, ChatColors.thumbnailBorder, chipShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // File chip (iOS: icon + filename inside a tinted square)
                Column(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant, chipShape)
                        .border(1.dp, ChatColors.thumbnailBorder, chipShape),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        AppIcons.InsertDriveFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = attachment.fileName,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        // Remove badge (iOS: xmark.circle.fill at the chip's top-right
        // corner, sitting half on / half off the thumbnail). Hairline
        // border keeps the badge readable against image content.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .border(0.5.dp, ChatColors.thumbnailBorder, CircleShape)
                .clip(CircleShape)
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AppIcons.Close,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.size(13.dp),
            )
        }
    }
}

// ─── Input Circle Button (iOS: 34×34 circle, secondary bg + border) ─────────

@Composable
internal fun InputCircleButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(ChatColors.inputIconBg, CircleShape)
            .border(0.5.dp, ChatColors.inputIconBorder, CircleShape)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Mic button with a two-state appearance — mirrors iOS `MicButton`.
 *
 * Idle: outlined mic, neutral bg.
 * Recording: filled mic, red-tinted bg, optional 2-letter locale badge
 * overlayed on the top-right (e.g. "EN", "ZH").
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun MicButton(
    isRecording: Boolean,
    localeBadge: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    // [T-android-voice-panel] While the inline voice panel is active the same
    // slot switches back to text input — keyboard glyph (mirrors iOS "T").
    isVoiceActive: Boolean = false,
) {
    val bg = if (isRecording) Color.Red.copy(alpha = 0.15f)
             else ChatColors.inputIconBg
    val tint = if (isRecording) Color.Red
               else MaterialTheme.colorScheme.onSurfaceVariant
    val borderColor = if (isRecording) Color.Transparent else ChatColors.inputIconBorder
    Box(
        modifier = Modifier
            .size(38.dp)
            .background(bg, CircleShape)
            .border(0.5.dp, borderColor, CircleShape)
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isVoiceActive) AppIcons.Keyboard else AppIcons.Mic,
            contentDescription = if (isVoiceActive) "Switch to keyboard"
            else if (isRecording) "Stop recording" else "Voice input",
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        if (!localeBadge.isNullOrEmpty()) {
            Text(
                text = localeBadge,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 2.dp)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

// [风铃code] ToolPreviewThumbnail removed with the tool preview window.

// ─── Floating Tool Status Bar (iOS: thumbnail + status capsule + pagination) ─

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FloatingToolStatusBar(
    toolBlocks: List<AssistantBlock>,
    onStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    onOpenTerminalWithCommand: (String) -> Unit = {},
    // T261: detail open routes through ChatViewModel state so this bar
    // shares one always-mounted sheet with the in-list pills (no more
    // dueling local-remember sheets, no LaunchedEffect(lastIndex) page
    // jumps when a new tool starts mid-view).
    onOpenDetail: (String) -> Unit = {},
) {
    var currentIndex by remember { mutableStateOf(toolBlocks.lastIndex.coerceAtLeast(0)) }
    val lastIndex = toolBlocks.lastIndex
    LaunchedEffect(lastIndex) {
        val block = toolBlocks.getOrNull(currentIndex)
        val isCurrentActive = block?.toolStatus == ToolBlockStatus.RUNNING ||
            block?.toolStatus == ToolBlockStatus.STREAMING ||
            block?.toolStatus == ToolBlockStatus.PENDING
        if (!isCurrentActive) currentIndex = lastIndex.coerceAtLeast(0)
    }
    val block = toolBlocks.getOrNull(currentIndex) ?: return
    // T261: sheet is hoisted to ChatScreen top-level; this bar only emits
    // open events. The current-displayed block id is what we want shown
    // when the user opens the sheet from this surface (not necessarily
    // the latest tool — the user may have paged via the chevrons).
    val onOpenCurrentDetail: () -> Unit = { onOpenDetail(block.id) }

    val isDone = block.toolStatus == ToolBlockStatus.SUCCESS
    val isFailed = block.toolStatus == ToolBlockStatus.FAILED ||
        block.toolStatus == ToolBlockStatus.TIMEOUT
    val isCancelled = block.toolStatus == ToolBlockStatus.CANCELLED
    val isRunning = block.toolStatus == ToolBlockStatus.RUNNING ||
        block.toolStatus == ToolBlockStatus.STREAMING ||
        block.toolStatus == ToolBlockStatus.PENDING
    val toolAccent = toolAccentColor(block.toolName)
    // [风铃code] Tool preview window removed by user request. The status bar
    // no longer reserves space for a thumbnail and always starts at 12 dp.

    // iOS layout: ZStack(alignment: .bottomLeading)
    // [风铃code] Thumbnail removed — only the 38dp status bar remains.
    val barHeight = 38.dp

    Box(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        // Layer 1: Status bar (bottom layer, fills from bottom)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .align(Alignment.BottomCenter)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(10.dp), ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.12f))
                .background(ChatColors.inputBg, RoundedCornerShape(10.dp))
                .border(0.5.dp, ChatColors.toolBorder, RoundedCornerShape(10.dp))
                .padding(start = 12.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status icon
            if (isRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = toolAccent,
                    strokeWidth = 1.5.dp,
                )
            } else {
                val (icon, tint) = when {
                    isDone -> AppIcons.CheckCircle to ToolCheckColor
                    isFailed -> AppIcons.Error to ToolErrorColor
                    isCancelled -> AppIcons.Close to ToolCancelColor
                    else -> AppIcons.Build to MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
            }

            Spacer(modifier = Modifier.width(6.dp))

            // [T-step-timestamp v2 aa8b1128] Inline HH:mm:ss prefix
            // removed — see ToolDetailSheet header for the new
            // "HH:mm:ss · 3s" display, surfaced only inside the
            // tapped-open detail sheet so the always-visible status bar
            // stays clean.

            // Tool title
            Text(
                text = block.toolTitle.ifEmpty { block.toolName },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = ChatColors.primaryText,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onOpenCurrentDetail() },
                overflow = TextOverflow.Ellipsis,
            )

            // Pagination
            if (toolBlocks.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    Icon(
                        AppIcons.ChevronLeft,
                        contentDescription = "Previous",
                        tint = if (currentIndex > 0) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                enabled = currentIndex > 0,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { if (currentIndex > 0) currentIndex-- },
                    )
                    Text(
                        "${currentIndex + 1}/${toolBlocks.size}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        AppIcons.ChevronRight,
                        contentDescription = "Next",
                        tint = if (currentIndex < toolBlocks.lastIndex) MaterialTheme.colorScheme.onSurface
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                enabled = currentIndex < toolBlocks.lastIndex,
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) { if (currentIndex < toolBlocks.lastIndex) currentIndex++ },
                    )
                }
            }
            // T170: stop button removed from the floating bar — only the
            // in-list ToolCallPill keeps the red square. Two stop affordances
            // on the same active tool felt redundant on tight screens.
        }

        // [风铃code] Layer 2 (Tool preview thumbnail) removed — user doesn't
        // want the tool-browsing window at all.
    }
}

/**
 * Inline 5-segment picker rendered on the right side of the `/thinking` row.
 * Mirrors iOS `thinkingLevelPicker`: the active level shows a filled pill;
 * the OFF pill uses a muted background, the others use the accent color.
 */
@Composable
internal fun ThinkingLevelPicker(
    current: ThinkingLevel,
    // [T-android-thinking-level-arch] Levels the CURRENT model actually supports
    // (OFF + everything up to its effectiveMaxThinkingLevel). Passed in so the
    // picker only ever offers reachable tiers; the row scrolls horizontally so
    // the extra GPT-5.6 tiers (MAX/ULTRA) don't overflow a narrow composer.
    availableLevels: List<ThinkingLevel>,
    onSelect: (ThinkingLevel) -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    // [T-android-thinking-level-arch] Clamped state: the stored level is higher
    // than what the current model can reach (e.g. ULTRA persisted, then the user
    // switched to DeepSeek which caps at XHIGH). `current` then isn't in
    // availableLevels, so no capsule would match `level == current` and the row
    // would look entirely unselected — as if thinking were off. Mirror iOS
    // (fb349342): highlight the highest available capsule in orange with an
    // up-arrow, signalling "your setting is higher, this model caps here".
    val maxAvailable = availableLevels.lastOrNull { it != ThinkingLevel.OFF }
    val isClamped = current.isEnabled && maxAvailable != null && current.rank > maxAvailable.rank
    val clampOrange = Color(0xFFFF9500)
    Row(
        modifier = Modifier
            .background(
                ChatColors.secondaryText.copy(alpha = 0.12f),
                RoundedCornerShape(6.dp),
            )
            .clip(RoundedCornerShape(6.dp))
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        availableLevels.forEach { level ->
            val isExactMatch = level == current
            val isClampedHighlight = isClamped && level == maxAvailable
            val isHighlighted = isExactMatch || isClampedHighlight
            val bg = when {
                // [T-android-thinking-picker-ui] Clamped tier → orange; normal
                // selection → blue (ChatColors.thinking, the theme-adaptive
                // system blue 007AFF/0A84FF), matching iOS's Color.blue. The
                // old ChatColors.sendButton was black in light / white in dark,
                // so the selected capsule read as unselected. (OFF no longer
                // appears in availableLevels, so its former branch is gone.)
                isClampedHighlight -> clampOrange.copy(alpha = 0.75f)
                isHighlighted -> ChatColors.thinking
                else -> Color.Transparent
            }
            // White text on both the blue and orange fills (readable in both
            // themes); grey when unselected.
            val fg = if (isHighlighted) Color.White else ChatColors.secondaryText
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(bg)
                    // [T-android-thinking-level-arch] Tapping the already-
                    // highlighted capsule toggles thinking OFF (covers both exact
                    // and clamped highlight); otherwise selects the tapped level.
                    .clickable { onSelect(if (isHighlighted) ThinkingLevel.OFF else level) }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Text(
                    text = level.localizedName(context),
                    fontSize = 11.sp,
                    fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    color = fg,
                )
                if (isClampedHighlight) {
                    Icon(
                        imageVector = AppIcons.KeyboardArrowUp,
                        contentDescription = null,
                        tint = fg,
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
        }
    }
}
