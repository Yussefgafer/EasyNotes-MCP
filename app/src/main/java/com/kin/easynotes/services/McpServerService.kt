package com.kin.easynotes.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.kin.easynotes.data.repository.SettingsRepositoryImpl
import com.kin.easynotes.domain.model.Note
import com.kin.easynotes.domain.repository.NoteRepository
import com.kin.easynotes.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {

    companion object {
        private const val TAG = "McpServerService"
    }

    @Inject
    lateinit var noteRepository: NoteRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverInstance: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null
    private var isServerRunning = false
    private var currentPort = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(101, createNotification("AI Server is initializing..."))
        
        serviceScope.launch {
            while (isActive) {
                try {
                    val enabled = settingsRepository.getBoolean(SettingsRepositoryImpl.MCP_ENABLED) ?: false
                    val port = settingsRepository.getInt(SettingsRepositoryImpl.MCP_PORT) ?: 8080

                    Log.d(TAG, "Lifecycle check: enabled=$enabled, port=$port, running=$isServerRunning, currentPort=$currentPort")

                    handleServerLifecycle(enabled, port)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in service lifecycle loop", e)
                }
                delay(5000)
            }
        }
        
        return START_STICKY
    }

    private suspend fun handleServerLifecycle(enabled: Boolean, port: Int) {
        if (enabled) {
            if (!isServerRunning || currentPort != port) {
                if (isServerRunning) {
                    Log.i(TAG, "Restarting server because of config change (port: $currentPort -> $port)")
                    stopKtorServer()
                }
                startKtorServer(port)
            }
        } else if (isServerRunning) {
            Log.i(TAG, "Stopping server because it was disabled in settings")
            stopKtorServer()
        }
    }

    private fun startKtorServer(port: Int) {
        Log.i(TAG, "Attempting to start Ktor server on port $port...")
        try {
            serverInstance = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                Log.d(TAG, "Ktor application configuring...")
                mcpStreamableHttp(path = "/mcp") {
                    Log.d(TAG, "MCP Streamable HTTP transport initializing at /mcp")
                    Server(
                        serverInfo = Implementation(name = "EasyNotes-MCP", version = "1.2.0"),
                        options = ServerOptions(
                            capabilities = ServerCapabilities(
                                tools = ServerCapabilities.Tools(listChanged = true)
                            )
                        )
                    ).apply {
                        addTool(
                            name = "list_notes",
                            description = "List all notes saved in the app"
                        ) { _ ->
                            Log.d(TAG, "MCP Tool Call: list_notes")
                            val notes = runBlocking { noteRepository.getAllNotes().first() }
                            val contentText = if (notes.isEmpty()) {
                                "No notes found in EasyNotes."
                            } else {
                                notes.joinToString("\n---\n") {
                                    "ID: ${it.id} | Title: ${it.name}\nContent: ${it.description}"
                                }
                            }
                            CallToolResult(content = listOf(TextContent(contentText)))
                        }

                        addTool(
                            name = "add_note",
                            description = "Create a new note in the app"
                        ) { request ->
                            val title = request.arguments?.get("title")?.jsonPrimitive?.content ?: ""
                            val content = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
                            Log.d(TAG, "MCP Tool Call: add_note (title=$title)")

                            if (title.isBlank() && content.isBlank()) {
                                CallToolResult(
                                    content = listOf(TextContent("Error: Both title and content are missing.")),
                                    isError = true
                                )
                            } else {
                                runBlocking { noteRepository.addNote(Note(name = title, description = content)) }
                                CallToolResult(content = listOf(TextContent("Note \"$title\" added successfully to EasyNotes!")))
                            }
                        }
                    }
                }
            }.start(wait = false)
            
            isServerRunning = true
            currentPort = port
            Log.i(TAG, "Ktor server started successfully on port $port")
            updateNotification("AI Server (MCP) is running on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Ktor server on port $port", e)
            isServerRunning = false
            updateNotification("Server Error: ${e.message}")
        }
    }

    private fun stopKtorServer() {
        Log.i(TAG, "Stopping Ktor server (running on $currentPort)...")
        try {
            serverInstance?.stop(1000, 2000)
            Log.d(TAG, "Ktor server instance stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error while stopping Ktor server", e)
        }
        serverInstance = null
        isServerRunning = false
        currentPort = -1
        updateNotification("AI Server is currently stopped")
    }

    private fun createNotification(content: String): Notification {
        return Notification.Builder(this, "mcp_channel")
            .setContentTitle("EasyNotes MCP Connector")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(101, createNotification(content))
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("mcp_channel", "MCP Server Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        Log.i(TAG, "McpServerService being destroyed")
        stopKtorServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
