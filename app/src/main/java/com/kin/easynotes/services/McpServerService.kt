package com.kin.easynotes.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kin.easynotes.domain.model.Note
import com.kin.easynotes.domain.repository.NoteRepository
import com.kin.easynotes.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {

    @Inject
    lateinit var noteRepository: NoteRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverInstance: NettyApplicationEngine? = null
    private var isServerRunning = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(101, createNotification("Starting AI Server..."))
        
        // Observe settings and start/stop server dynamically
        serviceScope.launch {
            settingsRepository.getPreferences().asMap().let { _ ->
                // We use a simplified way to observe the specific keys
                // In a real app, you'd have a properly mapped Flow in the repository
                while (isActive) {
                    val prefs = settingsRepository.getPreferences()
                    val enabled = prefs[androidx.datastore.preferences.core.booleanPreferencesKey("mcp_enabled")] ?: false
                    val port = prefs[androidx.datastore.preferences.core.intPreferencesKey("mcp_port")] ?: 8080
                    
                    handleServerLifecycle(enabled, port)
                    delay(2000) // Poll every 2 seconds for setting changes
                }
            }
        }
        
        return START_STICKY
    }

    private suspend fun handleServerLifecycle(enabled: Boolean, port: Int) {
        if (enabled && !isServerRunning) {
            startKtorServer(port)
        } else if (!enabled && isServerRunning) {
            stopKtorServer()
        }
    }

    private fun startKtorServer(port: Int) {
        try {
            serverInstance = embeddedServer(Netty, port = port, host = "0.0.0.0") {
                install(SSE)
                
                val mcpServer = Server(
                    serverInfo = Implementation(name = "EasyNotes-MCP-Server", version = "1.0.0"),
                    options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(true)))
                )

                mcpServer.addTool(
                    name = "list_notes",
                    description = "List all notes saved in the app",
                    inputSchema = Tool.Input(properties = emptyMap())
                ) {
                    val notes = runBlocking { noteRepository.getAllNotes().first() }
                    val content = notes.joinToString("\n---\n") { 
                        "ID: ${it.id} | Title: ${it.name}\nContent: ${it.description}" 
                    }
                    CallToolResult(content = listOf(TextContent(content)))
                }

                mcpServer.addTool(
                    name = "add_note",
                    description = "Create a new note in the app",
                    inputSchema = Tool.Input(
                        properties = mapOf(
                            "title" to JsonPrimitive("string"),
                            "content" to JsonPrimitive("string")
                        ),
                        required = listOf("title", "content")
                    )
                ) { request ->
                    val title = request.arguments["title"]?.jsonPrimitive?.content ?: ""
                    val content = request.arguments["content"]?.jsonPrimitive?.content ?: ""
                    runBlocking { noteRepository.addNote(Note(name = title, description = content)) }
                    CallToolResult(content = listOf(TextContent("Note '$title' added successfully!")))
                }

                routing {
                    get("/sse") {
                        val transport = SseServerTransport(this)
                        mcpServer.connect(transport)
                    }
                }
            }.start(wait = false)
            
            isServerRunning = true
            updateNotification("AI Server is running on port $port")
        } catch (e: Exception) {
            isServerRunning = false
            updateNotification("Error starting server: ${e.message}")
        }
    }

    private fun stopKtorServer() {
        serverInstance?.stop(1000, 2000)
        serverInstance = null
        isServerRunning = false
        updateNotification("AI Server is stopped")
    }

    private fun createNotification(content: String): Notification {
        return Notification.Builder(this, "mcp_channel")
            .setContentTitle("EasyNotes MCP")
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
        stopKtorServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
