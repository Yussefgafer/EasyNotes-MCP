package com.kin.easynotes.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.kin.easynotes.domain.model.Note
import com.kin.easynotes.domain.repository.NoteRepository
import dagger.hilt.android.AndroidEntryPoint
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.sse.*
import io.modelcontextprotocol.kotlin.sdk.server.*
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject

@AndroidEntryPoint
class McpServerService : Service() {

    @Inject
    lateinit var noteRepository: NoteRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var server: NettyApplicationEngine? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(101, createNotification())
        startMcpServer()
        return START_STICKY
    }

    private fun startMcpServer() {
        server = embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
            install(SSE)
            
            val mcpServer = Server(
                serverInfo = Implementation(name = "EasyNotes-MCP-Server", version = "1.0.0"),
                options = ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(true)))
            )

            // Tool: List all notes
            mcpServer.addTool(
                name = "list_notes",
                description = "List all notes saved in the app",
                inputSchema = Tool.Input(properties = emptyMap())
            ) {
                val notes = noteRepository.getAllNotes().first()
                val content = notes.joinToString("\n---\n") { 
                    "ID: ${it.id} | Title: ${it.name}\nContent: ${it.description}" 
                }
                CallToolResult(content = listOf(TextContent(content)))
            }

            // Tool: Add a new note
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
                noteRepository.addNote(Note(name = title, description = content))
                CallToolResult(content = listOf(TextContent("Note '$title' added successfully!")))
            }

            // Tool: Delete a note
            mcpServer.addTool(
                name = "delete_note",
                description = "Delete a note by its ID",
                inputSchema = Tool.Input(
                    properties = mapOf(
                        "id" to JsonPrimitive("number")
                    ),
                    required = listOf("id")
                )
            ) { request ->
                val id = request.arguments["id"]?.jsonPrimitive?.content?.toIntOrNull()
                if (id != null) {
                    // Note: This requires getting the note first or having a deleteById in repo
                    // For simplicity, we assume the repo can handle a shell note with ID
                    noteRepository.deleteNote(Note(id = id, name = "", description = ""))
                    CallToolResult(content = listOf(TextContent("Note with ID $id deleted.")))
                } else {
                    CallToolResult(content = listOf(TextContent("Invalid ID")), isError = true)
                }
            }

            routing {
                get("/sse") {
                    val transport = SseServerTransport(this)
                    mcpServer.connect(transport)
                }
            }
        }.start(wait = false)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, "mcp_channel")
            .setContentTitle("EasyNotes MCP Server")
            .setContentText("Server is running on port 8080")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel("mcp_channel", "MCP Server Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        server?.stop(1000, 2000)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
