package com.trilink.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trilink.app.data.model.ItemType
import com.trilink.app.data.repository.InboxItem
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    inboxItems: List<InboxItem>,
    onDeleteItem: (id: String) -> Unit,
    onOpenFile: (localPath: String) -> Unit
) {
    val context = LocalContext.current
    val dateFormatter = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Inbox") }
            )
        }
    ) { padding ->
        if (inboxItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Inbox is empty.\nTransferred items will appear here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(inboxItems) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (item.itemType) {
                                            ItemType.TEXT -> Icons.Default.Notes
                                            ItemType.URL -> Icons.Default.Link
                                            ItemType.IMAGE -> Icons.Default.Image
                                            ItemType.FILE -> Icons.Default.InsertDriveFile
                                        },
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.displayName,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                IconButton(onClick = { onDeleteItem(item.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "From: ${item.senderDeviceName} • ${dateFormatter.format(Date(item.receivedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )

                            if (item.textContent != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = item.textContent,
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                if (item.textContent != null) {
                                    TextButton(onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("TriLink Content", item.textContent))
                                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy")
                                    }
                                }
                                if (item.localPath != null) {
                                    Button(onClick = { onOpenFile(item.localPath) }) {
                                        Icon(Icons.Default.OpenInNew, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Open")
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
