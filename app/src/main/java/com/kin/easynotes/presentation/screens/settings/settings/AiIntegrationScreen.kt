package com.kin.easynotes.presentation.screens.settings.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.kin.easynotes.R
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
        title = "AI Integration (MCP)",
        onBackNavClicked = { navController.navigateUp() }
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                ServerStatusCard(enabled = settings.mcpEnabled, url = serverUrl)
            }

            item {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                SectionBlock(
                    listOf(
                        SettingSection(
                            title = "Enable MCP Server",
                            features = listOf("Allow AI models to access your notes via local network"),
                            icon = Icons.Rounded.PowerSettingsNew,
                            isSwitch = true,
                            switchState = settings.mcpEnabled,
                            onSwitchChange = { 
                                viewModel.update(settings.copy(mcpEnabled = it))
                            }
                        )
                    )
                )
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = "How to connect?", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1. Ensure your PC and phone are on the same Wi-Fi.\n" +
                                   "2. Copy the Server URL above.\n" +
                                   "3. Add it to your AI client (e.g., Claude Desktop config).",
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { clipboardManager.setText(AnnotatedString(serverUrl)) },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(8.dp)
                        ) {
                            Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Server URL")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ServerStatusCard(enabled: Boolean, url: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (enabled) Color(0xFF4CAF50) else Color.Gray)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (enabled) "Server is Running" else "Server is Stopped",
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (enabled) {
                    Text(text = url, fontSize = 12.sp, color = Color(0xFF2E7D32).copy(alpha = 0.8f))
                }
            }
        }
    }
}

fun getLocalIpAddress(context: Context): String? {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    var ipAddress = wifiManager.connectionInfo.ipAddress
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
