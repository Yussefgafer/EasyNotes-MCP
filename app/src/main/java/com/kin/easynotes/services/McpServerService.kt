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
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.modelcontextprotocol.kotlin.sdk.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {

    companion object {
        private const val TAG = "McpServerService"
        private const val AUTH_HEADER = "X-MCP-API-KEY"
        private const val STATIC_API_KEY = "easynotes-secret-123"
    }

    @Inject
    lateinit var noteRepository: NoteRepository
    
    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val serverMutex = Mutex()
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
        serverMutex.withLock {
            if (enabled) {
                if (!isServerRunning || currentPort != port) {
                    if (isServerRunning) {
                        Log.i(TAG, "Lifecycle: Restarting server (port: $currentPort -> $port)")
                        stopKtorServerLocked()
                    }
                    startKtorServerLocked(port)
                }
            } else if (isServerRunning) {
                Log.i(TAG, "Lifecycle: Stopping server (disabled in settings)")
                stopKtorServerLocked()
            }
        }
    }

    private fun startKtorServerLocked(port: Int) {
        Log.i(TAG, "Action: Initiating Ktor (CIO) server on port $port")
        try {
            serverInstance = embeddedServer(CIO, port = port, host = "0.0.0.0") {
                // Personal use: Allow any host but require API Key for critical paths
                install(CORS) {
                    anyHost() 
                    allowMethod(HttpMethod.Options)
                    allowMethod(HttpMethod.Get)
                    allowMethod(HttpMethod.Post)
                    allowMethod(HttpMethod.Delete)
                    allowHeader(HttpHeaders.ContentType)
                    allowHeader(AUTH_HEADER)
                    allowHeader("Mcp-Session-Id")
                    allowHeader("Mcp-Protocol-Version")
                    exposeHeader("Mcp-Session-Id")
                    exposeHeader("Mcp-Protocol-Version")
                    allowNonSimpleContentTypes = true
                }

                // Enhanced Authentication Middleware
                intercept(ApplicationCallPipeline.Plugins) {
                    // 1. Always allow OPTIONS for CORS preflight
                    if (call.request.httpMethod == HttpMethod.Options) return@intercept
                    
                    val path = call.request.path()
                    if (path.startsWith("/mcp")) {
                        // 2. Protect POST and DELETE (Data access and Session management)
                        if (call.request.httpMethod == HttpMethod.Post || call.request.httpMethod == HttpMethod.Delete) {
                            val apiKey = call.request.headers[AUTH_HEADER]
                            if (apiKey != STATIC_API_KEY) {
                                call.respond(HttpStatusCode.Unauthorized, "Invalid or missing MCP API Key")
                                finish()
                            }
                        }
                        // Note: GET is allowed for SSE initialization
                    }
                }

                routing {
                    get("/") {
                        call.respondText("EasyNotes MCP Server is online. Ready at /mcp (Key required for POST)", ContentType.Text.Plain)
                    }
                }
                
                mcpStreamableHttp(path = "/mcp") {
                    Log.i(TAG, "MCP Streamable HTTP Transport initialized at /mcp")
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
                            description = "List all non-encrypted notes saved in the app",
                            inputSchema = ToolSchema(
                                properties = buildJsonObject {},
                                required = emptyList()
                            )
                        ) { _ ->
                            Log.d(TAG, "Tool Call: list_notes")
                            try {
                                val notes = noteRepository.getAllNotes().first()
                                val contentText = if (notes.isEmpty()) {
                                    "No notes found in EasyNotes."
                                } else {
                                    notes.filter { !it.encrypted }.joinToString("\n---\n") { 
                                        "ID: ${it.id} | Title: ${it.name}\nContent: ${it.description}"
                                    }
                                }
                                CallToolResult(content = listOf(TextContent(text = contentText)))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error listing notes", e)
                                CallToolResult(content = listOf(TextContent(text = "Error: ${e.message}")), isError = true)
                            }
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
                            try {
                                val title = request.arguments?.get("title")?.jsonPrimitive?.content?.trim() ?: ""
                                val content = request.arguments?.get("content")?.jsonPrimitive?.content?.trim() ?: ""
                                Log.d(TAG, "Tool Call: add_note (title=$title)")

                                if (title.isBlank() && content.isBlank()) {
                                    CallToolResult(content = listOf(TextContent(text = "Error: Empty title/content")), isError = true)
                                } else {
                                    noteRepository.addNote(Note(name = title, description = content))
                                    CallToolResult(content = listOf(TextContent(text = "Note added successfully!")))
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error adding note", e)
                                CallToolResult(content = listOf(TextContent(text = "Error: ${e.message}")), isError = true)
                            }
                        }

                        addTool(
                            name = "search_notes",
                            description = "Search for non-encrypted notes by a keyword",
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
                            try {
                                val query = request.arguments?.get("query")?.jsonPrimitive?.content ?: ""
                                Log.d(TAG, "Tool Call: search_notes ($query)")
                                val notes = noteRepository.getAllNotes().first()
                                val filtered = notes.filter { !it.encrypted && (it.name.contains(query, true) || it.description.contains(query, true)) }
                                val content = if (filtered.isEmpty()) "No results." else filtered.joinToString("\n---\n") { 
                                    "ID: ${it.id} | Title: ${it.name}\n${it.description}" 
                                }
                                CallToolResult(content = listOf(TextContent(text = content)))
                            } catch (e: Exception) {
                                Log.e(TAG, "Error searching notes", e)
                                CallToolResult(content = listOf(TextContent(text = "Error: ${e.message}")), isError = true)
                            }
                        }
                    }
                }
            }.start(wait = false)
            
            isServerRunning = true
            currentPort = port
            Log.i(TAG, "Success: Server listening on port $port")
            updateNotification("MCP Server active on port $port (Auth Enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Critical failure: Could not start Ktor server", e)
            isServerRunning = false
            updateNotification("Server Error: ${e.message}")
        }
    }

    private fun stopKtorServerLocked() {
        Log.i(TAG, "Action: Stopping server instance")
        try {
            serverInstance?.stop(500, 1000)
            Log.d(TAG, "Success: Server stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error: stopping server", e)
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
        Log.i(TAG, "Service: Being destroyed")
        // FIXED: Using existing serviceScope to perform non-blocking cleanup
        serviceScope.launch {
            try {
                serverMutex.withLock {
                    stopKtorServerLocked()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error during cleanup", e)
            } finally {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
