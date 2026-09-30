package com.fenglingcode.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.fenglingcode.app.deeplink.DeepLinkAction
import com.fenglingcode.app.deeplink.DeepLinkCoordinator
import com.fenglingcode.app.ui.settings.getAppearancePrefs
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.dialog
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fenglingcode.app.data.repository.ChatRepository
import com.fenglingcode.app.data.repository.ProviderRepository
import com.fenglingcode.app.ui.chat.ChatScreen
import com.fenglingcode.app.ui.sessions.SessionListScreen

import com.fenglingcode.app.ui.settings.AddAgentLoopGroupsScreen
import com.fenglingcode.app.ui.settings.AddAgentLoopModelsScreen
import com.fenglingcode.app.ui.settings.AddCustomModelScreen
import com.fenglingcode.app.ui.settings.BackgroundSettingsScreen
import com.fenglingcode.app.ui.settings.AddModelsToGroupScreen
import com.fenglingcode.app.ui.settings.ShadowVoiceDetailScreen
import com.fenglingcode.app.ui.settings.AddProviderScreen
import com.fenglingcode.app.ui.settings.ModelEntryDetailScreen
import com.fenglingcode.app.ui.settings.ModelGroupDetailScreen
import com.fenglingcode.app.ui.settings.ModelGroupsScreen
import com.fenglingcode.app.ui.settings.ProviderDetailScreen
import com.fenglingcode.app.ui.settings.ProviderListScreen
import com.fenglingcode.app.ui.sandbox.FileBrowserScreen
import com.fenglingcode.app.ui.sandbox.FileBrowserViewModel
import com.fenglingcode.app.ui.sandbox.FileItem
import com.fenglingcode.app.ui.sandbox.FilePreviewScreen
import com.fenglingcode.app.ui.sandbox.RootfsManagementScreen
import com.fenglingcode.app.ui.settings.EnvironmentVariablesScreen
import com.fenglingcode.app.ui.settings.AppearanceScreen
import com.fenglingcode.app.ui.settings.SettingsScreen
import com.fenglingcode.app.ui.settings.SystemPermissionsScreen
import com.fenglingcode.app.ui.settings.SessionStorageDetailScreen
import com.fenglingcode.app.ui.settings.SkillDetailScreen
import com.fenglingcode.app.ui.settings.StorageManagementScreen
import com.fenglingcode.app.ui.settings.SkillFileViewerScreen
import com.fenglingcode.app.ui.settings.UsageStatsScreen
import com.fenglingcode.app.ui.settings.FenglingSkillsBrowserScreen
import com.fenglingcode.app.ui.settings.MountDetailScreen
import com.fenglingcode.app.ui.settings.MountedFoldersScreen
import com.fenglingcode.app.ui.settings.SharedFolderDetailScreen
import com.fenglingcode.app.ui.settings.SharedFoldersScreen
import com.fenglingcode.app.ui.settings.SkillsManagementScreen
import com.fenglingcode.app.data.repository.EnvVarRepository
import com.fenglingcode.app.data.repository.MemoryRepository
import com.fenglingcode.app.data.repository.SkillRepository
import com.fenglingcode.app.ui.settings.LogDetailScreen
import com.fenglingcode.app.ui.settings.LogManagementScreen
import com.fenglingcode.app.ui.settings.MemoryFileEditScreen
import com.fenglingcode.app.ui.settings.MemoryManagementScreen
import com.fenglingcode.app.ui.settings.OffloadPermissionScreen
import com.fenglingcode.app.ui.settings.ShizukuPermissionScreen
import com.fenglingcode.app.sandbox.RootfsManager
import com.fenglingcode.app.sandbox.TerminalSession
import com.fenglingcode.app.ui.terminal.TerminalScreen
import com.fenglingcode.app.ui.onboarding.OnboardingModelSelectionScreen

// T342: Material 3 motion easing curves. Compose-Material3 (1.3.x) ships
// `MotionScheme` only in 1.4-alpha; mirror the spec values directly so we
// don't take a dependency-bump tax just for two CubicBezierEasing instances.
// Source: m3.material.io/styles/motion/easing-and-duration/tokens-specs
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)

object Routes {
    const val SESSION_LIST = "sessions"
    const val CHAT = "chat/{sessionId}"
    const val SETTINGS = "settings"
    const val PROVIDER_LIST = "providers"
    const val ADD_PROVIDER = "add_provider"
    const val PROVIDER_DETAIL = "provider/{instanceId}"
    /** [T-android-provider-voice] Read-only shadow Voice Service detail. */
    const val SHADOW_VOICE_DETAIL = "voice_service/{instanceId}"
    const val MODEL_GROUPS = "model_groups"
    const val MODEL_GROUP_DETAIL = "model_group/{groupId}"
    const val ADD_MODELS_TO_GROUP = "add_models_to_group/{groupId}"
    /** T185: picker that adds model *entries* to the agent-loop set. */
    const val ADD_MODELS_TO_AGENT_LOOP = "add_models_to_agent_loop"
    /** T185: picker that adds model *groups* to the agent-loop set. */
    const val ADD_GROUPS_TO_AGENT_LOOP = "add_groups_to_agent_loop"
    /** T171→T182: AGENT_LOOP_MODELS deprecated (the screen lived inside
     *  Settings, now the picker is a section inside ModelGroupsScreen).
     *  Route declared so any back-compat deep-link string from preview
     *  builds pops back instead of crashing. */
    const val AGENT_LOOP_MODELS = "agent_loop_models"
    const val MODEL_ENTRY_DETAIL = "model_entry/{instanceId}/{entryId}"
    const val ADD_CUSTOM_MODEL = "add_custom_model/{instanceId}"
    const val STORAGE = "storage"
    const val BACKUP = "backup"
    const val BACKUP_DESTINATIONS = "backup_destinations"
    const val BACKUP_HISTORY_DETAIL = "backup_history_detail"
    const val BACKUP_DESTINATION_BROWSE = "backup_destination_browse"
    const val RESTORE_BROWSE = "restore_browse"
    // [T-android-restore-server-list] The servers you can restore FROM, plus
    // an Add Server entry. Separate from BACKUP_DESTINATIONS, which is the
    // editable management screen for the same servers.
    const val RESTORE_SERVERS = "restore_servers"
    const val SESSION_STORAGE_DETAIL = "session_storage/{sessionId}"
    const val ROOTFS_MANAGEMENT = "rootfs_management"
    const val MIRROR_CATEGORY_DETAIL = "mirror_category/{categoryKey}"
    fun mirrorCategoryDetail(categoryKey: String) = "mirror_category/$categoryKey"
    const val FILE_BROWSER = "file_browser"
    const val FILE_PREVIEW = "file_preview"
    const val ENV_VARS = "env_vars"
    const val SKILLS = "skills"
    const val SKILL_DETAIL = "skill/{skillId}"
    const val SKILL_FILE = "skill_file/{skillId}/{relativePath}"
    const val MINIS_SKILLS_BROWSER = "fengling_skills_browser"

    fun skillDetail(skillId: String) = "skill/$skillId"
    fun skillFile(skillId: String, relativePath: String = "SKILL.md"): String {
        // Path may contain `/`, which the nav library treats as a route
        // separator. URL-encode so subdirectory paths survive a round-trip.
        val encoded = java.net.URLEncoder.encode(relativePath, "UTF-8").replace("+", "%20")
        return "skill_file/$skillId/$encoded"
    }
    const val TERMINAL = "terminal?initCommand={initCommand}&sessionId={sessionId}"
    fun terminal(initCommand: String? = null, sessionId: String? = null): String {
        // URLEncoder follows application/x-www-form-urlencoded — spaces become `+`.
        // Nav library only %-decodes the route, so `+` would reach the screen literally.
        // Replace `+` with `%20` so Nav decodes it back to a space.
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        val params = buildList {
            if (initCommand != null) add("initCommand=${enc(initCommand)}")
            if (sessionId != null) add("sessionId=${enc(sessionId)}")
        }
        return if (params.isEmpty()) "terminal" else "terminal?${params.joinToString("&")}"
    }
    /** Chat-files browser: opens FileBrowser rooted at /var/fengling for the session. */
    const val CHAT_FILES = "chat_files/{sessionId}"
    fun chatFiles(sessionId: String) = "chat_files/$sessionId"
    const val MEMORY = "memory"
    /** [T-mcp-integration-android] MCP Integrations management screen. */
    const val MCP = "mcp"
    /** [T-soul-md] SOUL.md editor. */
    const val SOUL = "soul"
    const val MEMORY_FILE_EDIT = "memory_file/{fileName}/{isGlobal}"
    const val PERMISSIONS = "permissions"
    /**
     * T322 / [T-android-privileged-backend]: Shizuku-protocol manager
     * walkthrough — handles both Shizuku and AXManager (they share the same
     * binder protocol + client SDK).
     */
    const val SHIZUKU = "shizuku"
    /** T323: System Permissions (Accessibility service status, etc.). */
    const val SYSTEM_PERMISSIONS = "system_permissions"
    const val USAGE_STATS = "usage_stats"
    const val LOGS = "logs"
    const val LOG_DETAIL = "log_detail/{fileName}"
    const val APPEARANCE = "appearance"
    const val BACKGROUND = "background"
    const val ONBOARDING_MODELS = "onboarding_models"
    /** T219-2: Mount external folders settings + detail. */
    const val MOUNTED_FOLDERS = "mounted_folders"
    const val MOUNTED_FOLDERS_DETAIL = "mounted_folders_detail/{mountId}"
    fun mountedFoldersDetail(mountId: String) = "mounted_folders_detail/$mountId"
    /** T235: Shared folders (Shared / Skills / Memory) — fixed list. */
    const val SHARED_FOLDERS = "shared_folders"
    const val SHARED_FOLDERS_DETAIL = "shared_folders_detail/{folderId}"
    fun sharedFoldersDetail(folderId: String) = "shared_folders_detail/$folderId"
    /** [T-android-scheduled-tasks-design] Scheduled tasks list + editor. */
    const val SCHEDULED_TASKS = "scheduled_tasks"
    const val SCHEDULED_TASK_EDIT = "scheduled_tasks/edit?taskId={taskId}"
    fun scheduledTaskEdit(taskId: String? = null): String =
        if (taskId == null) "scheduled_tasks/edit" else "scheduled_tasks/edit?taskId=$taskId"
    // [T-android-scheduled-tasks-run-records] per-task execution log.
    const val SCHEDULED_TASK_RUNS = "scheduled_tasks/runs/{taskId}"
    fun scheduledTaskRuns(taskId: String): String = "scheduled_tasks/runs/$taskId"

    fun logDetail(fileName: String) = "log_detail/$fileName"
    fun sessionStorageDetail(sessionId: String) = "session_storage/$sessionId"
    fun memoryFileEdit(fileName: String, isGlobal: Boolean) = "memory_file/$fileName/$isGlobal"
    fun chat(sessionId: String) = "chat/$sessionId"
    fun providerDetail(instanceId: String) = "provider/$instanceId"
    fun shadowVoiceDetail(instanceId: String) = "voice_service/$instanceId"
    fun modelGroupDetail(groupId: String) = "model_group/$groupId"
    fun addModelsToGroup(groupId: String) = "add_models_to_group/$groupId"
    // [T-android-model-entry-route-slash-crash] entryId is a composite key
    // "<instanceId>/<modelId>" (compositeEntryKey) — it CONTAINS a '/'. Left
    // raw, that slash splits the route into an extra path segment, so the
    // built route no longer matches the registered MODEL_ENTRY_DETAIL pattern
    // (model_entry/{instanceId}/{entryId}) and navigate() throws
    // IllegalArgumentException "destination … cannot be found" — a guaranteed
    // crash on tapping any model whose id carries a '/'. URL-encode it so the
    // slash becomes %2F (one segment); the receiver decodes it back.
    fun modelEntryDetail(instanceId: String, entryId: String) =
        "model_entry/${android.net.Uri.encode(instanceId)}/${android.net.Uri.encode(entryId)}"
    fun addCustomModel(instanceId: String) = "add_custom_model/$instanceId"
}

/** Holder for file preview navigation state (not serializable via nav args). */
internal object FilePreviewHolder {
    var currentItem: FileItem? = null
    var fileBrowserViewModel: FileBrowserViewModel? = null
}

@Composable
fun AppNavigation(
    chatRepository: ChatRepository,
    providerRepository: ProviderRepository,
    envVarRepository: EnvVarRepository? = null,
    skillRepository: SkillRepository? = null,
    mcpRepository: com.fenglingcode.app.data.repository.MCPRepository? = null,
    memoryRepository: MemoryRepository? = null,
    navController: NavHostController = rememberNavController(),
    initialDeepLink: DeepLinkAction? = null,
) {
    val context = LocalContext.current

    // T219-5: use the application-scoped singleton from FenglingApp so UI
    // add/remove shares state with PRootKernel and the lifecycle re-probe
    // path. Pre-T219-5 this `remember { MountedFoldersStore(...) }` created
    // a SECOND independent instance — UI list updated but PRoot never
    // saw the change because PRootKernel.mountedFoldersStore pointed at
    // the application-scoped singleton in FenglingApp.
    val mountedFoldersStore = remember {
        (context.applicationContext as com.fenglingcode.app.FenglingApp).mountedFoldersStore
    }

    // Handle initial deep link after composition
    LaunchedEffect(initialDeepLink) {
        when (initialDeepLink) {
            is DeepLinkAction.OpenTerminal -> {
                navController.safeNavigate(Routes.terminal(initialDeepLink.initCommand))
            }
            is DeepLinkAction.OpenSession -> {
                // [T-fengling-no-home] On process-death recreation NavController
                // auto-restores [SESSION_LIST, chat/<id>] AND MainActivity
                // synthesizes an OpenSession deep-link. SESSION_LIST is now just
                // a blank start placeholder, so pop it inclusive=true — the chat
                // becomes the sole root and back exits rather than revealing a
                // blank frame. launchSingleTop still collapses the duplicate the
                // synthesized deep-link would otherwise push.
                navController.safeNavigate(Routes.chat(initialDeepLink.sessionId)) {
                    popUpTo(Routes.SESSION_LIST) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is DeepLinkAction.CreateEnvironmentVariable -> {
                DeepLinkCoordinator.setPendingEnvVarCreate(
                    initialDeepLink.key,
                    initialDeepLink.value,
                    initialDeepLink.note,
                )
                navController.safeNavigate(Routes.ENV_VARS)
            }
            // T183: any settings screen reachable by route string. The
            // parser already resolved the path → route mapping so we
            // just navigate.
            is DeepLinkAction.OpenSettingsScreen -> {
                navController.safeNavigate(initialDeepLink.route)
            }
            is DeepLinkAction.OpenPermissionSettings -> {
                navController.safeNavigate(Routes.PERMISSIONS)
            }
            // OpenHtmlPreview is handled by setting startDestination
            // (see below) so the NavHost mounts directly into the right
            // chat — no safeNavigate dance, no sessions-list flash.
            is DeepLinkAction.OpenHtmlPreview -> {}
            // App-icon quick actions: all three open a fresh draft chat.
            // Voice/camera additionally seed DeepLinkCoordinator.pendingChatAction
            // (done up-front in startDestination block below so the seed
            // lands before ChatScreen's first compose).
            is DeepLinkAction.NewChat,
            is DeepLinkAction.NewVoiceChat,
            is DeepLinkAction.NewCameraChat -> {
                // Navigation handled by startDestination = chat/<__new__…>
                // when the launch intent carries one of these actions.
                // Nothing to do here — see startDestination block below.
            }
            else -> {}
        }
    }

    // Resolve launch session preference once into a deferred navigation target.
    // T314: this LaunchedEffect fires the same frame the NavHost mounts, so
    // the SESSION_LIST start-destination's NavBackStackEntry is still in
    // STARTED state when we'd otherwise call navigate(). safeNavigate() —
    // designed to defang the back-then-tap race — early-returns whenever
    // the current entry isn't RESUMED, which silently dropped every
    // launch-session navigation and left the user on the home screen
    // regardless of mode 1 / 2 / 0. Wait for the start destination to
    // settle into RESUMED before navigating; for mode 3 (Home) we don't
    // need to navigate at all so we can skip the wait entirely.
    LaunchedEffect(Unit) {
        val hasDeepLink = initialDeepLink != null && initialDeepLink !is DeepLinkAction.Unknown
        if (hasDeepLink) return@LaunchedEffect
        val hasPendingShare =
            com.fenglingcode.app.share.ShareCoordinator.bufferVersion.value > 0
        // [T-fengling-launch] Simplified launch resolver: always open the last session.
        // The "Launch Session" setting has been removed, so we no longer honor mode preferences.
        // Circuit breakers (HangDetector, LaunchCycleBeacon) still apply:
        // if any trip, open a fresh draft instead of the potentially problematic last session.
        val forceNewDraft = com.fenglingcode.app.diagnostics.HangDetector.shouldForceHomeOnLaunch(context) ||
            com.fenglingcode.app.diagnostics.LaunchCycleBeacon.shouldForceHomeOnLaunch()
        val hasAnySession = chatRepository.dao.listSessions().isNotEmpty()
        val hasAnyProvider = providerRepository.instances.isNotEmpty()
        com.fenglingcode.app.logging.AppLogger.info(
            "XSessionDiag",
            "[XSessionDiag] launch: forceNewDraft=$forceNewDraft hasPendingShare=$hasPendingShare " +
                "hasAnySession=$hasAnySession hasAnyProvider=$hasAnyProvider",
        )
        // [风铃code] The session the user last OPENED (browsed), distinct from
        // "most recently updated". Validated against the live session list so a
        // deleted / synced-away id falls through to the recency fallback rather
        // than navigating to a dead chat.
        val lastOpenedId = com.fenglingcode.app.agent.UserProfile.lastOpenedSession(context)
        val target: String? = when {
            // [T-fengling-no-home] The onboarding home was deleted: a virgin
            // device (no sessions, no providers) now opens a draft chat too.
            // Providers are reachable from Settings via the drawer's Settings
            // entry; the model picker in the composer is the in-chat path.
            // Circuit breaker tripped or pending share: open a fresh draft.
            forceNewDraft || hasPendingShare -> Routes.chat("__new__${java.util.UUID.randomUUID()}")
            // Prefer the explicitly last-opened session, if it still exists.
            lastOpenedId != null &&
                (chatRepository.dao.getSession(lastOpenedId) != null) -> Routes.chat(lastOpenedId)
            // Fallback: the most recently updated session, or a new draft if none.
            else -> chatRepository.dao.listSessions().firstOrNull()?.let { Routes.chat(it.id) }
                ?: Routes.chat("__new__${java.util.UUID.randomUUID()}")
        }
        if (target != null) {
            // [T-fengling-no-home] navigate() replaces the blank boot start
            // destination (popUpTo … inclusive = true) so the chat becomes the
            // root of the back stack. Hardware back from a chat then exits the
            // app instead of revealing a session-list home, which no longer
            // exists. The start destination has not rendered yet on this first
            // composition pass, so there is no transition to flash.
            navController.navigate(target) {
                popUpTo(Routes.SESSION_LIST) { inclusive = true }
            }
        }
    }

    // T185: warm-share fallback — if a share lands while the user is sitting
    // on the session list (cold-start with mode 3 that raced past the launch
    // resolver, or onNewIntent re-fires processPendingShare), route into a
    // fresh chat so ChatScreen's existing LaunchedEffect(shareBufferVersion)
    // can drain it. The drain itself is idempotent: consumeBuffer is one-shot,
    // so a ChatScreen already in the backstack won't double-inject.
    val shareBufferVersion by com.fenglingcode.app.share.ShareCoordinator.bufferVersion.collectAsState()
    LaunchedEffect(shareBufferVersion) {
        if (shareBufferVersion == 0) return@LaunchedEffect
        val current = navController.currentDestination?.route ?: return@LaunchedEffect
        if (current == Routes.SESSION_LIST) {
            navController.safeNavigate(Routes.chat("__new__${java.util.UUID.randomUUID()}")) {
                popUpTo(Routes.SESSION_LIST) { inclusive = true }
            }
        }
    }

    // Pinned-shortcut cold start: when launched via
     // `fengling://session/<id>/<resource-path>`, set the pending HTML
     // preview synchronously and start NavHost directly at the matching
     // chat so ChatScreen's LaunchedEffect consumes the pending state on
     // first composition — no sessions-list flash, no launch-session
     // preference detour.
    val htmlShortcut = initialDeepLink as? DeepLinkAction.OpenHtmlPreview
    // App-icon quick action cold start: mount NavHost directly at a fresh
    // draft chat, seeding the pending action so ChatScreen consumes it on
    // its first LaunchedEffect tick. Mirrors the htmlShortcut path —
    // avoids a sessions-list flash and a duplicate back-stack entry.
    val quickActionStart: String? = when (initialDeepLink) {
        is DeepLinkAction.NewVoiceChat -> {
            DeepLinkCoordinator.setPendingChatAction(
                DeepLinkCoordinator.ChatAction.START_VOICE,
            )
            Routes.chat("__new__${java.util.UUID.randomUUID()}")
        }
        is DeepLinkAction.NewCameraChat -> {
            DeepLinkCoordinator.setPendingChatAction(
                DeepLinkCoordinator.ChatAction.OPEN_CAMERA,
            )
            Routes.chat("__new__${java.util.UUID.randomUUID()}")
        }
        is DeepLinkAction.NewChat -> Routes.chat("__new__${java.util.UUID.randomUUID()}")
        else -> null
    }
    val startDestination = when {
        htmlShortcut != null -> {
            // Seed coordinator before NavHost composition so ChatScreen sees
            // the pending state on its very first LaunchedEffect tick.
            DeepLinkCoordinator.setPendingHtmlPreview(
                htmlShortcut.sessionId,
                htmlShortcut.resourcePath,
                htmlShortcut.title,
            )
            Routes.chat(htmlShortcut.sessionId)
        }
        quickActionStart != null -> quickActionStart
        else -> Routes.SESSION_LIST
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        // T153: paint the in-app theme color underneath every transition
        // frame. Without this the NavHost's transition surface is
        // transparent and the window background bleeds through during
        // enter/exit animations — on dark mode the base Light window
        // background flashes white between screens. Tying the host to
        // the active Material colorScheme also keeps the first frame
        // correct on cold start.
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        // T342: Material 3 motion — shared-axis X transition.
        // Spec (m3.material.io/styles/motion/transitions):
        //   - enter uses EmphasizedDecelerate (cubic-bezier 0.05, 0.7, 0.1, 1.0)
        //     so the new destination eases in confidently
        //   - exit uses EmphasizedAccelerate (cubic-bezier 0.3, 0.0, 0.8, 0.15)
        //     so the leaving destination clears out fast
        //   - both legs together feel like a single 300ms motion (200ms
        //     exit overlapping 300ms enter), short enough that a rapid second
        //     tap still lands on the next destination once safeNavigate's
        //     RESUMED guard releases
        //   - the small slide distance (~SlideDirection default ≈ container
        //     width ÷ N — Compose's slideIntoContainer already picks a
        //     subtle distance) plus fade reads as a single coordinated
        //     motion rather than a hard cut, matching Settings/Files in
        //     Material You system apps.
        // RESUMED guard via safeNavigate + the colorScheme.background
        // modifier above (T333) still defend against rapid-tap white
        // flashes; the slightly longer enter spec (300ms vs 220ms) is
        // covered by the same guard.
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(300, easing = EmphasizedDecelerate),
            ) + fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerate))
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = tween(200, easing = EmphasizedAccelerate),
            ) + fadeOut(animationSpec = tween(200, easing = EmphasizedAccelerate))
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(300, easing = EmphasizedDecelerate),
            ) + fadeIn(animationSpec = tween(300, easing = EmphasizedDecelerate))
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = tween(200, easing = EmphasizedAccelerate),
            ) + fadeOut(animationSpec = tween(200, easing = EmphasizedAccelerate))
        },
    ) {
        // [T-fengling-no-home] The session-list HOME SCREEN is deleted. This
        // route survives ONLY as the NavHost start-destination placeholder —
        // it must exist so popUpTo(SESSION_LIST) references and process-death
        // state restore keep resolving, and the launch LaunchedEffect above
        // replaces it (inclusive = true) with a chat within the first frame.
        // It renders a bare themed surface: if it ever composes (should be at
        // most one frame), the user sees blank background, not a list.
        // SessionListScreen itself remains in the codebase only as the TABLET
        // two-pane list pane (ChatSplitScaffoldRoute.listPane).
        composable(Routes.SESSION_LIST) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {}
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            ChatSplitScaffoldRoute(
                initialSessionId = sessionId,
                navController = navController,
                chatRepository = chatRepository,
                providerRepository = providerRepository,
                memoryRepository = memoryRepository,
                skillRepository = skillRepository,
                mcpRepository = mcpRepository,
            )
        }

        composable(Routes.SETTINGS) {
            // [T-android-settings-reorg] Browser settings moved out of the home
            // shell menu into Settings; the sheet needs a tab pool, scoped to
            // this entry so it dies with the screen.
            val settingsCtx = LocalContext.current
            val browserTabPool = remember { com.fenglingcode.app.browser.BrowserTabPool(settingsCtx) }
            var showBrowserSettings by remember { mutableStateOf(false) }
            SettingsScreen(
                onBack = { navController.safePopBackStack() },
                onProvidersClick = { navController.safeNavigate(Routes.PROVIDER_LIST) },
                onModelGroupsClick = { navController.safeNavigate(Routes.MODEL_GROUPS) },
                onStorageClick = { navController.safeNavigate(Routes.STORAGE) },
                onRootfsManagementClick = { navController.safeNavigate(Routes.ROOTFS_MANAGEMENT) },
                onScheduledTasksClick = { navController.safeNavigate(Routes.SCHEDULED_TASKS) },
                onBrowserSettingsClick = { showBrowserSettings = true },
                onBackupClick = { navController.safeNavigate(Routes.BACKUP) },
                onEnvVarsClick = { navController.safeNavigate(Routes.ENV_VARS) },
                onSkillsClick = { navController.safeNavigate(Routes.SKILLS) },
                onTerminalClick = { navController.safeNavigate(Routes.terminal()) },
                onMemoryClick = { navController.safeNavigate(Routes.MEMORY) },
                onMcpClick = { navController.safeNavigate(Routes.MCP) },
                onSoulClick = { navController.safeNavigate(Routes.SOUL) },
                onPermissionsClick = { navController.safeNavigate(Routes.PERMISSIONS) },
                onUsageClick = { navController.safeNavigate(Routes.USAGE_STATS) },
                onAppearanceClick = { navController.safeNavigate(Routes.APPEARANCE) },
                onBackgroundClick = { navController.safeNavigate(Routes.BACKGROUND) },
                onLogsClick = { navController.safeNavigate(Routes.LOGS) },
                onMountedFoldersClick = { navController.safeNavigate(Routes.MOUNTED_FOLDERS) },
                onSharedFoldersClick = { navController.safeNavigate(Routes.SHARED_FOLDERS) },
            )
            if (showBrowserSettings) {
                com.fenglingcode.app.ui.browser.BrowserSettingsSheet(
                    tabPool = browserTabPool,
                    onDismiss = { showBrowserSettings = false },
                )
            }
        }

        composable(Routes.BACKUP) {
            com.fenglingcode.app.ui.settings.backup.BackupAndRestoreScreen(
                onBack = { navController.safePopBackStack() },
                onManageDestinations = { navController.safeNavigate(Routes.BACKUP_DESTINATIONS) },
                onChooseRestoreServer = { navController.safeNavigate(Routes.RESTORE_SERVERS) },
                onOpenHistoryRecord = { id ->
                    navController.safeNavigate("${Routes.BACKUP_HISTORY_DETAIL}/$id")
                },
                onBrowseDestination = { name ->
                    navController.safeNavigate(
                        "${Routes.RESTORE_BROWSE}/" + android.net.Uri.encode(name),
                    )
                },
            )
        }

        composable(
            "${Routes.BACKUP_HISTORY_DETAIL}/{recordId}",
            arguments = listOf(navArgument("recordId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("recordId").orEmpty()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val history = remember { com.fenglingcode.app.backup.BackupHistory.get(ctx) }
            val record = remember(id) { history.records().firstOrNull { it.id == id } }
            if (record == null) {
                // The record was removed (or pruned) while this screen was on
                // the stack; there is nothing to show, so leave rather than
                // render an empty shell.
                LaunchedEffect(Unit) { navController.safePopBackStack() }
            } else {
                // The screen needs a ViewModel for the delete-with-files path
                // (it talks to rclone); scoped to this entry so it dies with
                // the screen.
                val vm: com.fenglingcode.app.ui.settings.backup.BackupViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                com.fenglingcode.app.ui.settings.backup.BackupHistoryDetailScreen(
                    record = record,
                    onBack = { navController.safePopBackStack() },
                    onRemove = {
                        history.remove(id)
                        navController.safePopBackStack()
                    },
                    onRemoveWithFiles = {
                        vm.removeHistoryRecordWithFiles(id)
                        navController.safePopBackStack()
                    },
                    onOpenDestination = { name ->
                        navController.safeNavigate(
                            "${Routes.BACKUP_DESTINATION_BROWSE}/" +
                                android.net.Uri.encode(name),
                        )
                    },
                )
            }
        }

        composable(
            "${Routes.BACKUP_DESTINATION_BROWSE}/{remoteName}",
            arguments = listOf(navArgument("remoteName") { type = NavType.StringType }),
        ) { entry ->
            val name = entry.arguments?.getString("remoteName").orEmpty()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val remote = remember(name) {
                com.fenglingcode.app.backup.remote.RcloneRemoteStore(ctx).remote(name)
            }
            if (remote == null) {
                // The destination was removed since the record was written.
                LaunchedEffect(Unit) { navController.safePopBackStack() }
            } else {
                val vm: com.fenglingcode.app.ui.settings.backup.BackupViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel()
                com.fenglingcode.app.ui.settings.backup.BackupDestinationBrowseScreen(
                    remote = remote,
                    vm = vm,
                    onBack = { navController.safePopBackStack() },
                )
            }
        }

        // [T-android-restore-server-list] Reached from the restore tab's
        // "Choose from Server…" — always, configured or not. Picking a row
        // (or finishing an add) continues to that server's package browser.
        composable(Routes.RESTORE_SERVERS) {
            com.fenglingcode.app.ui.settings.backup.RestoreServersScreen(
                onBack = { navController.safePopBackStack() },
                onPickServer = { name ->
                    navController.safeNavigate(
                        "${Routes.RESTORE_BROWSE}/" + android.net.Uri.encode(name),
                    )
                },
            )
        }

        composable(
            "${Routes.RESTORE_BROWSE}/{remoteName}",
            arguments = listOf(navArgument("remoteName") { type = NavType.StringType }),
        ) { entry ->
            val name = entry.arguments?.getString("remoteName").orEmpty()
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val remote = remember(name) {
                com.fenglingcode.app.backup.remote.RcloneRemoteStore(ctx).remote(name)
            }
            if (remote == null) {
                LaunchedEffect(Unit) { navController.safePopBackStack() }
            } else {
                // Scoped to the BACKUP entry so the browser and the restore
                // screen share one ViewModel — the picked package has to be
                // visible to the screen that restores it.
                val parent = remember(entry) { navController.getBackStackEntry(Routes.BACKUP) }
                val vm: com.fenglingcode.app.ui.settings.backup.BackupViewModel =
                    androidx.lifecycle.viewmodel.compose.viewModel(parent)
                com.fenglingcode.app.ui.settings.backup.RestoreBrowseScreen(
                    remote = remote,
                    vm = vm,
                    onBack = { navController.safePopBackStack() },
                    // [T-android-restore-picked-lands-home] Pop to BACKUP, not
                    // one level up.
                    //
                    // This screen is reachable at two different depths:
                    // BACKUP → RESTORE_BROWSE when a destination is tapped on
                    // the restore tab, but BACKUP → RESTORE_SERVERS →
                    // RESTORE_BROWSE when the user came through "Choose from
                    // Server…". A single pop is right for the first and one
                    // short for the second, so after a multi-GB download and
                    // extract the user landed back on the server list — the
                    // step BEFORE the one they just completed — with no sign
                    // of the package they had waited for. It is on the Backup
                    // screen, one more pop away, that the opened package and
                    // its category checkboxes actually render.
                    //
                    // Popping to a named destination is depth-independent, so
                    // a future third entry point cannot reintroduce this.
                    onPicked = {
                        navController.safePopBackStack(
                            route = Routes.BACKUP,
                            inclusive = false,
                        )
                    },
                )
            }
        }

        composable(Routes.BACKUP_DESTINATIONS) {
            com.fenglingcode.app.ui.settings.backup.RcloneDestinationsScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.SHARED_FOLDERS) {
            SharedFoldersScreen(
                onBack = { navController.safePopBackStack() },
                onFolderClick = { folderId ->
                    navController.safeNavigate(Routes.sharedFoldersDetail(folderId))
                },
            )
        }

        composable(
            route = Routes.SHARED_FOLDERS_DETAIL,
            arguments = listOf(navArgument("folderId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val folderId = backStackEntry.arguments?.getString("folderId") ?: return@composable
            val ctx = androidx.compose.ui.platform.LocalContext.current
            SharedFolderDetailScreen(
                folderId = folderId,
                onBack = { navController.safePopBackStack() },
                onBrowseFiles = {
                    val rootfs = RootfsManager.getInstance(ctx.applicationContext)
                    val hostPath = java.io.File(rootfs.rootfsDir, "var/fengling/$folderId")
                    val label = when (folderId) {
                        "shared" -> ctx.getString(com.fenglingcode.app.R.string.shared_folder_name_shared)
                        "skills" -> ctx.getString(com.fenglingcode.app.R.string.shared_folder_name_skills)
                        "memory" -> ctx.getString(com.fenglingcode.app.R.string.shared_folder_name_memory)
                        else -> folderId
                    }
                    FilePreviewHolder.fileBrowserViewModel = FileBrowserViewModel(
                        rootPath = hostPath,
                        rootLabel = label,
                        // Route reads through PRoot bind mounts so the host
                        // dirs that back /var/fengling/{shared,skills,memory}
                        // resolve, matching how chat-files browse works.
                        linuxRootPath = "/var/fengling/$folderId",
                        appContext = ctx.applicationContext,
                    )
                    navController.safeNavigate(Routes.FILE_BROWSER)
                },
            )
        }

        composable(Routes.MOUNTED_FOLDERS) {
            MountedFoldersScreen(
                store = mountedFoldersStore,
                onBack = { navController.safePopBackStack() },
                onMountClick = { mountId ->
                    navController.safeNavigate(Routes.mountedFoldersDetail(mountId))
                },
            )
        }

        composable(
            route = Routes.MOUNTED_FOLDERS_DETAIL,
            arguments = listOf(navArgument("mountId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val mountId = backStackEntry.arguments?.getString("mountId") ?: return@composable
            val context = androidx.compose.ui.platform.LocalContext.current
            MountDetailScreen(
                store = mountedFoldersStore,
                mountId = mountId,
                onBack = { navController.safePopBackStack() },
                onBrowseFiles = {
                    val entry = mountedFoldersStore.entries.value.firstOrNull { it.id == mountId }
                    val hostPath = entry?.resolvedHostPath
                    if (hostPath != null) {
                        FilePreviewHolder.fileBrowserViewModel = FileBrowserViewModel(
                            rootPath = java.io.File(hostPath),
                            rootLabel = entry.name,
                        )
                        navController.safeNavigate(Routes.FILE_BROWSER)
                    } else {
                        // resolvedHostPath null = SAF tree from a non-externalstorage
                        // provider (cloud / Drive). Picker normally rejects these at
                        // add time, so this is a defensive fallback.
                        android.widget.Toast.makeText(
                            context,
                            "Mount path unavailable",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
            )
        }

        composable(Routes.PROVIDER_LIST) {
            ProviderListScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
                onAddProvider = { navController.safeNavigate(Routes.ADD_PROVIDER) },
                onProviderClick = { instanceId ->
                    navController.safeNavigate(Routes.providerDetail(instanceId))
                },
                onVoiceServiceClick = { instanceId ->
                    navController.safeNavigate(Routes.shadowVoiceDetail(instanceId))
                },
            )
        }

        composable(
            route = Routes.SHADOW_VOICE_DETAIL,
            arguments = listOf(navArgument("instanceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            ShadowVoiceDetailScreen(
                instanceId = instanceId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.ADD_PROVIDER) {
            AddProviderScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
                onSaved = { navController.safePopBackStack() },
            )
        }

        composable(
            route = Routes.PROVIDER_DETAIL,
            arguments = listOf(navArgument("instanceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            ProviderDetailScreen(
                instanceId = instanceId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
                onModelEntryClick = { entryId ->
                    navController.safeNavigate(Routes.modelEntryDetail(instanceId, entryId))
                },
                onAddCustomModel = {
                    navController.safeNavigate(Routes.addCustomModel(instanceId))
                },
                onVoiceServiceClick = { id ->
                    navController.safeNavigate(Routes.shadowVoiceDetail(id))
                },
            )
        }

        composable(Routes.MODEL_GROUPS) {
            ModelGroupsScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
                onGroupClick = { groupId ->
                    navController.safeNavigate(Routes.modelGroupDetail(groupId))
                },
                onAddAgentLoopModels = {
                    navController.safeNavigate(Routes.ADD_MODELS_TO_AGENT_LOOP)
                },
                onAddAgentLoopGroups = {
                    navController.safeNavigate(Routes.ADD_GROUPS_TO_AGENT_LOOP)
                },
            )
        }

        composable(
            route = Routes.MODEL_GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            ModelGroupDetailScreen(
                groupId = groupId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
                onAddModels = {
                    navController.safeNavigate(Routes.addModelsToGroup(groupId))
                },
            )
        }

        composable(
            route = Routes.ADD_MODELS_TO_GROUP,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            AddModelsToGroupScreen(
                groupId = groupId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        // T185: full-screen picker for adding entries to the agent-loop
        // usable set (replaces the T182 ModalBottomSheet so the visual
        // matches AddModelsToGroupScreen — same shared
        // modelEntryPickerItems composable in ui/components/).
        composable(Routes.ADD_MODELS_TO_AGENT_LOOP) {
            AddAgentLoopModelsScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        // T185: companion picker for adding model groups to the agent-loop
        // set. Simpler layout (no per-provider sectioning) but same
        // selection/confirm semantics as the entries picker.
        composable(Routes.ADD_GROUPS_TO_AGENT_LOOP) {
            AddAgentLoopGroupsScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        // T182: AgentLoopModels is no longer a standalone screen. The
        // picker now lives as the last section inside ModelGroupsScreen
        // (mirrors iOS Views/Providers/ModelGroupsView.swift L77-79's
        // `AgentLoopModelsSection`). Routes.AGENT_LOOP_MODELS is left
        // declared for back-compat with any deep-link string we may
        // have shipped to early users; safePopBackStack lands them on
        // the prior screen if it ever fires.
        composable(Routes.AGENT_LOOP_MODELS) {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                navController.safePopBackStack()
            }
        }

        composable(
            route = Routes.MODEL_ENTRY_DETAIL,
            arguments = listOf(
                navArgument("instanceId") { type = NavType.StringType },
                navArgument("entryId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            // [T-android-model-entry-route-slash-crash] Decode back the
            // %2F-encoded composite ids so ModelEntryDetailScreen can match
            // entry.id == "<instanceId>/<modelId>" (which carries a literal '/')
            // against the repo. Mirrors the encode in Routes.modelEntryDetail.
            val instanceId = backStackEntry.arguments?.getString("instanceId")
                ?.let { android.net.Uri.decode(it) } ?: return@composable
            val entryId = backStackEntry.arguments?.getString("entryId")
                ?.let { android.net.Uri.decode(it) } ?: return@composable
            ModelEntryDetailScreen(
                instanceId = instanceId,
                entryId = entryId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(
            route = Routes.ADD_CUSTOM_MODEL,
            arguments = listOf(navArgument("instanceId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val instanceId = backStackEntry.arguments?.getString("instanceId") ?: return@composable
            AddCustomModelScreen(
                instanceId = instanceId,
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.STORAGE) {
            StorageManagementScreen(
                chatDao = chatRepository.dao,
                onBack = { navController.safePopBackStack() },
                onRootfsClick = { navController.safeNavigate(Routes.ROOTFS_MANAGEMENT) },
                onSessionClick = { sessionId ->
                    navController.safeNavigate(Routes.sessionStorageDetail(sessionId))
                },
            )
        }

        composable(
            route = Routes.SESSION_STORAGE_DETAIL,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            SessionStorageDetailScreen(
                sessionId = sessionId,
                chatDao = chatRepository.dao,
                onBack = { navController.safePopBackStack() },
                onBrowseFiles = { rootPath ->
                    // [T-android-copy-abs-path-fullpath] This browser is rooted at
                    // the per-session host dir (filesDir/fengling-sessions/<sid>),
                    // whose immediate children (workspace/ attachments/ offloads/
                    // browser/) are exactly the PRoot /var/fengling/* subdirs. The
                    // host listing already resolves correctly so we keep rootPath
                    // host-based (no linuxRootPath re-routing — that would redirect
                    // /var/fengling to the global/empty placeholder dir). We only pass
                    // displayLinuxPrefix = "/var/fengling" so "Copy Absolute Path"
                    // emits the agent-visible /var/fengling/workspace/foo.py instead of
                    // the opaque /data/user/0/.../fengling-sessions/<sid>/... host path.
                    FilePreviewHolder.fileBrowserViewModel = FileBrowserViewModel(
                        rootPath = java.io.File(rootPath),
                        rootLabel = "Session Files",
                        displayLinuxPrefix = "/var/fengling",
                    )
                    navController.safeNavigate(Routes.FILE_BROWSER)
                },
            )
        }

        composable(Routes.ROOTFS_MANAGEMENT) {
            val context = androidx.compose.ui.platform.LocalContext.current
            RootfsManagementScreen(
                onBack = { navController.safePopBackStack() },
                onBrowseFiles = {
                    val rootfs = RootfsManager.getInstance(context.applicationContext)
                    FilePreviewHolder.fileBrowserViewModel = FileBrowserViewModel(
                        rootPath = rootfs.rootfsDir,
                        rootLabel = "/",
                    )
                    navController.safeNavigate(Routes.FILE_BROWSER)
                },
                // [T-android-mirror-manual-select] Without this callback the
                // mirror rows fall back to the declaration-site no-op default
                // and MirrorCategoryDetailScreen (manual mirror selection,
                // iOS MirrorCategoryDetailView parity) was unreachable.
                onMirrorCategoryClick = { category ->
                    navController.safeNavigate(Routes.mirrorCategoryDetail(category.key))
                },
            )
        }

        composable(
            route = Routes.MIRROR_CATEGORY_DETAIL,
            arguments = listOf(navArgument("categoryKey") { type = NavType.StringType }),
        ) { backStackEntry ->
            val categoryKey = backStackEntry.arguments?.getString("categoryKey") ?: return@composable
            val category = com.fenglingcode.app.ui.sandbox.MirrorCatalog.categoryFromKey(categoryKey)
                ?: return@composable
            com.fenglingcode.app.ui.sandbox.MirrorCategoryDetailScreen(
                category = category,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.FILE_BROWSER) {
            val vm = FilePreviewHolder.fileBrowserViewModel ?: return@composable
            FileBrowserScreen(
                viewModel = vm,
                onBack = { navController.safePopBackStack() },
                onPreviewFile = { item ->
                    FilePreviewHolder.currentItem = item
                    navController.safeNavigate(Routes.FILE_PREVIEW)
                },
            )
        }

        // Browse Chat Files (iOS parity: open FileBrowser rooted at the full
        // Linux root, focused on /var/fengling. Matches AIChatView.swift L490:
        //   FileBrowserView(rootPath: dataPath, initialPath: dataPath/var/fengling,
        //                   rootLabel: "/")
        // so the user can navigate up out of /var/fengling into the broader rootfs.
        composable(
            route = Routes.CHAT_FILES,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val rootfs = RootfsManager.getInstance(context.applicationContext)
            val varFengling = java.io.File(rootfs.rootfsDir, "var/fengling")
            val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
            val vm = remember(rootfs.rootfsDir.absolutePath, varFengling.absolutePath, sessionId) {
                FileBrowserViewModel(
                    rootPath = rootfs.rootfsDir,
                    initialPath = varFengling.takeIf { it.exists() },
                    rootLabel = "/",
                    // T121: route directory listings through PRootKernel bind
                    // mounts so /var/fengling/{skills,memory,shared} resolve to
                    // their backing host dirs (filesDir/fengling-global/<subdir>).
                    // Without this the browser walks the rootfs tarball
                    // directly and shows the empty placeholder dirs that ship
                    // inside Alpine's var/fengling/ — every subdir reads as
                    // "Empty folder" even though the agent has files there.
                    linuxRootPath = "/",
                    // T147: scope per-session subdirs (attachments / workspace
                    // / offloads / browser) to THIS chat's host dir even when
                    // another session was the last to boot a PRoot — that
                    // global bindMounts state is last-writer-wins and would
                    // otherwise hide the agent's generated files for the
                    // session the user is looking at.
                    sessionId = sessionId,
                    appContext = context.applicationContext,
                )
            }
            FileBrowserScreen(
                viewModel = vm,
                onBack = { navController.safePopBackStack() },
                onPreviewFile = { item ->
                    FilePreviewHolder.currentItem = item
                    navController.safeNavigate(Routes.FILE_PREVIEW)
                },
            )
        }

        // Rendered as a Dialog destination (not a `composable`) so the
        // underlying screen — typically ChatScreen — stays in the composition
        // while preview is open. With `composable()`, NavHost unmounts the
        // previous entry, which detaches the chat LazyColumn from layout;
        // when the user pops back, LazyListState re-anchors to (0, 0) and
        // the user loses their scroll position (plus a white-flash on the
        // first frame before the list remeasures). `dialog()` keeps the
        // back entry's composition alive — listState retains both
        // firstVisibleItemIndex/Offset and its layoutInfo cache, so the
        // chat paints its previous viewport on the first frame after pop.
        // `usePlatformDefaultWidth=false` lets the dialog fill the screen
        // edge-to-edge, matching the prior full-screen composable feel;
        // `decorFitsSystemWindows=false` lets FilePreviewScreen handle its
        // own insets exactly like before.
        dialog(
            route = Routes.FILE_PREVIEW,
            dialogProperties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            val item = FilePreviewHolder.currentItem ?: return@dialog
            FilePreviewScreen(
                item = item,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.ENV_VARS) {
            if (envVarRepository != null) {
                EnvironmentVariablesScreen(
                    envVarRepository = envVarRepository,
                    onBack = { navController.safePopBackStack() },
                )
            }
        }

        composable(Routes.SKILLS) {
            if (skillRepository != null) {
                SkillsManagementScreen(
                    skillRepository = skillRepository,
                    onBack = { navController.safePopBackStack() },
                    onSkillClick = { skillId -> navController.safeNavigate(Routes.skillDetail(skillId)) },
                    onFenglingSkillsClick = { navController.safeNavigate(Routes.MINIS_SKILLS_BROWSER) },
                )
            }
        }

        composable(
            route = Routes.SKILL_DETAIL,
            arguments = listOf(navArgument("skillId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val skillId = backStackEntry.arguments?.getString("skillId") ?: return@composable
            if (skillRepository != null) {
                SkillDetailScreen(
                    skillId = skillId,
                    skillRepository = skillRepository,
                    onBack = { navController.safePopBackStack() },
                    onFileClick = { id, relativePath ->
                        navController.safeNavigate(Routes.skillFile(id, relativePath))
                    },
                )
            }
        }

        composable(
            route = Routes.SKILL_FILE,
            arguments = listOf(
                navArgument("skillId") { type = NavType.StringType },
                navArgument("relativePath") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val skillId = backStackEntry.arguments?.getString("skillId") ?: return@composable
            val rawPath = backStackEntry.arguments?.getString("relativePath") ?: "SKILL.md"
            val relativePath = java.net.URLDecoder.decode(rawPath, "UTF-8")
            if (skillRepository != null) {
                SkillFileViewerScreen(
                    skillId = skillId,
                    relativePath = relativePath,
                    skillRepository = skillRepository,
                    onBack = { navController.safePopBackStack() },
                )
            }
        }

        composable(Routes.MINIS_SKILLS_BROWSER) {
            if (skillRepository != null) {
                FenglingSkillsBrowserScreen(
                    skillRepository = skillRepository,
                    onBack = { navController.safePopBackStack() },
                )
            }
        }

        composable(
            Routes.TERMINAL,
            arguments = listOf(
                androidx.navigation.navArgument("initCommand") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                androidx.navigation.navArgument("sessionId") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val initCommand = backStackEntry.arguments?.getString("initCommand")
            val sessionId = backStackEntry.arguments?.getString("sessionId")
            val session = remember { TerminalSession(context.applicationContext) }
            TerminalScreen(
                terminalSession = session,
                onBack = { navController.safePopBackStack() },
                initCommand = initCommand,
                sessionId = sessionId,
            )
        }

        composable(Routes.MEMORY) {
            if (memoryRepository != null) {
                MemoryManagementScreen(
                    memoryRepository = memoryRepository,
                    onBack = { navController.safePopBackStack() },
                    onFileClick = { fileName, isGlobal ->
                        navController.safeNavigate(Routes.memoryFileEdit(fileName, isGlobal))
                    },
                )
            }
        }

        // [T-mcp-integration-android] MCP Integrations management screen.
        composable(Routes.MCP) {
            if (mcpRepository != null) {
                com.fenglingcode.app.ui.settings.MCPIntegrationsScreen(
                    mcpRepository = mcpRepository,
                    onBack = { navController.safePopBackStack() },
                    envVarRepository = envVarRepository,
                )
            }
        }

        // [T-soul-md] SOUL.md editor.
        composable(Routes.SOUL) {
            com.fenglingcode.app.ui.settings.SoulSettingsScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(
            route = Routes.MEMORY_FILE_EDIT,
            arguments = listOf(
                navArgument("fileName") { type = NavType.StringType },
                navArgument("isGlobal") { type = NavType.BoolType },
            ),
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            val isGlobal = backStackEntry.arguments?.getBoolean("isGlobal") ?: false
            if (memoryRepository != null) {
                MemoryFileEditScreen(
                    fileName = fileName,
                    isGlobal = isGlobal,
                    memoryRepository = memoryRepository,
                    onBack = { navController.safePopBackStack() },
                )
            }
        }

        composable(Routes.PERMISSIONS) {
            OffloadPermissionScreen(
                onBack = { navController.safePopBackStack() },
                // [T-android-privileged-backend] Single Shizuku-protocol screen
                // handles both Shizuku and AXManager managers; the old
                // multi-backend screen was retired in favour of a single
                // surface (the two managers share one binder slot, so a
                // multi-backend abstraction was misleading).
                onOpenPrivilegedBackend = { navController.safeNavigate(Routes.SHIZUKU) },
            )
        }

        composable(Routes.SHIZUKU) {
            ShizukuPermissionScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.SYSTEM_PERMISSIONS) {
            SystemPermissionsScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.USAGE_STATS) {
            UsageStatsScreen(
                chatDao = chatRepository.dao,
                providerConfig = providerRepository.config.value,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.APPEARANCE) {
            AppearanceScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.BACKGROUND) {
            BackgroundSettingsScreen(
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.LOGS) {
            LogManagementScreen(
                onBack = { navController.safePopBackStack() },
                onLogFileClick = { fileName ->
                    navController.safeNavigate(Routes.logDetail(fileName))
                },
            )
        }

        composable(
            route = Routes.LOG_DETAIL,
            arguments = listOf(navArgument("fileName") { type = NavType.StringType }),
        ) { backStackEntry ->
            val fileName = backStackEntry.arguments?.getString("fileName") ?: return@composable
            LogDetailScreen(
                fileName = fileName,
                onBack = { navController.safePopBackStack() },
            )
        }

        composable(Routes.ONBOARDING_MODELS) {
            OnboardingModelSelectionScreen(
                providerRepository = providerRepository,
                onBack = { navController.safePopBackStack() },
            )
        }

        // [T-android-scheduled-tasks-design] Scheduled tasks list + editor.
        composable(Routes.SCHEDULED_TASKS) {
            com.fenglingcode.app.ui.scheduled.ScheduledTasksScreen(
                onBack = { navController.safePopBackStack() },
                onEditTask = { taskId ->
                    navController.safeNavigate(Routes.scheduledTaskEdit(taskId))
                },
                onViewRuns = { taskId ->
                    navController.safeNavigate(Routes.scheduledTaskRuns(taskId))
                },
                onOpenSession = { sessionId ->
                    navController.safeNavigate(Routes.chat(sessionId))
                },
            )
        }
        // [T-android-scheduled-tasks-run-records] per-task run records.
        composable(
            route = Routes.SCHEDULED_TASK_RUNS,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId") ?: return@composable
            com.fenglingcode.app.ui.scheduled.ScheduledTaskRunsScreen(
                taskId = taskId,
                onBack = { navController.safePopBackStack() },
                onOpenSession = { sessionId ->
                    navController.safeNavigate(Routes.chat(sessionId))
                },
            )
        }
        composable(
            route = Routes.SCHEDULED_TASK_EDIT,
            arguments = listOf(
                navArgument("taskId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            com.fenglingcode.app.ui.scheduled.ScheduledTaskEditScreen(
                taskId = taskId,
                onBack = { navController.safePopBackStack() },
                onOpenSession = { sessionId ->
                    navController.safeNavigate(Routes.chat(sessionId))
                },
            )
        }
    }
}
