package com.fenglingcode.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.fenglingcode.app.BuildConfig
import com.fenglingcode.app.R
import com.fenglingcode.app.ui.components.openExternalUrl
import com.fenglingcode.app.i18n.uppercaseForDisplay
import com.fenglingcode.app.ui.theme.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onProvidersClick: () -> Unit,
    onModelGroupsClick: () -> Unit,
    // [T-android-settings-reorg] Storage-overview entry (renamed from the
    // ambiguous onRootfsClick); the actual Rootfs Management page got its own
    // row moved in from the home shell menu.
    onStorageClick: () -> Unit = {},
    // [T-android-settings-reorg] Rootfs management, moved out of the home shell
    // overflow menu into Settings.
    onRootfsManagementClick: () -> Unit = {},
    onBackupClick: () -> Unit = {},
    onEnvVarsClick: () -> Unit = {},
    onSkillsClick: () -> Unit = {},
    onTerminalClick: () -> Unit = {},
    onMemoryClick: () -> Unit = {},
    // [T-mcp-integration-android] MCP Integrations page, listed directly below
    // Memory. Default no-op for callers that haven't wired the route yet.
    onMcpClick: () -> Unit = {},
    // [T-soul-md] Soul settings page lives between Skills and Memory in the
    // Agent Runtime section; default no-op for callers that haven't wired
    // the route yet.
    onSoulClick: () -> Unit = {},
    onPermissionsClick: () -> Unit = {},
    onUsageClick: () -> Unit = {},
    onAppearanceClick: () -> Unit = {},
    onLogsClick: () -> Unit = {},
    // T219-2: Mount External Folders entry. Default no-op for any caller
    // that hasn't wired the route yet.
    onMountedFoldersClick: () -> Unit = {},
    // T235: Shared Folders entry (Shared / Skills / Memory). Default no-op
    // for back-compat with callers wired before T235.
    onSharedFoldersClick: () -> Unit = {},
    // T50: Background & Notifications screen (battery optimisation +
    // OEM autostart guidance). Default no-op so older callers/tests
    // don't need to be retrofitted.
    onBackgroundClick: () -> Unit = {},
    // [T-android-settings-reorg] Scheduled-tasks management, moved off the home
    // toolbar into Settings. Default no-op for callers not yet wired.
    onScheduledTasksClick: () -> Unit = {},
    // [T-android-settings-reorg] Browser settings, moved out of the home shell
    // overflow menu into Settings.
    onBrowserSettingsClick: () -> Unit = {},
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            AppIcons.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // -- Models & Providers --
            SettingsSection(
                title = stringResource(R.string.settings_section_llm_providers),
                footer = stringResource(R.string.settings_section_llm_providers_footer),
            ) {
                SettingsItem(
                    icon = AppIcons.Shop,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_manage_providers),
                    subtitle = stringResource(R.string.settings_manage_providers_subtitle),
                    onClick = onProvidersClick,
                )
                SettingsItem(
                    icon = AppIcons.Settings,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_model_groups),
                    subtitle = stringResource(R.string.settings_model_groups_subtitle),
                    onClick = onModelGroupsClick,
                )
                SettingsItem(
                    icon = AppIcons.BarChart,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_token_usage),
                    subtitle = stringResource(R.string.settings_token_usage_subtitle),
                    onClick = onUsageClick,
                    showDivider = false,
                )
            }

            // -- [T-android-settings-reorg] General: appearance + the two
            // entries moved here from the home screen (scheduled tasks from
            // the toolbar button, browser settings from the shell menu). --
            SettingsSection(
                title = stringResource(R.string.settings_section_general),
                footer = stringResource(R.string.settings_section_general_footer),
            ) {
                SettingsItem(
                    icon = AppIcons.Contrast,
                    iconColor = Color(0xFF5856D6),
                    title = stringResource(R.string.settings_section_appearance),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    onClick = onAppearanceClick,
                )
                SettingsItem(
                    icon = AppIcons.Schedule,
                    iconColor = Color(0xFFFF9500),
                    title = stringResource(R.string.sessionlist_scheduled_tasks),
                    subtitle = stringResource(R.string.settings_scheduled_tasks_subtitle),
                    onClick = onScheduledTasksClick,
                )
                SettingsItem(
                    icon = AppIcons.Computer,
                    iconColor = Color(0xFF30B0C7),
                    title = stringResource(R.string.sessionlist_browser_settings),
                    subtitle = stringResource(R.string.settings_browser_settings_subtitle),
                    onClick = onBrowserSettingsClick,
                    showDivider = false,
                )
            }

            // -- Agent Runtime --
            SettingsSection(title = stringResource(R.string.settings_section_agent_runtime)) {
                SettingsItem(
                    icon = AppIcons.Extension,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_skills),
                    subtitle = stringResource(R.string.settings_skills_subtitle),
                    onClick = onSkillsClick,
                )
                // [T-soul-md] insertion between Skills and Memory per spec.
                SettingsItem(
                    icon = AppIcons.Assistant,
                    iconColor = Color(0xFFFF9500),
                    title = stringResource(R.string.settings_soul),
                    subtitle = stringResource(R.string.settings_soul_subtitle),
                    onClick = onSoulClick,
                )
                SettingsItem(
                    icon = AppIcons.Psychology,
                    iconColor = Color(0xFF5856D6),
                    title = stringResource(R.string.settings_memory),
                    subtitle = stringResource(R.string.settings_memory_subtitle),
                    onClick = onMemoryClick,
                )
                // [T-mcp-integration-android] MCP Integrations — directly below Memory.
                // [T-android-mcp-icon-distinct] Dashboard (2x2 block grid) instead of
                // Extension so MCP no longer shares the Skills row's puzzle-piece icon —
                // the grid reads as "multiple composed blocks/servers". teal unchanged.
                SettingsItem(
                    icon = AppIcons.Dashboard,
                    iconColor = Color(0xFF30B0C7),
                    title = stringResource(R.string.settings_mcp),
                    subtitle = stringResource(R.string.settings_mcp_subtitle),
                    onClick = onMcpClick,
                )
                SettingsItem(
                    icon = AppIcons.Terminal,
                    iconColor = Color(0xFF34C759),
                    title = stringResource(R.string.settings_env_vars),
                    subtitle = stringResource(R.string.settings_env_vars_subtitle),
                    onClick = onEnvVarsClick,
                    showDivider = false,
                )
            }

            // -- [T-android-settings-reorg] Storage & Data: capacity overview,
            // rootfs management (moved in from the home shell menu), folder
            // sharing, backup --
            SettingsSection(title = stringResource(R.string.settings_section_storage)) {
                SettingsItem(
                    icon = AppIcons.CreateDisk,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_section_storage),
                    subtitle = stringResource(R.string.settings_storage_subtitle),
                    onClick = onStorageClick,
                )
                SettingsItem(
                    icon = AppIcons.Sandbox,
                    iconColor = Color(0xFF8E8E93),
                    title = stringResource(R.string.sessionlist_rootfs_management),
                    subtitle = stringResource(R.string.settings_rootfs_subtitle),
                    onClick = onRootfsManagementClick,
                )
                SettingsItem(
                    icon = AppIcons.Folder,
                    iconColor = Color(0xFF34C759),
                    title = stringResource(R.string.settings_shared_folders),
                    subtitle = stringResource(R.string.settings_shared_folders_subtitle),
                    onClick = onSharedFoldersClick,
                )
                SettingsItem(
                    icon = AppIcons.MountStorage,
                    iconColor = Color(0xFFFF9500),
                    title = stringResource(R.string.settings_mount_external_folders),
                    subtitle = stringResource(R.string.settings_mount_external_folders_subtitle),
                    onClick = onMountedFoldersClick,
                )
                SettingsItem(
                    icon = AppIcons.Backup,
                    iconColor = Color(0xFF34C759),
                    title = stringResource(R.string.settings_backup_restore),
                    subtitle = stringResource(R.string.settings_backup_restore_subtitle),
                    onClick = onBackupClick,
                    showDivider = false,
                )
            }

            // -- [T-android-settings-reorg] System: permissions, background
            // behaviour, logs --
            SettingsSection(title = stringResource(R.string.settings_section_system)) {
                SettingsItem(
                    icon = AppIcons.Shield,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_section_permissions),
                    subtitle = stringResource(R.string.settings_permissions_subtitle),
                    onClick = onPermissionsClick,
                )
                SettingsItem(
                    icon = AppIcons.Hibernate,
                    iconColor = Color(0xFFFF9500),
                    title = stringResource(R.string.bg_section_header),
                    subtitle = stringResource(R.string.bg_section_subtitle),
                    onClick = onBackgroundClick,
                )
                SettingsItem(
                    icon = AppIcons.Log,
                    iconColor = Color(0xFF007AFF),
                    title = stringResource(R.string.settings_section_logs),
                    subtitle = stringResource(R.string.settings_logs_subtitle),
                    onClick = onLogsClick,
                    showDivider = false,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

}

/**
 * A grouped settings section with header and optional footer, matching iOS grouped List sections.
 */
@Composable
private fun SettingsSection(
    title: String,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp),
    ) {
        // Section header
        Text(
            text = title.uppercaseForDisplay(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )

        // Section card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(color = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            content()
        }

        // Section footer
        if (footer != null) {
            Text(
                text = footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                lineHeight = 16.sp,
            )
        }
    }
}

/**
 * A single settings row item with colored icon, title, optional subtitle, and chevron.
 * Styled to match iOS settings rows with SF Symbol-like colored circle icons.
 */
@Composable
private fun SettingsItem(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Colored circle icon (matching iOS settings style)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(
                        color = iconColor,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }

            Spacer(Modifier.width(14.dp))

            // Title + subtitle
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Chevron
            Icon(
                imageVector = AppIcons.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp),
            )
        }

        // Divider between items (inset to match icon alignment)
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 58.dp, end = 14.dp)
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            )
        }
    }
}

