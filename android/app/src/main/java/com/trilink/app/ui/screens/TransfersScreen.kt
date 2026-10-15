package com.trilink.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        topBar = {
            TopAppBar(
                title = { Text("Transfers") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            if (filteredTransfers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No ${tabs[selectedTab].lowercase()} transfers.",
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredTransfers) { transfer ->
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        transfer.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(transfer.deliveryMode.name) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Status: ${transfer.status.name} • ${transfer.sizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )

                                if (transfer.status == TransferStatus.UPLOADING || transfer.status == TransferStatus.DOWNLOADING) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val progress = if (transfer.sizeBytes > 0) {
                                        (transfer.bytesTransferred.toFloat() / transfer.sizeBytes.toFloat()).coerceIn(0f, 1f)
                                    } else 0f
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            "${(progress * 100).toInt()}% (${transfer.bytesTransferred / 1024} KB / ${transfer.sizeBytes / 1024} KB)",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        TextButton(onClick = { onCancelTransfer(transfer.id) }) {
                                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                if (transfer.failureReason != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Reason: ${transfer.failureReason}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedButton(onClick = { onRetryTransfer(transfer) }) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Retry Transfer")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
