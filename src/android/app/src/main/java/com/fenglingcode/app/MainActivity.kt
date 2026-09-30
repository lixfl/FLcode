package com.fenglingcode.app

import android.app.LocaleManager
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.provider.Settings
import com.fenglingcode.app.accessibility.AccessibilityRecoveryManager
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import android.app.AlertDialog
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.fenglingcode.app.offload.OffloadPermissionManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.fenglingcode.app.deeplink.DeepLinkAction
import com.fenglingcode.app.deeplink.DeepLinkCoordinator
import com.fenglingcode.app.deeplink.DeepLinkHandler
import com.fenglingcode.app.logging.AppLogger
import com.fenglingcode.app.service.SessionActivityTracker
import com.fenglingcode.app.ui.navigation.AppNavigation
import com.fenglingcode.app.ui.navigation.Routes
import com.fenglingcode.app.ui.navigation.safeNavigate
import com.fenglingcode.app.ui.NewerDatabaseGuidanceScreen
import com.fenglingcode.app.ui.settings.KEY_FONT_APP_BASE
import com.fenglingcode.app.ui.settings.KEY_KEEP_SCREEN_AWAKE
import com.fenglingcode.app.ui.settings.KEY_LANGUAGE
import com.fenglingcode.app.ui.settings.KEY_THEME_MODE
import com.fenglingcode.app.ui.settings.PREF_APPEARANCE
import com.fenglingcode.app.ui.settings.getAppearancePrefs
import com.fenglingcode.app.ui.settings.fontScaleForLevel
import com.fenglingcode.app.ui.settings.keepScreenAwakeEnabled
import com.fenglingcode.app.ui.theme.FenglingTheme

private const val KEY_CURRENT_CHAT_SESSION_ID = "fengling.current_chat_session_id"

class MainActivity : ComponentActivity() {

    private var navController: NavHostController? = null

    /**
     * T166: id of the chat the user is currently inside, mirrored from
     * the nav back-stack so [onSaveInstanceState] can persist it across
     * process death. Restored value is fed into [AppNavigation] as the
     * initial deep-link so the user lands back where they were.
     */
    private var currentChatSessionId: String? = null

    /**
     * T166: id of the chat to re-open on first composition after a
     * process-death restart. Set in [onCreate] from the saved state
     * bundle, consumed exactly once by [AppNavigation] via the
     * synthesised deep-link.
     */
    private var restoredChatSessionId: String? = null

    /**
     * T293 / issue #10: tracks whether [onStart] has fired at least once.
     * The launch-session preference has been removed; cold start now always
     * opens the last session via AppNavigation's simplified resolver.
     */
    private var hasResumedFromBackground = false

    /**
     * Bridges [OffloadPermissionManager.requestAndroidPermission] to the system
     * runtime-permission dialog. Background offload handlers suspend while
     * this launcher's callback resolves the request.
     */
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>

    /**
     * Used by the "go to settings" gate to open e.g. the app-details or
     * Notification Access settings page. We don't rely on the result —
     * [OffloadPermissionManager.requestSettingsGate] polls until the
     * permission actually changes (or times out).
     */
    private lateinit var settingsLauncher: ActivityResultLauncher<Intent>

    // T-n01-andmenu-l10n: on Android 12 and below `LocaleManager.applicationLocales`
    // does not exist, so the saved language pick has no effect on framework
    // strings — including the labels of the system text-selection ActionMode
    // ("Cut" / "Copy" / "Paste"). Override the base Configuration so the
    // framework picks the right locale on those devices. No-op on 13+.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.fenglingcode.app.i18n.LocaleWrap.wrap(newBase))
    }

    /**
     * T-android-safemode-lateinit-crash: escape hatch for a process whose
     * [FenglingApp.onCreate] early-returned after a subsystem-init failure.
     *
     * That early return is irreversible within the process — the
     * repositories stay unassigned no matter what — so there is nothing
     * this Activity can do to become usable. Killing the process is the
     * recovery: the next launch runs Application.onCreate from the top, so
     * a one-off init failure doesn't lock the user out until reinstall.
     *
     * We deliberately do NOT auto-relaunch an Activity here. Doing so from
     * a dying process races the system's own task restart on some OEM
     * builds (MIUI included) and can produce two tasks. Exiting cleanly
     * and letting the user's next tap start the app is predictable.
     */
    private fun finishAndRestartProcess() {
        try {
            android.widget.Toast.makeText(
                this,
                getString(R.string.crash_safe_mode_restart_needed),
                android.widget.Toast.LENGTH_LONG,
            ).show()
        } catch (t: Throwable) {
            android.util.Log.w("MainActivity", "restart toast failed: ${t.message}")
        }
        finish()
        // Let the toast render before tearing the process down. The delay
        // runs on the main looper of a process we are about to kill, which
        // is fine — nothing else is scheduled on it at this point.
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 1200L)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fenglingApp = application as? FenglingApp

        // [T-android-downgrade-compat] A database written by a NEWER build is
        // not a crash — it is a recoverable state with a specific remedy, and
        // the file has deliberately been left untouched. Handle it before the
        // degraded-init path below.
        if (fenglingApp != null &&
            fenglingApp.dbVersionDecision ==
            com.fenglingcode.app.data.db.DatabaseVersionGuard.Decision.SHOW_NEWER_DB_GUIDANCE
        ) {
            android.util.Log.w("MainActivity", "showing newer-database guidance screen")
            setContent { NewerDatabaseGuidanceScreen(onExit = { finishAndRemoveTask() }) }
            return
        }

        // T-android-safemode-lateinit-crash: if FenglingApp.onCreate
        // early-returned after a subsystem-init failure, every repository
        // the UI reads is unassigned for the life of this process.
        // Composing here would throw UninitializedPropertyAccessException on
        // the first frame, so bail visibly and restart into a fresh process
        // instead. Gate on the Application's own `subsystemsInitialized`
        // flag — the authoritative "did init run" signal.
        if (fenglingApp == null || !fenglingApp.subsystemsInitialized) {
            android.util.Log.w(
                "MainActivity",
                "app subsystems not initialized — restarting process",
            )
            finishAndRestartProcess()
            return
        }

        // T166: if we were killed by LMK while the user was inside a
        // chat, restore the sessionId now so the synthesised deep-link
        // re-opens it before any composable is composed. ChatViewModel
        // rehydrates messages from SQLite via session id, so the user
        // lands in the same chat with the same history rendered.
        // Process death loses streamJob coroutine state — any in-flight
        // stream is treated as cancelled and the user can tap Retry.
        // Honour the launch circuit breakers (HangDetector, LaunchCycleBeacon):
        // when the previous boot tripped one, don't let LMK-restore drop the
        // user straight back into the chat that may have been the trigger.
        // AppNavigation reads the same breakers for its launch-mode resolution;
        // gating restoredChatSessionId here covers the path where saved-state
        // would override the launch-mode dispatch entirely.
        restoredChatSessionId = savedInstanceState?.getString(KEY_CURRENT_CHAT_SESSION_ID)
            ?.takeUnless {
                com.fenglingcode.app.diagnostics.HangDetector.shouldForceHomeOnLaunch(this) ||
                    com.fenglingcode.app.diagnostics.LaunchCycleBeacon.shouldForceHomeOnLaunch()
            }

        permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            val allGranted = results.isNotEmpty() && results.values.all { it }
            OffloadPermissionManager.respondToAndroidPermission(allGranted)
        }
        settingsLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* polled by OffloadPermissionManager.requestSettingsGate */ }

        // Bridge: system permission dialog (first ask / can-ask-again).
        // If the permission has already been permanently denied (dialog
        // would no-op), report DENIED so the handler can fall back to the
        // in-app settings gate.
        lifecycleScope.launch {
            OffloadPermissionManager.pendingAndroidPermission
                .filterNotNull()
                .collect { req ->
                    val permanentlyDenied = req.permissions.any { permission ->
                        ContextCompat.checkSelfPermission(this@MainActivity, permission) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED &&
                            !ActivityCompat.shouldShowRequestPermissionRationale(
                                this@MainActivity, permission
                            ) &&
                            OffloadPermissionManager.hasAskedForPermission(this@MainActivity, permission)
                    }
                    if (permanentlyDenied) {
                        OffloadPermissionManager.respondToAndroidPermission(
                            OffloadPermissionManager.AndroidPermissionResult.DENIED
                        )
                    } else {
                        for (p in req.permissions) {
                            OffloadPermissionManager.markPermissionAsked(this@MainActivity, p)
                        }
                        permissionLauncher.launch(req.permissions.toTypedArray())
                    }
                }
        }

        // Bridge: in-app "open settings" dialog. Used for already-denied
        // runtime permissions AND for special-access capabilities like
        // Notification Access.
        lifecycleScope.launch {
            OffloadPermissionManager.pendingSettingsGate
                .filterNotNull()
                .collect { gate ->
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(gate.title)
                        .setMessage(gate.message)
                        .setCancelable(false)
                        .setPositiveButton(gate.positiveLabel) { d, _ ->
                            d.dismiss()
                            val intent = Intent(gate.settingsAction).apply {
                                if (gate.requiresPackageUri) {
                                    data = Uri.fromParts("package", packageName, null)
                                }
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            try {
                                settingsLauncher.launch(intent)
                            } catch (_: Throwable) {
                                // Some OEMs don't expose every settings panel.
                                // Fall through — the polling loop will time out.
                            }
                            OffloadPermissionManager.respondToSettingsGate(
                                OffloadPermissionManager.SettingsGateDecision.OPEN
                            )
                        }
                        .setNegativeButton(gate.negativeLabel) { d, _ ->
                            d.dismiss()
                            OffloadPermissionManager.respondToSettingsGate(
                                OffloadPermissionManager.SettingsGateDecision.CANCEL
                            )
                        }
                        .show()
                }
        }

        // [T-android-a11y-force-stop-recovery] Bridge: the accessibility-grant
        // repair prompt. Raised from the a11y tool path when the framework has
        // stripped our component out of ENABLED_ACCESSIBILITY_SERVICES (the
        // force-stop case). Two shapes depending on whether Shizuku can do the
        // privileged write for us.
        //
        // setCancelable(true) + setOnCancelListener, unlike the settings gate
        // above: the spec requires that an interrupted dialog counts as a
        // cancel and does not wedge the waiting agent turn. Every exit path —
        // button, back press, outside tap — resolves the continuation exactly
        // once (respond() no-ops if already resolved, e.g. after a timeout).
        lifecycleScope.launch {
            AccessibilityRecoveryManager.pendingPrompt
                .filterNotNull()
                .collect { prompt ->
                    val b = AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.a11y_repair_dialog_title))
                        .setCancelable(true)
                        .setOnCancelListener {
                            AccessibilityRecoveryManager.respond(
                                AccessibilityRecoveryManager.Decision.CANCEL
                            )
                        }
                        .setNegativeButton(R.string.a11y_repair_cancel) { d, _ ->
                            d.dismiss()
                            AccessibilityRecoveryManager.respond(
                                AccessibilityRecoveryManager.Decision.CANCEL
                            )
                        }
                    if (prompt.shizukuAvailable) {
                        b.setMessage(getString(R.string.a11y_repair_dialog_message_shizuku))
                            .setPositiveButton(R.string.a11y_repair_action_repair) { d, _ ->
                                d.dismiss()
                                AccessibilityRecoveryManager.respond(
                                    AccessibilityRecoveryManager.Decision.REPAIR
                                )
                            }
                    } else {
                        b.setMessage(getString(R.string.a11y_repair_dialog_message_manual))
                            .setPositiveButton(R.string.a11y_repair_action_open_settings) { d, _ ->
                                d.dismiss()
                                try {
                                    startActivity(
                                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                } catch (_: Throwable) {
                                    // Some OEMs hide this panel; the user can
                                    // still reach it from Settings manually.
                                }
                                AccessibilityRecoveryManager.respond(
                                    AccessibilityRecoveryManager.Decision.OPEN_SETTINGS
                                )
                            }
                    }
                    b.show()
                }
        }

        // Apply saved language before composing UI
        applySavedLanguage()

        // T51: ShareReceiverActivity re-launches MainActivity with the
        // `shared_content=true` extra after persisting a PendingShare to
        // share_prefs. Process it now so ShareCoordinator's in-memory
        // buffer is populated before any ChatScreen composes.
        //
        // [T-android-share-launch-crash] Deliberately UNCONDITIONAL, matching
        // iOS `checkForPendingShare()` which runs on every launch
        // (FenglingApp.swift:279) rather than keying off a launch parameter.
        // Gating on the extra made the share unrecoverable in exactly the case
        // the OEM-crash fallback creates: when ShareReceiverActivity cannot
        // start MainActivity at all, the extra is never delivered, so a user
        // who then opens the app from the launcher had their already-persisted
        // share sit unread — the toast tells them to open the app, and opening
        // it did nothing. Reading it here is the safety net that makes that
        // advice true.
        //
        // Safe to call on every launch: processPendingShare returns
        // immediately when share_prefs holds no record (the overwhelmingly
        // common case), and consumes-and-clears the record when it does, so a
        // share is never injected twice. Staleness is still enforced inside
        // (LAUNCH_MAX_AGE_MS), so an abandoned record cannot resurface later.
        com.fenglingcode.app.share.ShareCoordinator.processPendingShare(this)

        // Wire FLAG_KEEP_SCREEN_ON to (Appearance → Keep Screen Awake) AND
        // SessionActivityTracker.activeSessions. Lock held iff toggle is on
        // AND at least one session is currently running a task. Mirrors iOS
        // KeepScreenAwakeController (AIChatViewModel.swift:320), which holds
        // UIApplication.isIdleTimerDisabled under the same conditions.
        // Re-evaluates on every prefs change (the existing prefs listener
        // below covers the toggle) and on every activeSessions transition.
        lifecycleScope.launch {
            SessionActivityTracker.activeSessions.collect { active ->
                applyKeepScreenAwakeFlag(active.isNotEmpty())
            }
        }

        // Register for debug screenshot capture (debug builds only)
        if (BuildConfig.DEBUG) {
            com.fenglingcode.app.debug.DebugRPCHandler.currentActivity = java.lang.ref.WeakReference(this)
        }

        // Non-null and fully initialized — proven by the guard above.
        val app = requireNotNull(application as? FenglingApp)

        // Parse deep link from launch intent. A real deep-link in the
        // launch intent always wins over a saved-state restore (the
        // user explicitly tapped a link). Otherwise, if we were killed
        // while inside a chat, synthesise an OpenSession deep-link so
        // the navigation stack lands on that chat instead of the
        // sessions list. T166.
        val explicitDeepLink = DeepLinkHandler.parse(intent?.data)
        val launchDeepLink = if (explicitDeepLink !is DeepLinkAction.Unknown) {
            explicitDeepLink
        } else {
            restoredChatSessionId
                ?.let { DeepLinkAction.OpenSession(it) }
                ?: DeepLinkAction.Unknown
        }

        setContent {
            val prefs = remember { getAppearancePrefs(this) }
            var themeMode by remember { mutableIntStateOf(prefs.getInt(KEY_THEME_MODE, 0)) }
            var appBaseLevel by remember { mutableIntStateOf(prefs.getInt(KEY_FONT_APP_BASE, 0)) }

            DisposableEffect(prefs) {
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    when (key) {
                        KEY_THEME_MODE -> themeMode = sp.getInt(KEY_THEME_MODE, 0)
                        KEY_FONT_APP_BASE -> appBaseLevel = sp.getInt(KEY_FONT_APP_BASE, 0)
                        KEY_KEEP_SCREEN_AWAKE -> applyKeepScreenAwakeFlag(
                            SessionActivityTracker.activeSessions.value.isNotEmpty()
                        )
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }

            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            val fontScale = fontScaleForLevel(appBaseLevel)

            SideEffect {
                // [风铃code] The app never calls setDefaultNightMode /
                // setLocalNightMode — `darkTheme` here is a Compose-only
                // decision driven by the in-app themeMode pref.
                // `SystemBarStyle.auto` however picks icon appearance from
                // the SYSTEM uiMode, so a user who forces 浅色 in-app on a
                // system-dark phone got white status-bar icons painted on
                // the white chat background: the bar reads as "eaten" (icons
                // invisible). Same in reverse for forced 深色 on a
                // system-light phone. Pick the style explicitly from
                // `darkTheme` so the icon colour always matches what we
                // actually paint behind it. TRANSPARENT scrims keep the
                // T205 "no grey strip" behaviour that motivated the earlier
                // auto(TRANSPARENT, TRANSPARENT) fix.
                val barStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }

            FenglingTheme(darkTheme = darkTheme, fontScale = fontScale) {
                val navController = rememberNavController().also { this.navController = it }

                // T166: drive `SessionActivityTracker.setPresent` /
                // `setAbsent` from the nav back-stack so the foreground
                // service runs the entire time the user is inside a
                // chat, not only while a stream is in flight. Without
                // this hook the process drops from adj=200 to adj=700
                // the moment Home is pressed and a Pixel 4a will
                // reclaim within a couple of minutes (see
                // docs/parity/android-keep-alive-audit.md).
                DisposableEffect(navController) {
                    val job = lifecycleScope.launch {
                        navController.currentBackStackEntryFlow.collect { entry ->
                            val isChatRoute = entry.destination.route == Routes.CHAT
                            val sid = entry.arguments?.getString("sessionId").takeIf { isChatRoute }
                            val previous = currentChatSessionId
                            if (sid != previous) {
                                if (previous != null) {
                                    SessionActivityTracker.setAbsent(previous)
                                }
                                if (sid != null) {
                                    SessionActivityTracker.setPresent(sid)
                                }
                                currentChatSessionId = sid
                            }
                        }
                    }
                    onDispose { job.cancel() }
                }

                AppNavigation(
                    chatRepository = app.chatRepository,
                    providerRepository = app.providerRepository,
                    envVarRepository = app.envVarRepository,
                    skillRepository = app.skillRepository,
                    mcpRepository = app.mcpRepository,
                    memoryRepository = app.memoryRepository,
                    navController = navController,
                    initialDeepLink = launchDeepLink,
                )

                // T-config: root-level fengling-config confirm dialog.
                // Bound to ConfigConfirmationGate.pending — the gate
                // fires whenever a CLI write is awaiting user OK. The
                // dialog is rendered on top of any active screen, so
                // it works regardless of where the user is when the
                // agent triggers a change. Mirrors iOS FenglingApp.swift
                // root-level `.sheet(item: gate.pending)`.
                com.fenglingcode.app.ui.settings.ConfigConfirmDialogHost()
            }
        }
    }

    /**
     * T166: persist the current chat sessionId so an LMK kill while in
     * chat restarts back to the same session (see `restoredChatSessionId`
     * in [onCreate]). Called by the OS in the same lifecycle phase that
     * Activity Recreation uses, so a configuration change also goes
     * through this path — which is exactly what we want; the state is
     * cheap and re-read is idempotent.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentChatSessionId?.let {
            outState.putString(KEY_CURRENT_CHAT_SESSION_ID, it)
        }
    }

    /**
     * T293 / issue #10: no-op since the launch-session preference was removed.
     * Cold start always opens the last session via AppNavigation's resolver.
     * Background→foreground transitions no longer trigger navigation changes.
     */
    override fun onStart() {
        super.onStart()
        hasResumedFromBackground = true
    }

    /**
     * T166: when the Activity is genuinely torn down (not just paused),
     * release any presence we held. Pause / Home / lock-screen do NOT
     * trigger this — those are exactly the cases the FG service exists
     * to bias OOM through.
     */
    override fun onDestroy() {
        currentChatSessionId?.let { SessionActivityTracker.setAbsent(it) }
        currentChatSessionId = null
        super.onDestroy()
    }

    /**
     * Apply (or release) the activity window's `FLAG_KEEP_SCREEN_ON` based on
     * the user's "Keep Screen Awake" toggle and whether any session has an
     * active task right now. Idempotent — flipping with the same desired
     * state is a no-op at the WindowManager level.
     */
    private fun applyKeepScreenAwakeFlag(hasActiveSession: Boolean) {
        val want = keepScreenAwakeEnabled(this) && hasActiveSession
        if (want) {
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            AppLogger.info("KeepScreenAwake", "screen-on lock acquired (active sessions present)")
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            AppLogger.info(
                "KeepScreenAwake",
                "screen-on lock released (toggle=${keepScreenAwakeEnabled(this)}, active=$hasActiveSession)",
            )
        }
    }

    private fun applySavedLanguage() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val code = getSharedPreferences(PREF_APPEARANCE, MODE_PRIVATE).getString(KEY_LANGUAGE, "") ?: ""
        val localeManager = getSystemService(LocaleManager::class.java) ?: return
        val desired = if (code.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(code)
        if (localeManager.applicationLocales.toLanguageTags() != desired.toLanguageTags()) {
            localeManager.applicationLocales = desired
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // T51: warm-start share — ShareReceiverActivity re-launches with
        // FLAG_ACTIVITY_CLEAR_TOP, which delivers the new intent here when
        // MainActivity is already alive. Process the buffered share before
        // touching the deep-link path so the share coordinator sees it.
        if (intent.getBooleanExtra("shared_content", false)) {
            com.fenglingcode.app.share.ShareCoordinator.processPendingShare(this)
        }
        handleDeepLink(intent.data)
    }

    private fun handleDeepLink(uri: Uri?) {
        val action = DeepLinkHandler.parse(uri)
        val nav = navController ?: return
        when (action) {
            is DeepLinkAction.OpenTerminal -> {
                nav.navigate(Routes.terminal(action.initCommand))
            }
            is DeepLinkAction.OpenSession -> {
                // [T-fengling-no-home] Mirror AppNavigation's OpenSession
                // options: pop the blank SESSION_LIST placeholder inclusive so
                // the chat is the sole root (back exits the app), and
                // launchSingleTop collapses a duplicate when the same chat is
                // already showing.
                nav.navigate(Routes.chat(action.sessionId)) {
                    popUpTo(Routes.SESSION_LIST) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is DeepLinkAction.CreateEnvironmentVariable -> {
                DeepLinkCoordinator.setPendingEnvVarCreate(action.key, action.value, action.note)
                nav.navigate(Routes.ENV_VARS)
            }
            // T183: any settings screen reachable by route string.
            is DeepLinkAction.OpenSettingsScreen -> {
                nav.navigate(action.route)
            }
            is DeepLinkAction.OpenPermissionSettings -> {
                nav.navigate(Routes.PERMISSIONS)
            }
            is DeepLinkAction.OpenHtmlPreview -> {
                DeepLinkCoordinator.setPendingHtmlPreview(
                    action.sessionId,
                    action.resourcePath,
                    action.title,
                )
                nav.navigate(Routes.chat(action.sessionId))
            }
            // App-icon quick actions (mirrors iOS QuickActionRouter). All
            // three open a fresh draft chat; voice/camera additionally seed
            // DeepLinkCoordinator.pendingChatAction so ChatScreen auto-fires
            // the corresponding UI on first compose.
            is DeepLinkAction.NewChat,
            is DeepLinkAction.NewVoiceChat,
            is DeepLinkAction.NewCameraChat -> {
                when (action) {
                    is DeepLinkAction.NewVoiceChat -> DeepLinkCoordinator
                        .setPendingChatAction(DeepLinkCoordinator.ChatAction.START_VOICE)
                    is DeepLinkAction.NewCameraChat -> DeepLinkCoordinator
                        .setPendingChatAction(DeepLinkCoordinator.ChatAction.OPEN_CAMERA)
                    else -> {}
                }
                val newRoute = Routes.chat("__new__${java.util.UUID.randomUUID()}")
                nav.navigate(newRoute) {
                    // [T-fengling-no-home] The new draft replaces the whole
                    // stack up to (and including) the blank placeholder, so
                    // back from the draft exits instead of uncovering it.
                    popUpTo(Routes.SESSION_LIST) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is DeepLinkAction.OpenAlarmList -> {
                // T297: fengling://views/alarm now opens the system Clock app
                // directly via AlarmClock.ACTION_SHOW_ALARMS — the in-app
                // AlarmListScreen was a one-button passthrough that did the
                // exact same thing. The android-alarm tool envelope still
                // emits this view_url so existing chat cards keep working.
                val showAlarms = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    startActivity(showAlarms)
                } catch (_: android.content.ActivityNotFoundException) {
                    AppLogger.warning("DeepLink", "OpenAlarmList: no Clock app handles SHOW_ALARMS")
                }
            }
            else -> {}
        }
    }
}
