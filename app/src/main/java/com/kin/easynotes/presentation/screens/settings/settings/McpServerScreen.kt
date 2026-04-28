package com.kin.easynotes.presentation.screens.settings.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.SettingsEthernet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kin.easynotes.presentation.screens.settings.SettingsScaffold
import com.kin.easynotes.presentation.screens.settings.model.SettingsViewModel
import com.kin.easynotes.presentation.screens.settings.widgets.ActionType
import com.kin.easynotes.presentation.screens.settings.widgets.SettingsBox
import java.net.Inet4Address

@Composable
fun McpServerScreen(navController: NavController, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by viewModel.settings
    val ipAddress = remember { getLocalIpAddress(context) ?: "Unknown IP" }
    val serverUrl = "http://$ipAddress:${settings.mcpPort}/mcp"

    SettingsScaffold(
        settingsViewModel = viewModel,
        title = "MCP Server",
        onBackNavClicked = { navController.navigateUp() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Server Management Section
            item {
                Text(
                    text = "Server Management",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp)
                )
                
                SettingsBox(
                    settingsViewModel = viewModel,
                    title = if (settings.mcpEnabled) "Server is Running" else "Server is Stopped",
                    description = if (settings.mcpEnabled) serverUrl else "Enable MCP to connect AI models",
                    icon = Icons.Rounded.Dns,
                    actionType = ActionType.CUSTOM,
                    radius = shapeManager(isFirst = true, radius = settings.cornerRadius),
                    customAction = { } // Port adjustment logic can be added here or via a dialog like other settings
                )
                
                SettingsBox(
                    settingsViewModel = viewModel,
                    title = "Enable MCP Server",
                    description = "Allow AI models to access notes via SSE transport",
                    icon = Icons.Rounded.PowerSettingsNew,
                    actionType = ActionType.SWITCH,
                    switchChecked = settings.mcpEnabled,
                    onSwitchCheckedChange = { isChecked ->
                        viewModel.update(settings.copy(mcpEnabled = isChecked))
                    },
                    radius = shapeManager(radius = settings.cornerRadius)
                )

                SettingsBox(
                    settingsViewModel = viewModel,
                    title = "Server Port",
                    description = "Current port: ${settings.mcpPort}",
                    icon = Icons.Rounded.SettingsEthernet,
                    actionType = ActionType.CUSTOM,
                    radius = shapeManager(isLast = true, radius = settings.cornerRadius),
                    customAction = { onExit -> 
                        // Port adjustment logic can be added here or via a dialog like other settings
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Connection Guide & Documentation
            item {
                Text(
                    text = "Connection Guide",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(settings.cornerRadius.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "How to connect",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "1. Connect phone and PC to the same Wi-Fi.\n" +
                                   "2. Add the following to your Claude config:",
                            style = MaterialTheme.typography.bodySmall,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "\"easynotes\": {\n  \"command\": \"curl\",\n  \"args\": [\"-N\", \"-s\", \"$serverUrl\"]\n}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Available Tools:",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        
                        Text(
                            text = "• list_notes: View all notes\n• add_note: Create new notes\n• search_notes: Find specific notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (settings.mcpEnabled) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape((settings.cornerRadius / 2).dp),
                                contentPadding = PaddingValues(vertical = 10.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Copy Server URL", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String? {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network: Network? = connectivityManager.activeNetwork
    val linkProperties: LinkProperties? = connectivityManager.getLinkProperties(network)

    return linkProperties?.linkAddresses
        ?.map { it.address }
        ?.filterIsInstance<Inet4Address>()
        ?.firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}
