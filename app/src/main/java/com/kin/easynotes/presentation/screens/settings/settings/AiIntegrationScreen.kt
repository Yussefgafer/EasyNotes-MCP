package com.kin.easynotes.presentation.screens.settings.settings

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
    val serverUrl = "http://$ipAddress:${settings.mcpPort}/sse"

    SettingsScaffold(
        settingsViewModel = viewModel,
        title = "AI Integration",
        onBackNavClicked = { navController.navigateUp() }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Server Status Section
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
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "Configuration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )
                    
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                MaterialText(
                                    titleSize = 16.sp,
                                    title = "Enable MCP Server",
                                    description = "Allow AI models to access notes via the Model Context Protocol"
                                )
                            }
                            Switch(
                                checked = settings.mcpEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.update(settings.copy(mcpEnabled = isChecked))
                                }
                            )
                        }
                    }
                }
            }

            // Connection Guide
            item {
                Card(
                    modifier = Modifier.padding(horizontal = 8.dp),
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
                                   "2. Copy the URL and add it to your AI client configuration (e.g., Claude Desktop).",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (settings.mcpEnabled) {
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
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
