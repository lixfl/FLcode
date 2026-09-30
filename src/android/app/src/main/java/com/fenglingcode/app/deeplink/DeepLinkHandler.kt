package com.fenglingcode.app.deeplink

import android.net.Uri
import com.fenglingcode.app.ui.navigation.Routes

/**
 * Parses fengling:// deep link URIs into navigation actions.
 *
 * Supported routes (matching iOS):
 *   fengling://share                              → open share flow
 *   fengling://views/alarm                        → open alarm list
 *   fengling://open_terminal?init_command=...      → open terminal with command
 *   fengling://session/<id>                        → open specific session
 *   fengling://settings                            → Settings home
 *   fengling://settings/providers                  → Provider list
 *   fengling://settings/providers/<instanceId>     → Provider detail
 *   fengling://settings/model-groups               → Model Groups (incl. Agent Loop section)
 *   fengling://settings/model-groups/<groupId>     → Model Group detail
 *   fengling://settings/usage                      → Token usage
 *   fengling://settings/skills                     → Skills management
 *   fengling://settings/memory                     → Memory management
 *   fengling://settings/storage                    → Storage management
 *   fengling://settings/mount-external             → Mount External Folders list
 *   fengling://settings/mounts                     → alias for mount-external
 *   fengling://settings/shared-folders             → Shared Folders list (T235)
 *   fengling://settings/shared_folders             → alias for shared-folders
 *   fengling://settings/logs                       → Log management
 *   fengling://settings/appearance                 → Appearance
 *   fengling://settings/background                 → Background settings
 *   fengling://settings/about                      → About
 *   fengling://settings/permissions                → Permissions
 *   fengling://settings/environments[?create_key=...&create_value=...&create_note=...]
 *                                               → Environment variables
 *   fengling://settings/rootfs                     → Rootfs management (mirror config lives here)
 *   fengling://settings/mirrors                    → alias for rootfs (mirrors live inside Rootfs UI)
 *
 * Unknown settings paths fall back to Settings home rather than
 * Unknown — matches iOS's "best-effort land somewhere reasonable"
 * behavior so an LLM-generated link can never strand the user.
 *
 * Resource-class URIs (`fengling://workspace/...`, `fengling://skills/...`,
 * etc.) are intentionally NOT handled here — they resolve to on-disk
 * files and go through `ChatLinkResolver` at the chat-view layer. This
 * parser only handles *navigation* targets that change the app's top-
 * level screen.
 */
sealed class DeepLinkAction {
    data object OpenShare : DeepLinkAction()
    data object OpenAlarmList : DeepLinkAction()
    data class OpenTerminal(val initCommand: String?) : DeepLinkAction()
    data class CreateEnvironmentVariable(val key: String, val value: String, val note: String) : DeepLinkAction()
    data object OpenPermissionSettings : DeepLinkAction()
    data class OpenSession(val sessionId: String) : DeepLinkAction()

    /**
     * App-icon long-press quick actions (mirrors iOS QuickActionRouter).
     *
     *  - [NewChat] — plain new draft chat
     *  - [NewVoiceChat] — new chat + auto-trigger voice input mic on first compose
     *  - [NewCameraChat] — new chat + auto-launch camera attachment on first compose
     *
     * Encoded as `fengling://action/<name>` so the static shortcuts XML can
     * point to them via plain Intent.data without any custom extras —
     * matches the existing fengling:// deep-link conventions.
     */
    data object NewChat : DeepLinkAction()
    data object NewVoiceChat : DeepLinkAction()
    data object NewCameraChat : DeepLinkAction()

    /**
     * T183: any settings screen reachable by route string. Extends the
     * sealed family so consumers don't have to learn 14 new case types
     * — they `safeNavigate(action.route)` and the route table in
     * [com.fenglingcode.app.ui.navigation.Routes] does the rest. Routes
     * with arguments (provider detail, model-group detail) carry the
     * already-formatted route ("provider/<id>", "model_group/<id>").
     */
    data class OpenSettingsScreen(val route: String) : DeepLinkAction()

    /**
     * Launch the in-chat HTML preview, fullscreen, for a pinned home-screen
     * shortcut.
     *
     * Encoded as `fengling://session/<sessionId>/<resource-path>` —
     * [sessionId] selects which chat to land in; [resourcePath] is the
     * resource path under `/var/fengling/` (e.g. `/browser/snake.html`).
     * [title] is the cached page title at pin time, used as the fallback
     * while WebView re-reports its own.
     */
    data class OpenHtmlPreview(
        val sessionId: String,
        val resourcePath: String,
        val title: String,
    ) : DeepLinkAction()

    data object Unknown : DeepLinkAction()
}

object DeepLinkHandler {
    fun parse(uri: Uri?): DeepLinkAction {
        if (uri == null || uri.scheme != "fengling") return DeepLinkAction.Unknown
        val host = uri.host ?: return DeepLinkAction.Unknown
        val path = uri.path.orEmpty()

        return when (host) {
            "share" -> DeepLinkAction.OpenShare
            "views" -> when (path) {
                "/alarm" -> DeepLinkAction.OpenAlarmList
                else -> DeepLinkAction.Unknown
            }
            "open_terminal" -> DeepLinkAction.OpenTerminal(
                initCommand = uri.getQueryParameter("init_command")
            )
            // Quick-actions surface (app-icon long-press). Path drives which
            // pending action ChatScreen consumes on first compose. Mirrors iOS
            // QuickActionRouter.swift action ids 1:1.
            "action" -> when (path.removePrefix("/")) {
                "new_chat" -> DeepLinkAction.NewChat
                "voice_chat" -> DeepLinkAction.NewVoiceChat
                "camera_chat" -> DeepLinkAction.NewCameraChat
                else -> DeepLinkAction.Unknown
            }
            "settings" -> parseSettingsPath(uri)
            "session" -> {
                // fengling://session/<sessionId>                → OpenSession
                // fengling://session/<sessionId>/<resource-path> → OpenHtmlPreview
                val segments = path.removePrefix("/")
                    .split('/')
                    .filter { it.isNotEmpty() }
                val sid = segments.firstOrNull()
                when {
                    sid.isNullOrBlank() -> DeepLinkAction.Unknown
                    segments.size == 1 -> DeepLinkAction.OpenSession(sid)
                    else -> {
                        val resourcePath = "/" + segments.drop(1).joinToString("/")
                        val title = uri.getQueryParameter("title").orEmpty()
                        DeepLinkAction.OpenHtmlPreview(sid, resourcePath, title)
                    }
                }
            }
            else -> DeepLinkAction.Unknown
        }
    }

    /**
     * T183: walk a `fengling://settings/<path>` URI to the right NavHost
     * route. Two cases stay distinct:
     *
     *  - `environments?create_key=…` — keeps the dedicated
     *    [DeepLinkAction.CreateEnvironmentVariable] case because the
     *    target screen needs the parameters wired through
     *    `DeepLinkCoordinator.setPendingEnvVarCreate`.
     *  - `permissions` — keeps [DeepLinkAction.OpenPermissionSettings]
     *    so existing dispatch logic stays unchanged.
     *
     * Everything else funnels through [DeepLinkAction.OpenSettingsScreen]
     * carrying a route string from [Routes]. Unknown paths land on
     * Settings home rather than [DeepLinkAction.Unknown] so an LLM-
     * generated link can never strand the user; iOS does the same.
     */
    private fun parseSettingsPath(uri: Uri): DeepLinkAction {
        val path = uri.path?.trimStart('/').orEmpty()
        if (path.isEmpty()) return DeepLinkAction.OpenSettingsScreen(Routes.SETTINGS)
        val segments = path.split('/').filter { it.isNotEmpty() }
        val head = segments.firstOrNull() ?: return DeepLinkAction.OpenSettingsScreen(Routes.SETTINGS)
        val arg = segments.getOrNull(1)?.takeIf { it.isNotBlank() }

        return when (head) {
            "providers" ->
                if (arg != null) DeepLinkAction.OpenSettingsScreen(Routes.providerDetail(arg))
                else DeepLinkAction.OpenSettingsScreen(Routes.PROVIDER_LIST)
            "model-groups", "model_groups" ->
                if (arg != null) DeepLinkAction.OpenSettingsScreen(Routes.modelGroupDetail(arg))
                else DeepLinkAction.OpenSettingsScreen(Routes.MODEL_GROUPS)
            "usage", "usage-stats", "usage_stats" ->
                DeepLinkAction.OpenSettingsScreen(Routes.USAGE_STATS)
            "skills" -> DeepLinkAction.OpenSettingsScreen(Routes.SKILLS)
            "memory" -> DeepLinkAction.OpenSettingsScreen(Routes.MEMORY)
            "storage" -> DeepLinkAction.OpenSettingsScreen(Routes.STORAGE)
            "mount-external", "mount_external", "mounts", "mounted-folders", "mounted_folders" ->
                DeepLinkAction.OpenSettingsScreen(Routes.MOUNTED_FOLDERS)
            "shared-folders", "shared_folders" ->
                DeepLinkAction.OpenSettingsScreen(Routes.SHARED_FOLDERS)
            "logs" -> {
                // Optional ?tab=… selects the segmented control on the
                // Logs screen. Currently recognized: "logs" (default),
                // "config-audit". Pushed onto DeepLinkCoordinator so the
                // screen can read it on appear and clear. Mirrors iOS
                // DeepLinkRouter.handleSettings logs handling.
                DeepLinkCoordinator.setPendingLogsTab(uri.getQueryParameter("tab"))
                DeepLinkAction.OpenSettingsScreen(Routes.LOGS)
            }
            "appearance" -> DeepLinkAction.OpenSettingsScreen(Routes.APPEARANCE)
            "background" -> DeepLinkAction.OpenSettingsScreen(Routes.BACKGROUND)
            "permissions" -> DeepLinkAction.OpenPermissionSettings
            // mirrors live as a section inside Rootfs management — no
            // standalone destination, so route both /mirrors and /rootfs
            // there. The user lands on the same screen; mirror config is
            // visible as the "Mirrors" section inside.
            "mirrors", "rootfs", "rootfs-management", "rootfs_management" ->
                DeepLinkAction.OpenSettingsScreen(Routes.ROOTFS_MANAGEMENT)
            "environments" -> {
                // iOS parity (AIChatView.swift L1407-1411): only `create_key`
                // is required. Missing `create_value`/`create_note` default
                // to empty string so a link like
                //   fengling://settings/environments?create_key=GH_TOKEN&create_value=
                // (where `create_value=` is present-but-blank) still opens
                // the prefilled form. Without create_key, plain navigation
                // to the Env Vars list.
                val key = uri.getQueryParameter("create_key")
                if (!key.isNullOrEmpty()) {
                    DeepLinkAction.CreateEnvironmentVariable(
                        key = key,
                        value = uri.getQueryParameter("create_value") ?: "",
                        note = uri.getQueryParameter("create_note") ?: "",
                    )
                } else {
                    DeepLinkAction.OpenSettingsScreen(Routes.ENV_VARS)
                }
            }
            // Unknown path — land on Settings home rather than failing,
            // so the user can find what they wanted by browsing.
            else -> DeepLinkAction.OpenSettingsScreen(Routes.SETTINGS)
        }
    }
}
