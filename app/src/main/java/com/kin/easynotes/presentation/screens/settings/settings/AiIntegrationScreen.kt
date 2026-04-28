package com.kin.easynotes.presentation.screens.settings.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kin.easynotes.presentation.components.material.MaterialText
import com.kin.easynotes.presentation.screens.settings.SettingsScaffold
import com.kin.easynotes.presentation.screens.settings.model.SettingsViewModel
import com.kin.easynotes.presentation.screens.settings.widgets.SectionBlock
import com.kin.easynotes.presentation.screens.settings.widgets.SettingSection
import java.net.Inet4Address

@Composable
fun AiIntegrationScreen(navController: NavController, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val settings by viewModel.settings
    val ipAddress = remember { getLocalIpAddress(context) ?: "Unknown IP" }
    val serverUrl = "http://$ipAddress:${settings.mcpPort}/mcp"

    var portText by remember { mutableStateOf(settings.mcpPort.toString()) }

    SettingsScaffold(
        settingsViewModel = viewModel,
        title = "AI Integration",
        onBackNavClicked = { navController.navigateUp() }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Status Section
            item {
                SectionBlock(
                    listOf(
                        SettingSection(
                            title = if (settings.mcpEnabled) "Server is Running" else "Server is Stopped",
                            features = listOf(if (settings.mcpEnabled) serverUrl else "Enable the switch below to start"),
                            icon = Icons.Rounded.Dns,
                            onClick = {}
                        )
                    )
                )
            }

            // Configuration Section
            item {
                Column {
                    Text(
                        text = "Configuration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(32.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        // Enable Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialText(
                                modifier = Modifier.weight(1f),
                                titleSize = 16.sp,
                                title = "Enable MCP Server",
                                description = "Allow AI models to access notes via SSE"
                            )
                            Switch(
                                checked = settings.mcpEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.update(settings.copy(mcpEnabled = isChecked))
                                }
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 24.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )

                        // Port Configuration
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaterialText(
                                modifier = Modifier.weight(1f),
                                titleSize = 16.sp,
                                title = "Server Port",
                                description = "Port for the MCP server"
                            )

                            OutlinedTextField(
                                value = portText,
                                onValueChange = {
                                    if (it.length <= 5 && it.all { char -> char.isDigit() }) {
                                        portText = it
                                        val port = it.toIntOrNull()
                                        if (port != null && port in 1..65535) {
                                            viewModel.update(settings.copy(mcpPort = port))
                                        }
                                    }
                                },
                                modifier = Modifier.width(100.dp),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = LocalTextStyle.current.copy(textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            )
                        }
                    }
                }
            }

            // Connection Guide
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(32.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "How to connect",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "1. Connect phone and PC to the same Wi-Fi.\n" +
                                   "2. Use the SSE transport type in your AI client.\n" +
                                   "3. Add the following to your Claude config:",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "\"easynotes\": {\n  \"command\": \"curl\",\n  \"args\": [\"-N\", \"-s\", \"$serverUrl\"]\n}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Available Tools:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                        )
                        
                        Text(
                            text = "• list_notes: View all notes\n• add_note: Create new notes\n• search_notes: Find specific notes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (settings.mcpEnabled) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(vertical = 14.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Copy Server URL", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
