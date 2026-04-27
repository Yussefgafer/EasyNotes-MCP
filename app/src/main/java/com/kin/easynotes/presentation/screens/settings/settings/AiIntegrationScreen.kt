package com.kin.easynotes.presentation.screens.settings.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
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
import com.kin.easynotes.presentation.components.material.MaterialText
import com.kin.easynotes.presentation.screens.settings.SettingsScaffold
import com.kin.easynotes.presentation.screens.settings.model.SettingsViewModel
import com.kin.easynotes.presentation.screens.settings.widgets.SectionBlock
import com.kin.easynotes.presentation.screens.settings.widgets.SettingSection
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteOrder

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
            verticalArrangement = Arrangement.spacedBy(24.dp)
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

            // Controls Section (Manual Switch to match Solid design)
            item {
                Column {
                    Text(
                        text = "Configuration",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    )
                    
                    // Custom Row for Switch since SettingSection doesn't support it
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(32.dp)) // Matching app's SectionBlock shape
                            .background(color = MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            MaterialText(
                                titleSize = 16.sp,
                                title = "Enable MCP Server",
                                description = "Allow AI models to access notes"
                            )
                        }
                        Switch(
                            checked = settings.mcpEnabled,
                            onCheckedChange = { 
                                viewModel.update(settings.copy(mcpEnabled = it))
                            }
                        )
                    }
                }
            }

            // Connection Guide
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "1. Connect phone and PC to the same Wi-Fi.\n" +
                                   "2. Copy the URL and add it to your AI client.",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp
                        )
                        if (settings.mcpEnabled) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Copy Server URL")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var ipAddress = wifiManager.connectionInfo.ipAddress
    if (ipAddress == 0) return null
    
    if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) {
        ipAddress = Integer.reverseBytes(ipAddress)
    }
    val ipByteArray = BigInteger.valueOf(ipAddress.toLong()).toByteArray()
    return try {
        InetAddress.getByAddress(ipByteArray).hostAddress
    } catch (e: Exception) {
        null
    }
}
