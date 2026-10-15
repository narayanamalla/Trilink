package com.trilink.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.Transfer
import com.trilink.app.data.model.TransferStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(
    transfers: List<Transfer>,
    onCancelTransfer: (id: String) -> Unit,
    onRetryTransfer: (transfer: Transfer) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Active", "Completed", "Queued", "Failed")

    val filteredTransfers = remember(selectedTab, transfers) {
        when (selectedTab) {
            0 -> transfers.filter { it.status == TransferStatus.UPLOADING || it.status == TransferStatus.DOWNLOADING }
            1 -> transfers.filter { it.status == TransferStatus.DELIVERED }
            2 -> transfers.filter { it.status == TransferStatus.QUEUED }
            3 -> transfers.filter { it.status == TransferStatus.FAILED || it.status == TransferStatus.CANCELLED || it.status == TransferStatus.EXPIRED }
            else -> transfers
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Transfers",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp)
        ) {
            // Pinterest-style Horizontal Pill Filter
            Surface(
                shape = RoundedCornerShape(100.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                .clickable { selectedTab = index },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                title,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filteredTransfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(28.dp))
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "No ${tabs[selectedTab].lowercase()} transfers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTransfers) { transfer ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        transfer.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(100.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = transfer.deliveryMode.name,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "${transfer.sizeBytes / 1024} KB · ${transfer.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                if (transfer.status == TransferStatus.UPLOADING || transfer.status == TransferStatus.DOWNLOADING) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    val progress = if (transfer.sizeBytes > 0) {
                                        (transfer.bytesTransferred.toFloat() / transfer.sizeBytes.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${(progress * 100).toInt()}% (${transfer.bytesTransferred / 1024} KB / ${transfer.sizeBytes / 1024} KB)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                        TextButton(onClick = { onCancelTransfer(transfer.id) }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }

                                if (transfer.failureReason != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Reason: ${transfer.failureReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { onRetryTransfer(transfer) },
                                        shape = RoundedCornerShape(100.dp)
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Transfer")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}
