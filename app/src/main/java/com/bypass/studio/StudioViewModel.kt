package com.bypass.studio

import android.app.Application
import android.content.pm.PackageManager
import android.speech.SpeechRecognizer
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.security.MessageDigest

data class ChatMessage(val role: String, val content: String)
data class PendingAction(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val path: String,
    val content: String,
    val command: String
)

class StudioViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = StudioDatabase.get(app).dao()
    private val repo = FileRepo(app)
    val voice = VoiceEngine(app) { log(it) }
    private var server: FileRepo.LocalServer? = null
    private var chatJob: Job? = null
    private var streamJob: Job? = null
    private var rootChecked = false
    private var rootAvailable = false

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
    val backgroundAssistant = mutableStateOf(false)
    val rootStatus = mutableStateOf("Not checked")

    val projects = mutableStateListOf<File>()
    val project = mutableStateOf<File>(File(repo.root, "default").apply { mkdirs() })
    val openTabs = mutableStateListOf<File>()
    val currentFile = mutableStateOf<File?>(null)
    val fileContent = mutableStateOf("")
    val findText = mutableStateOf("")
    val replaceText = mutableStateOf("")

    val terminal = mutableStateListOf<String>()
    val console = mutableStateListOf<String>()
    val serverRunning = mutableStateOf(false)
    val pendingAction = mutableStateOf<PendingAction?>(null)

    private val toolRegex = Regex("""<(file|edit_file|mkdir|delete_file|command|fetch)([^>]*)>([\s\S]*?)</\1>""")

    init {
        voice.init()
        refreshProjects()
        viewModelScope.launch {
            theme.value = runCatching { StudioTheme.valueOf(dao.memory("theme") ?: "OBSIDIAN") }.getOrDefault(StudioTheme.OBSIDIAN)
            apiKey.value = dao.memory("api_key") ?: ""
            model.value = dao.memory("model") ?: model.value
            endpoint.value = dao.memory("endpoint") ?: endpoint.value
            persona.value = dao.memory("persona") ?: persona.value
            language.value = dao.memory("language") ?: language.value
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
    }

    fun setTheme(value: StudioTheme) {
        theme.value = value
        viewModelScope.launch { dao.putMemory(MemoryEntity("theme", value.name)) }
    }

    suspend fun saveMemory(key: String, value: String) {
        dao.putMemory(MemoryEntity(key, value))
    }

    fun persistSettings() {
        viewModelScope.launch {
            saveMemory("api_key", apiKey.value)
            saveMemory("model", model.value)
            saveMemory("endpoint", endpoint.value)
            saveMemory("persona", persona.value)
            saveMemory("language", language.value)
            saveMemory("provider", provider.value.name)
            log("Settings saved")
        }
    }

    fun memorySnapshot(): String {
        var result = ""
        viewModelScope.launch {
            val name = dao.memory("user_name") ?: "Boss"
            val lang = dao.memory("language") ?: language.value
            result = "User: $name. Preferred language: $lang. Bypass Studio workspace."
        }
        return result.ifBlank { "User: Boss. Preferred language: bn. Bypass Studio workspace." }
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
        startServer()
        log("Project: ${file.name}")
    }

    fun createProject(name: String) {
        val dir = repo.createFolder(repo.root, name)
        refreshProjects()
        switchProject(dir)
    }

    fun createFile(name: String) {
        val f = repo.createFile(project.value, name)
        openFile(f)
        refreshProjects()
    }

    fun createFolder(name: String) {
        repo.createFolder(project.value, name)
        refreshProjects()
    }

    fun openFile(file: File) {
        currentFile.value = file
        fileContent.value = runCatching { repo.read(file) }.getOrDefault("")
        if (!openTabs.contains(file)) openTabs.add(file)
    }

    fun closeTab(file: File) {
        openTabs.remove(file)
        if (currentFile.value == file) currentFile.value = openTabs.lastOrNull()
        currentFile.value?.let { fileContent.value = repo.read(it) }
    }

    fun saveFile() {
        currentFile.value?.let { repo.write(it, fileContent.value); log("Saved ${it.name}") }
    }

    fun deleteFile(file: File) {
        repo.delete(file)
        openTabs.remove(file)
        if (currentFile.value == file) currentFile.value = null
        refreshProjects()
    }

    fun renameFile(file: File, newName: String) {
        val renamed = repo.rename(file, newName)
        val idx = openTabs.indexOf(file)
        if (idx >= 0) openTabs[idx] = renamed
        if (currentFile.value == file) currentFile.value = renamed
        refreshProjects()
    }

    fun replaceAll() {
        val from = findText.value
        if (from.isBlank()) return
        fileContent.value = fileContent.value.replace(from, replaceText.value)
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
        server = repo.LocalServer(project.value)
        server?.start { log(it) }
        serverRunning.value = true
    }

    fun stopServer() {
        server?.stop()
        server = null
        serverRunning.value = false
        log("Server stopped")
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

    fun sendPrompt() {
        val text = input.value.trim()
        if (text.isBlank() || streaming.value) return
        input.value = ""
        messages.add(ChatMessage("user", text))
        viewModelScope.launch { dao.insertChat(ChatEntity(session = session.value, role = "user", content = text)) }
        streamJob?.cancel()
        streaming.value = true
        streamBuffer.value = ""
        streamJob = viewModelScope.launch {
            try {
                val cfg = AiConfig(provider.value, apiKey.value, model.value, endpoint.value, budget.value)
                val system = "You are Bypass Studio, a professional Android AI coding IDE. " +
                    "User memory: ${memorySnapshot()}. Use tool tags <file path=\"...\">content</file>, " +
                    "<edit_file path=\"...\">content</edit_file>, <mkdir path=\"...\">name</mkdir>, " +
                    "<delete_file path=\"...\">path</delete_file>, <command>shell</command>, <fetch>query</fetch>. " +
                    "Mode: ${mode.value}. Reply in Bangla unless asked otherwise."
                val history = messages.map { it.role to it.content }
                val full = AiClient.stream(cfg, listOf("system" to system) + history) { chunk ->
                    streamBuffer.value += chunk
                }
                val actions = parseActions(full)
                if (actions.isNotEmpty()) {
                    pendingAction.value = actions.first()
                    if (autoApprove.value) approveAction()
                } else {
                    appendAssistant(full)
                }
            } catch (e: Exception) {
                appendAssistant("Error: ${e.message}")
            } finally {
                streaming.value = false
                streamBuffer.value = ""
            }
        }
    }

    private suspend fun appendAssistant(text: String) {
        messages.add(ChatMessage("assistant", text))
        dao.insertChat(ChatEntity(session = session.value, role = "assistant", content = text))
    }

    private fun parseActions(text: String): List<PendingAction> {
        return toolRegex.findAll(text).map { match ->
            val type = match.groupValues[1]
            val attrs = match.groupValues[2]
            val body = match.groupValues[3].trim()
            val pathAttr = Regex("""path="([^"]+)"""").find(attrs)?.groupValues?.get(1) ?: ""
            when (type) {
                "command" -> PendingAction("a${System.nanoTime()}", type, "Run shell command", body, "", "", body)
                "fetch" -> PendingAction("a${System.nanoTime()}", type, "Live web search", body, "", "", body)
                "mkdir" -> PendingAction("a${System.nanoTime()}", type, "Create folder", pathAttr, pathAttr, body, "")
                "delete_file" -> PendingAction("a${System.nanoTime()}", type, "Delete file", pathAttr, pathAttr, "", "")
                else -> {
                    val existing = if (pathAttr.isNotBlank()) runCatching { repo.read(File(project.value, pathAttr)) }.getOrDefault("") else ""
                    PendingAction(
                        "a${System.nanoTime()}", type,
                        if (type == "file") "Create file" else "Edit file",
                        "Old:\n${existing.take(500)}\n\nNew:\n${body.take(500)}",
                        pathAttr, body, ""
                    )
                }
            }
        }.toList()
    }

    fun denyAction() {
        pendingAction.value = null
        log("Action denied")
    }

    fun approveAction() {
        val action = pendingAction.value ?: return
        pendingAction.value = null
        viewModelScope.launch {
            try {
                when (action.type) {
                    "file", "edit_file" -> {
                        val target = File(project.value, action.path)
                        repo.write(target, action.content)
                        openFile(target)
                        appendAssistant("Applied ${action.type}: ${action.path}")
                    }
                    "mkdir" -> {
                        repo.createFolder(project.value, action.content.ifBlank { action.path })
                        refreshProjects()
                        appendAssistant("Created folder: ${action.path}")
                    }
                    "delete_file" -> {
                        repo.delete(File(project.value, action.path))
                        refreshProjects()
                        appendAssistant("Deleted: ${action.path}")
                    }
                    "command" -> {
                        val output = runCommand(action.command)
                        appendAssistant("Command output:\n$output")
                    }
                    "fetch" -> {
                        val result = AiClient.webSearch(action.command)
                        appendAssistant(result)
                    }
                }
            } catch (e: Exception) {
                appendAssistant("Action failed: ${e.message}")
            }
        }
    }

    fun checkRootOnce(force: Boolean = false) {
        if (rootChecked && !force) return
        rootChecked = true
        viewModelScope.launch {
            val output = withContext(Dispatchers.IO) {
                runCommand("id -u", capture = true)
            }
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

    fun speak(text: String) = voice.speak(text, language.value, persona.value, speed.value)

    fun startVoiceInput(recognizer: SpeechRecognizer) {
        voice.startListening(
            recognizer,
            onResult = { text -> input.value = text },
            onError = { log(it) }
        )
    }

    fun exportAudio(text: String, out: File) {
        voice.synthesizeToFile(text, out) { result ->
            if (result == 0) log("MP3/WAV exported: ${out.name}") else log("Audio export failed: $result")
        }
    }

    fun exportVideo(text: String, out: File) {
        viewModelScope.launch(Dispatchers.IO) {
            BroadcastExporter.exportSlides(getApplication(), text, out) { log(it) }
        }
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
