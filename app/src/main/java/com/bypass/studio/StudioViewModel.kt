package com.bypass.studio

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ChatMessage(val role: String, val content: String)

data class PendingAction(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val path: String,
    val content: String,
    val command: String,
    val status: String = "PENDING"
)

data class ServerInfo(val port: Int, val name: String, val url: String, val running: Boolean)

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = StudioDatabase.get(app).dao()
    private val repo = FileRepo(app)
    val voice = VoiceEngine(app) { log(it) }
    private var server: FileRepo.LocalServer? = null
    private val managedServers = mutableMapOf<Int, FileRepo.LocalServer>()
    private val serverNames = mutableMapOf<Int, String>()
    private var chatJob: Job? = null
    private var streamJob: Job? = null
    private var saveJob: Job? = null
    private var rootChecked = false
    private var rootAvailable = false
    private var pendingCapsule = false

    val theme = mutableStateOf(StudioTheme.OBSIDIAN)
    val tab = mutableStateOf(0)
    val sessions = mutableStateListOf("main")
    val session = mutableStateOf("main")
    val messages = mutableStateListOf<ChatMessage>()
    val streamBuffer = mutableStateOf("")
    val streaming = mutableStateOf(false)
    val input = mutableStateOf("")

    val provider = mutableStateOf(Provider.OPENROUTER)
    val apiKey = mutableStateOf("")
    val model = mutableStateOf("google/gemini-2.0-flash-001")
    val endpoint = mutableStateOf("https://openrouter.ai/api/v1/chat/completions")
    val budget = mutableStateOf(6000)
    val mode = mutableStateOf("Chat")
    val language = mutableStateOf("bn")
    val persona = mutableStateOf("Emma")
    val speed = mutableStateOf(1.2f)
    val autoApprove = mutableStateOf(false)
    val capsule = mutableStateOf(false)
    val overlayGranted = mutableStateOf(false)
    val backgroundAssistant = mutableStateOf(false)
    val rootStatus = mutableStateOf("Not checked")

    val projects = mutableStateListOf<File>()
    val project = mutableStateOf<File>(File(repo.root, "default").apply { mkdirs() })
    val openTabs = mutableStateListOf<File>()
    val currentFile = mutableStateOf<File?>(null)
    val fileContent = mutableStateOf("")
    val fileDirty = mutableStateOf(false)
    val findText = mutableStateOf("")
    val replaceText = mutableStateOf("")

    val terminal = mutableStateListOf<String>()
    val console = mutableStateListOf<String>()
    val serverRunning = mutableStateOf(false)
    val previewUrl = mutableStateOf("http://127.0.0.1:8080/")
    val servers = mutableStateListOf<ServerInfo>()
    val serverPortInput = mutableStateOf("8080")
    val serverPathInput = mutableStateOf("")

    val pendingActions = mutableStateListOf<PendingAction>()
    val actionStatus = mutableStateListOf<String>()

    val memoryRows = mutableStateListOf<Memory>()
    val memoryQuery = mutableStateOf("")
    val freeModels = mutableStateListOf<String>()

    val broadcastScript = mutableStateOf("")
    val broadcastPersonas = mutableStateListOf("Emma", "Rohan")
    val broadcastLang = mutableStateOf("bn")
    val broadcastRatio = mutableStateOf("9:16")
    val broadcastAllVoices = mutableStateOf(false)
    val broadcastProgress = mutableStateOf(0)
    val broadcastLog = mutableStateOf("")
    val lastExport = mutableStateOf<File?>(null)

    val availablePersonas: List<String> get() = voice.personaNames

    private val toolRegex = Regex("""<(file|edit_file|mkdir|delete_file|command|fetch)([^>]*)>([\s\S]*?)</\1>""")
    private val memoryRegex = Regex("""<memory_save>([\s\S]*?)</memory_save>""")

    init {
        voice.init()
        overlayGranted.value = Settings.canDrawOverlays(app)
        refreshProjects()
        refreshMemory()
        viewModelScope.launch {
            theme.value = runCatching {
                StudioTheme.valueOf(dao.allMemory().first().firstOrNull { it.key == "theme" }?.value ?: "OBSIDIAN")
            }.getOrDefault(StudioTheme.OBSIDIAN)
            val rows = dao.allMemory().first()
            apiKey.value = rows.firstOrNull { it.key == "api_key" }?.value.orEmpty()
            model.value = rows.firstOrNull { it.key == "model" }?.value ?: model.value
            endpoint.value = rows.firstOrNull { it.key == "endpoint" }?.value ?: endpoint.value
            persona.value = rows.firstOrNull { it.key == "persona" }?.value ?: persona.value
            language.value = rows.firstOrNull { it.key == "language" }?.value ?: language.value
            runCatching { provider.value = Provider.valueOf(rows.firstOrNull { it.key == "provider" }?.value ?: "OPENROUTER") }
            observeChat()
        }
    }

    private fun observeChat() {
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            dao.chat(session.value).collect { rows ->
                messages.clear()
                rows.forEach { messages.add(ChatMessage(it.role, it.content)) }
            }
        }
    }

    fun log(line: String) {
        val stamped = "[${System.currentTimeMillis() % 100000}] $line"
        console.add(stamped)
        if (console.size > 200) console.removeAt(0)
        broadcastLog.value = console.takeLast(40).joinToString("\n")
    }

    fun setTheme(value: StudioTheme) {
        theme.value = value
        viewModelScope.launch { putMemory("theme", value.name) }
    }

    suspend fun putMemory(key: String, value: String) {
        dao.putMemory(Memory(key, value, System.currentTimeMillis()))
    }

    fun persistSettings() {
        viewModelScope.launch {
            putMemory("api_key", apiKey.value)
            putMemory("model", model.value)
            putMemory("endpoint", endpoint.value)
            putMemory("persona", persona.value)
            putMemory("language", language.value)
            putMemory("provider", provider.value.name)
            log("Settings saved")
        }
    }

    fun refreshMemory(query: String = "") {
        viewModelScope.launch {
            val rows = if (query.isBlank()) dao.allMemory().first() else dao.searchMemory(query)
            memoryRows.clear()
            memoryRows.addAll(rows)
        }
    }

    fun deleteMemory(key: String) {
        viewModelScope.launch {
            dao.deleteMemory(key)
            refreshMemory(memoryQuery.value)
            log("Memory deleted: $key")
        }
    }

    fun clearMemory() {
        viewModelScope.launch {
            dao.clearMemory()
            refreshMemory("")
            log("All memories cleared")
        }
    }

    private suspend fun memorySnapshot(): String {
        val rows = dao.allMemory().first().takeLast(40)
        if (rows.isEmpty()) return "No persistent memory yet."
        return "Persistent memory:\n" + rows.joinToString("\n") { "- ${it.key}: ${it.value}" }
    }

    fun refreshProjects() {
        projects.clear()
        projects.addAll(repo.projects())
        if (projects.none { it.name == project.value.name }) {
            project.value = projects.firstOrNull() ?: File(repo.root, "default").apply { mkdirs() }
        }
    }

    fun switchProject(file: File) {
        project.value = file
        openTabs.clear()
        currentFile.value = null
        fileContent.value = ""
        fileDirty.value = false
        startServer()
        log("Project: ${file.name}")
    }

    fun createProject(name: String) {
        if (name.isBlank()) return
        val dir = repo.createFolder(repo.root, name)
        refreshProjects()
        switchProject(dir)
    }

    fun createFile(name: String) {
        if (name.isBlank()) return
        val f = repo.createFile(project.value, name)
        openFile(f)
        refreshProjects()
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        repo.createFolder(project.value, name)
        refreshProjects()
    }

    fun openFile(file: File) {
        currentFile.value = file
        fileContent.value = runCatching { repo.read(file) }.getOrDefault("")
        fileDirty.value = false
        if (!openTabs.contains(file)) openTabs.add(file)
    }

    fun closeTab(file: File) {
        openTabs.remove(file)
        if (currentFile.value == file) currentFile.value = openTabs.lastOrNull()
        currentFile.value?.let { fileContent.value = repo.read(it) }
        fileDirty.value = false
    }

    fun onEditorChange(text: String) {
        fileContent.value = text
        fileDirty.value = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(800)
            saveFile(silent = true)
        }
    }

    fun saveFile(silent: Boolean = false) {
        val file = currentFile.value ?: return
        repo.write(file, fileContent.value)
        fileDirty.value = false
        if (!silent) log("Saved ${file.name}")
    }

    fun deleteFile(file: File) {
        repo.delete(file)
        openTabs.remove(file)
        if (currentFile.value == file) {
            currentFile.value = null
            fileContent.value = ""
        }
        refreshProjects()
    }

    fun renameFile(file: File, newName: String) {
        if (newName.isBlank()) return
        val renamed = repo.rename(file, newName)
        val idx = openTabs.indexOf(file)
        if (idx >= 0) openTabs[idx] = renamed
        if (currentFile.value == file) currentFile.value = renamed
        refreshProjects()
    }

    fun replaceAll() {
        val from = findText.value
        if (from.isBlank()) return
        onEditorChange(fileContent.value.replace(from, replaceText.value))
        saveFile()
    }

    fun cloneRepo(url: String) {
        viewModelScope.launch {
            try {
                log("Cloning $url")
                val dir = repo.cloneGitHub(url) { log(it) }
                refreshProjects()
                switchProject(dir)
            } catch (e: Exception) {
                log("Clone failed: ${e.message}")
            }
        }
    }

    fun startServer() {
        server?.stop()
        server = repo.LocalServer(project.value, 8080)
        server?.start { log(it) }
        managedServers[8080] = server!!
        serverNames[8080] = project.value.name
        serverRunning.value = true
        previewUrl.value = "http://127.0.0.1:8080/"
        refreshManagedServers()
    }

    fun stopServer() = stopManagedServer(8080)

    fun startManagedServer() {
        val port = serverPortInput.value.toIntOrNull() ?: 8080
        val name = serverPathInput.value.ifBlank { project.value.name }
        val root = if (serverPathInput.value.isBlank()) {
            project.value
        } else {
            File(repo.root, serverPathInput.value).apply { mkdirs() }
        }
        managedServers[port]?.stop()
        val s = repo.LocalServer(root, port)
        s.start { log(it) }
        managedServers[port] = s
        serverNames[port] = name
        if (port == 8080) {
            server = s
            serverRunning.value = true
            previewUrl.value = "http://127.0.0.1:8080/"
        }
        refreshManagedServers()
    }

    fun refreshManagedServers() {
        servers.clear()
        managedServers.forEach { (port, s) ->
            servers.add(ServerInfo(port, serverNames[port] ?: "server-$port", "http://127.0.0.1:$port/", s.isRunning()))
        }
    }

    fun stopManagedServer(port: Int) {
        managedServers[port]?.stop()
        servers.firstOrNull { it.port == port }?.let { old ->
            servers[servers.indexOf(old)] = old.copy(running = false)
        }
        if (port == 8080) {
            server = null
            serverRunning.value = false
        }
        log("Server stopped: $port")
    }

    fun stopAllManagedServers() {
        managedServers.keys.toList().forEach { stopManagedServer(it) }
    }

    fun requestOverlay(context: Context) {
        if (!Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    }

    fun setCapsule(context: Context, enabled: Boolean) {
        capsule.value = enabled
        if (enabled && !Settings.canDrawOverlays(context)) {
            pendingCapsule = true
            requestOverlay(context)
        } else {
            syncOverlay(context)
        }
    }

    fun syncOverlay(context: Context) {
        overlayGranted.value = Settings.canDrawOverlays(context)
        if (overlayGranted.value && pendingCapsule) {
            pendingCapsule = false
            capsule.value = true
        }
        if (!overlayGranted.value && capsule.value) {
            capsule.value = false
            pendingCapsule = true
        }
        val intent = Intent(context, OverlayService::class.java)
        if (capsule.value && overlayGranted.value) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            runCatching { context.stopService(intent) }
        }
    }

    fun syncBackground(context: Context) {
        val intent = Intent(context, VoiceService::class.java)
        if (backgroundAssistant.value) {
            runCatching { ContextCompat.startForegroundService(context, intent) }
        } else {
            runCatching { context.stopService(intent) }
        }
    }

    fun newSession() {
        val id = "session-${System.currentTimeMillis() % 100000}"
        sessions.add(id)
        session.value = id
        observeChat()
    }

    fun switchSession(id: String) {
        session.value = id
        observeChat()
    }

    fun clearChat() {
        viewModelScope.launch {
            dao.clearChat(session.value)
            messages.clear()
        }
    }

    fun discoverFreeModels() {
        viewModelScope.launch {
            freeModels.clear()
            val cfg = AiConfig(provider.value, apiKey.value, model.value, endpoint.value, budget.value)
            val found = AiClient.discoverModels(cfg)
            freeModels.addAll(found)
            log("Discovered ${found.size} models for ${provider.value.label}")
        }
    }

    fun applyPreset(name: String) {
        when (name) {
            "Web app" -> {
                mode.value = "Builder"
                input.value = "Build a polished responsive web app with clean UI, offline fallback, and a self-test checklist."
            }
            "Fix bug" -> {
                mode.value = "Agent"
                input.value = "Find the root cause of this bug, patch the smallest correct file, and explain how to verify it."
            }
            "Explain" -> {
                mode.value = "Chat"
                input.value = "Explain this simply in Bangla, then show one working example."
            }
            "Broadcast" -> {
                mode.value = "Broadcast"
                tab.value = 7
                input.value = "Write a 30-second two-person Bangla broadcast script."
            }
        }
        log("Preset applied: $name")
    }

    fun sendPrompt() {
        val text = input.value.trim()
        if (text.isBlank() || streaming.value) return
        input.value = ""
        messages.add(ChatMessage("user", text))
        viewModelScope.launch { dao.insertChat(Chat(session = session.value, role = "user", content = text)) }
        streamJob?.cancel()
        streaming.value = true
        streamBuffer.value = ""
        streamJob = viewModelScope.launch {
            try {
                val cfg = AiConfig(provider.value, apiKey.value, model.value, endpoint.value, budget.value)
                val wantsGrounding = Regex(
                    """(latest|current|today|news|web|search|ground|live|recent)""",
                    RegexOption.IGNORE_CASE
                ).containsMatchIn(text)
                val grounding = if (wantsGrounding) {
                    "\nLive web grounding for this request:\n" + AiClient.webSearch(text, cfg).take(6000) + "\n"
                } else ""
                val system = "You are Bypass Studio, a professional Android AI coding IDE. " +
                    "${memorySnapshot()}.$grounding Use tool tags <file path=\"...\">content</file>, " +
                    "<edit_file path=\"...\">content</edit_file>, <mkdir path=\"...\">name</mkdir>, " +
                    "<delete_file path=\"...\">path</delete_file>, <command>shell</command>, " +
                    "<fetch>query</fetch>, and <memory_save>fact to remember</memory_save>. " +
                    "Mode: ${mode.value}. Reply in Bangla unless asked otherwise."
                val history = messages.map { it.role to it.content }
                val full = AiClient.stream(cfg, listOf("system" to system) + history) { chunk ->
                    streamBuffer.value += chunk
                }
                saveMemories(full)
                val actions = parseActions(full)
                if (actions.isNotEmpty()) {
                    pendingActions.clear()
                    pendingActions.addAll(actions)
                    actionStatus.clear()
                    actionStatus.add("Analyzing ${actions.first().path.ifBlank { actions.first().type }}")
                    if (autoApprove.value) approveAll()
                } else {
                    appendAssistant(cleanOutput(full))
                }
            } catch (e: Exception) {
                appendAssistant("Error: ${e.message}")
            } finally {
                streaming.value = false
                streamBuffer.value = ""
            }
        }
    }

    private suspend fun saveMemories(full: String) {
        memoryRegex.findAll(full).forEach { match ->
            val fact = match.groupValues[1].trim()
            if (fact.isNotBlank()) {
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                putMemory("auto_${System.currentTimeMillis()}", "$stamp | $fact")
            }
        }
        refreshMemory(memoryQuery.value)
    }

    private fun cleanOutput(full: String): String {
        var out = toolRegex.replace(full, "")
        out = memoryRegex.replace(out, "")
        return out.trim().ifBlank { "Done." }
    }

    private suspend fun appendAssistant(text: String) {
        messages.add(ChatMessage("assistant", text))
        dao.insertChat(Chat(session = session.value, role = "assistant", content = text))
    }

    private fun parseActions(text: String): List<PendingAction> {
        return toolRegex.findAll(text).map { match ->
            val type = match.groupValues[1]
            val attrs = match.groupValues[2]
            val body = match.groupValues[3].trim()
            val pathAttr = Regex("""path="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: ""
            val id = "a${System.nanoTime()}"
            when (type) {
                "command" -> PendingAction(id, type, "Run shell command", body, "", "", body)
                "fetch" -> PendingAction(id, type, "Live web search", body, "", "", body)
                "mkdir" -> PendingAction(id, type, "Create folder", pathAttr, pathAttr, body, "")
                "delete_file" -> PendingAction(id, type, "Delete file", pathAttr, pathAttr, "", "")
                else -> {
                    val existing = if (pathAttr.isNotBlank()) {
                        runCatching { repo.read(File(project.value, pathAttr)) }.getOrDefault("")
                    } else ""
                    PendingAction(
                        id, type,
                        if (type == "file") "Create file" else "Edit file",
                        "Old:\n${existing.take(500)}\n\nNew:\n${body.take(500)}",
                        pathAttr, body, ""
                    )
                }
            }
        }.toList()
    }

    private fun setActionStatus(action: PendingAction, status: String) {
        val index = pendingActions.indexOfFirst { it.id == action.id }
        if (index >= 0) pendingActions[index] = action.copy(status = status)
    }

    fun rejectAction(id: String) {
        val action = pendingActions.firstOrNull { it.id == id } ?: return
        setActionStatus(action, "REJECTED")
        actionStatus.add("Rejected ${action.path.ifBlank { action.type }}")
    }

    fun approveAction(id: String) {
        val action = pendingActions.firstOrNull { it.id == id } ?: return
        viewModelScope.launch { executeAction(action) }
    }

    fun approveAll() {
        val list = pendingActions.filter { it.status == "PENDING" }
        if (list.isEmpty()) return
        list.forEach { setActionStatus(it, "QUEUED") }
        viewModelScope.launch {
            list.forEach { executeAction(it) }
        }
    }

    fun keepAll() {
        val list = pendingActions.filter { it.status == "PENDING" }
        if (list.isEmpty()) return
        viewModelScope.launch {
            list.forEach { action ->
                when (action.type) {
                    "file", "edit_file", "mkdir" -> executeAction(action)
                    else -> {
                        setActionStatus(action, "KEPT")
                        actionStatus.add("Kept for review: ${action.path.ifBlank { action.type }}")
                        appendAssistant("Kept for manual review: ${action.title}")
                    }
                }
            }
        }
    }

    fun rejectAll() {
        pendingActions.clear()
        actionStatus.clear()
        log("All actions rejected")
    }

    fun dismissQueue() {
        pendingActions.clear()
        actionStatus.clear()
        log("Diff queue hidden")
    }

    fun denyAction() = rejectAll()

    private suspend fun executeAction(action: PendingAction) {
        setActionStatus(action, "RUNNING")
        try {
            actionStatus.add("Analyzing ${action.path.ifBlank { action.type }}")
            when (action.type) {
                "file", "edit_file" -> {
                    val target = safeFile(action.path)
                    actionStatus.add("Editing ${action.path}")
                    repo.write(target, action.content)
                    withContext(Dispatchers.Main) { openFile(target) }
                    appendAssistant("Applied ${action.type}: ${action.path}")
                }
                "mkdir" -> {
                    actionStatus.add("Creating ${action.path}")
                    repo.createFolder(project.value, action.content.ifBlank { action.path })
                    withContext(Dispatchers.Main) { refreshProjects() }
                    appendAssistant("Created folder: ${action.path}")
                }
                "delete_file" -> {
                    actionStatus.add("Removing ${action.path}")
                    repo.delete(safeFile(action.path))
                    withContext(Dispatchers.Main) { refreshProjects() }
                    appendAssistant("Deleted: ${action.path}")
                }
                "command" -> {
                    actionStatus.add("Running command")
                    val output = withContext(Dispatchers.IO) { runCommand(action.command) }
                    appendAssistant("Command output:\n$output")
                }
                "fetch" -> {
                    actionStatus.add("Fetching live web data")
                    val cfg = AiConfig(provider.value, apiKey.value, model.value, endpoint.value, budget.value)
                    val result = AiClient.webSearch(action.command, cfg)
                    appendAssistant(result)
                }
            }
            setActionStatus(action, "DONE")
            actionStatus.add("Done: ${action.path.ifBlank { action.type }}")
        } catch (e: Exception) {
            setActionStatus(action, "FAILED")
            actionStatus.add("Failed: ${action.path.ifBlank { action.type }} — ${e.message}")
            appendAssistant("Action failed: ${e.message}")
        }
    }

    private fun safeFile(path: String): File {
        val target = File(project.value, path)
        require(target.canonicalPath.startsWith(project.value.canonicalPath)) { "Path outside project" }
        return target
    }

    fun checkRootOnce(force: Boolean = false) {
        if (rootChecked && !force) return
        rootChecked = true
        viewModelScope.launch {
            val output = withContext(Dispatchers.IO) { runCommand("id -u", capture = true) }
            rootAvailable = output.trim() == "0"
            rootStatus.value = if (rootAvailable) "ROOT" else "STANDARD"
            terminal.add(if (rootAvailable) "[ROOT] Root access available" else "[STANDARD] Root unavailable, using shell/Shizuku mode")
        }
    }

    fun runCommand(command: String, capture: Boolean = false): String {
        terminal.add("$ $command")
        val sb = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(arrayOf("/system/bin/sh", "-c", command))
            BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                lines.forEach { sb.append(it).append('\n'); if (!capture) terminal.add(it) }
            }
            BufferedReader(InputStreamReader(process.errorStream)).useLines { lines ->
                lines.forEach { sb.append(it).append('\n'); if (!capture) terminal.add("err: $it") }
            }
            process.waitFor()
        } catch (e: Exception) {
            sb.append("Error: ${e.message}\n")
            if (!capture) terminal.add("Error: ${e.message}")
        }
        return sb.toString()
    }

    fun clearTerminal() = terminal.clear()

    fun copyTerminal(context: Context): String {
        val text = terminal.joinToString("\n")
        return if (text.isBlank()) {
            "Terminal is empty"
        } else {
            copyText(context, text)
            "Terminal copied"
        }
    }

    fun speak(text: String) = voice.speak(text, language.value, persona.value, speed.value)

    fun startVoiceInput(recognizer: SpeechRecognizer) {
        voice.startListening(
            recognizer,
            onResult = { spoken ->
                val cleaned = applyVoiceCommand(spoken)
                if (cleaned.isNotBlank()) input.value = cleaned
            },
            onError = { log(it) },
            lang = when (language.value) {
                "hi" -> "hi-IN"
                "en" -> "en-US"
                else -> "bn-BD"
            }
        )
    }

    fun applyVoiceCommand(spoken: String): String {
        var result = spoken.trim()
        val aliases = mapOf(
            "emma" to "Emma", "এমা" to "Emma", "সোয়ারা" to "Swara", "স্বরা" to "Swara",
            "অনন্যা" to "Anaya", "অ্যানায়া" to "Anaya", "আরভ" to "Aarav", "রোহান" to "Rohan"
        )
        aliases.forEach { (key, name) ->
            if (result.contains(key, ignoreCase = true)) {
                persona.value = name
                result = result.replace(key, "", ignoreCase = true)
                speak("$name চালু", "bn", name, speed.value)
            }
        }
        val speedMatch = Regex("""(\d+(?:\.\d+)?)\s*(?:x|গুণ|গতি)""").find(result)
        if (speedMatch != null) {
            val value = speedMatch.groupValues[1].toFloatOrNull()
            if (value != null && value in 1.0f..2.0f) {
                speed.value = value
                result = result.replace(speedMatch.value, "")
                speak("গতি ${value} গুণ", "bn", persona.value, speed.value)
            }
        }
        if (result.contains("চালু", true) && result.contains("ব্যাকগ্রাউন্ড", true)) {
            backgroundAssistant.value = true
            result = ""
        }
        if (result.contains("বন্ধ", true) && result.contains("ব্যাকগ্রাউন্ড", true)) {
            backgroundAssistant.value = false
            result = ""
        }
        return result.replace(Regex("\\s+"), " ").trim()
    }

    fun exportAudio(text: String, out: File) {
        if (!voice.ready) {
            log("TTS not ready")
            return
        }
        voice.synthesizeLineToFile(text, out, language.value, persona.value, speed.value) { result ->
            if (result == 0) log("Audio exported: ${out.name}") else log("Audio export failed: $result")
        }
    }

    fun exportVideo(text: String, out: File) {
        exportBroadcastText(text, out)
    }

    private fun exportBroadcastText(text: String, out: File) {
        if (!voice.ready) {
            log("TTS not ready")
            return
        }
        val speakers = broadcastPersonas.ifEmpty { listOf("Emma") }
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapIndexed { i, line ->
                val speaker = speakers[i % speakers.size]
                BroadcastLine(speaker, line, broadcastLang.value, speaker, speed.value)
            }
        if (lines.isEmpty()) {
            log("Broadcast script is empty")
            return
        }
        lastExport.value = out
        broadcastProgress.value = 0
        log("Exporting ${lines.size} lines")
        viewModelScope.launch(Dispatchers.IO) {
            BroadcastExporter.export(
                context = getApplication(),
                voice = voice,
                lines = lines,
                out = out,
                ratio = broadcastRatio.value,
                allVoices = broadcastAllVoices.value,
                onProgress = { p ->
                    viewModelScope.launch(Dispatchers.Main) { broadcastProgress.value = p }
                },
                onLog = { line -> withContext(Dispatchers.Main) { log(line) } }
            )
        }
    }

    fun exportBroadcast() {
        val out = File(getApplication<Application>().codeCacheDir, "exports/broadcast-${System.currentTimeMillis()}.mp4")
        exportBroadcastText(broadcastScript.value, out)
    }

    fun toggleBroadcastPersona(personaName: String) {
        if (broadcastPersonas.contains(personaName)) {
            if (broadcastPersonas.size > 1) broadcastPersonas.remove(personaName)
        } else {
            broadcastPersonas.add(personaName)
        }
    }

    fun saveBroadcastToDownloads() {
        val file = lastExport.value ?: return log("No export file yet")
        val name = "BypassStudio-${System.currentTimeMillis()}.mp4"
        log(ExportManager.saveToDownloads(getApplication(), file, name))
    }

    fun shareBroadcast() {
        val file = lastExport.value ?: return log("No export file yet")
        log(ExportManager.share(getApplication(), file))
    }

    fun apkSignatureSha256(): String = try {
        val pm = getApplication<Application>().packageManager
        val info = pm.getPackageInfo(getApplication<Application>().packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        val cert = info.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        if (cert == null) "unavailable"
        else MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02X".format(it) }
    } catch (e: Exception) {
        "unavailable (${e.message})"
    }
}
