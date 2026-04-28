package com.kin.easynotes.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import com.kin.easynotes.data.repository.SettingsRepositoryImpl
import com.kin.easynotes.domain.model.Note
import com.kin.easynotes.domain.repository.NoteRepository
import com.kin.easynotes.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.server.mcpStreamableHttp
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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
    private var serverInstance: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private var isServerRunning = false
    private var currentPort = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(101, createNotification("AI Server is initializing..."))
        
        serviceScope.launch {
            Log.i(TAG, "Starting settings observation flow")
            val enabledKey = booleanPreferencesKey(SettingsRepositoryImpl.MCP_ENABLED)
            val portKey = intPreferencesKey(SettingsRepositoryImpl.MCP_PORT)
            
            settingsRepository.getPreferencesFlow()
                .map { preferences ->
                    val enabled = preferences[enabledKey] ?: false
                    val port = preferences[portKey] ?: 8080
                    enabled to port
                }
                .distinctUntilChanged()
                .collectLatest { (enabled, port) ->
                    Log.d(TAG, "Reactive settings update: enabled=$enabled, port=$port")
                    handleServerLifecycle(enabled, port)
                }
        }
        
        return START_STICKY
    }

    private suspend fun handleServerLifecycle(enabled: Boolean, port: Int) {
        if (enabled) {
            if (!isServerRunning || currentPort != port) {
                if (isServerRunning) {
                    Log.i(TAG, "Restarting server: port changed from $currentPort to $port")
                    stopKtorServer()
                }
                startKtorServer(port)
            }
        } else if (isServerRunning) {
            Log.i(TAG, "Stopping server: disabled in settings")
            stopKtorServer()
        }
    }

    private fun startKtorServer(port: Int) {
        Log.i(TAG, "Initiating Ktor (CIO) server on port $port")
        try {
            serverInstance = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                // Basic status route for debugging
                routing {
                    get("/") {
                        call.respondText("EasyNotes MCP Server is running! Use /mcp for protocol connection.")
                    }
                }
                
                // MCP protocol transport
                mcpStreamableHttp(path = "/mcp") {
                    Log.i(TAG, "MCP Transport initialized at /mcp")
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
                            description = "List all notes saved in the app",
                            inputSchema = ToolSchema(
                                properties = buildJsonObject {},
                                required = emptyList()
                            )
                        ) { _ ->
                            Log.d(TAG, "Tool Call: list_notes")
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
                            description = "Create a new note in the app",
                            inputSchema = ToolSchema(
                                properties = buildJsonObject {
                                    putJsonObject("title") {
                                        put("type", "string")
                                        put("description", "The title of the note")
                                    }
                                    putJsonObject("content") {
                                        put("type", "string")
                                        put("description", "The body content of the note")
                                    }
                                },
                                required = listOf("title", "content")
                            )
                        ) { request ->
                            val title = request.arguments?.get("title")?.jsonPrimitive?.content ?: ""
                            val content = request.arguments?.get("content")?.jsonPrimitive?.content ?: ""
                            Log.d(TAG, "Tool Call: add_note (title=$title)")

                            if (title.isBlank() && content.isBlank()) {
                                CallToolResult(content = listOf(TextContent("Error: Empty title/content")), isError = true)
                            } else {
                                runBlocking { noteRepository.addNote(Note(name = title, description = content)) }
                                CallToolResult(content = listOf(TextContent("Note added successfully!")))
                            }
                        }

                        addTool(
                            name = "search_notes",
                            description = "Search for notes by a keyword",
                            inputSchema = ToolSchema(
                                properties = buildJsonObject {
                                    putJsonObject("query") {
                                        put("type", "string")
                                        put("description", "The keyword to search for")
                                    }
                                },
                                required = listOf("query")
                            )
                        ) { request ->
                            val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
                            Log.d(TAG, "Tool Call: search_notes ($query)")
                            val notes = runBlocking { noteRepository.getAllNotes().first() }
                            val filtered = notes.filter { it.name.contains(query, true) || it.description.contains(query, true) }
                            val content = if (filtered.isEmpty()) "No results." else filtered.joinToString("\n---\n") { "Title: ${it.name}\n${it.description}" }
                            CallToolResult(content = listOf(TextContent(content)))
                        }
                    }
                }
            }.start(wait = false)
            
            isServerRunning = true
            currentPort = port
            Log.i(TAG, "Server successfully started and listening on port $port")
            updateNotification("MCP Server is active on port $port")
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure starting Ktor server", e)
            isServerRunning = false
            updateNotification("Server Error: ${e.message}")
        }
    }

    private fun stopKtorServer() {
        Log.i(TAG, "Stopping server instance...")
        try {
            serverInstance?.stop(500, 1000)
            Log.d(TAG, "Server instance stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }
        serverInstance = null
        isServerRunning = false
        currentPort = -1
        updateNotification("MCP Server is currently inactive")
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
        Log.i(TAG, "Service being destroyed")
        stopKtorServer()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
