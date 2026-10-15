package com.trilink.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.trilink.app.data.model.Device
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.model.PairingPayload
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.repository.InboxItem
import com.trilink.app.ui.screens.*

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Inbox : Screen("inbox", "Inbox", Icons.Default.Inbox)
    object Transfers : Screen("transfers", "Transfers", Icons.Default.SwapHoriz)
    object Devices : Screen("devices", "Devices", Icons.Default.Devices)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    object Send : Screen("send", "Send", Icons.Default.Send)
    object PairQr : Screen("pair_qr", "Pair", Icons.Default.QrCode)
}

@Composable
fun MainAppNavigation(
    devices: List<Device>,
    transfers: List<Transfer>,
    inboxItems: List<InboxItem>,
    currentDevicePayload: PairingPayload,
    remoteDeliveryEnabled: Boolean,
    onSendText: (Device, String, ItemType) -> Unit,
    onSendFile: (Device, Uri) -> Unit,
    onPairApproved: (PairingPayload) -> Unit,
    onRevokeDevice: (String) -> Unit,
    onCancelTransfer: (String) -> Unit,
    onRetryTransfer: (Transfer) -> Unit,
    onDeleteInboxItem: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleRemoteDelivery: (Boolean) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Inbox,
        Screen.Transfers,
        Screen.Devices,
        Screen.Settings
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.outline,
                                unselectedTextColor = MaterialTheme.colorScheme.outline
                            ),
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    devices = devices,
                    recentTransfers = transfers,
                    onSendClick = { navController.navigate(Screen.Send.route) },
                    onPairClick = { navController.navigate(Screen.PairQr.route) },
                    onDeviceClick = { navController.navigate(Screen.Send.route) }
                )
            }
            composable(Screen.Inbox.route) {
                InboxScreen(
                    inboxItems = inboxItems,
                    onDeleteItem = onDeleteInboxItem,
                    onOpenFile = onOpenFile
                )
            }
            composable(Screen.Transfers.route) {
                TransfersScreen(
                    transfers = transfers,
                    onCancelTransfer = onCancelTransfer,
                    onRetryTransfer = onRetryTransfer
                )
            }
            composable(Screen.Devices.route) {
                DevicesScreen(
                    devices = devices,
                    onPairClick = { navController.navigate(Screen.PairQr.route) },
                    onRevokeDevice = onRevokeDevice
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    remoteDeliveryEnabled = remoteDeliveryEnabled,
                    onToggleRemoteDelivery = onToggleRemoteDelivery
                )
            }
            composable(Screen.Send.route) {
                SendScreen(
                    devices = devices,
                    onSendText = onSendText,
                    onSendFile = onSendFile,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.PairQr.route) {
                PairQrScreen(
                    currentDevicePayload = currentDevicePayload,
                    onPairApproved = onPairApproved,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
