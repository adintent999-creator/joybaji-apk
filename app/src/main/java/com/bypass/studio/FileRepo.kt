package com.bypass.studio

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread

class FileRepo(private val context: Context) {
    val root = File(context.filesDir, "projects").apply { mkdirs() }

    fun projects(): List<File> = root.listFiles { file -> file.isDirectory }?.toList().orEmpty()

    fun list(dir: File): List<File> =
        if (dir.exists() && dir.isDirectory) dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })).orEmpty()
        else emptyList()

    fun read(file: File): String = if (file.exists()) file.readText() else ""

    fun write(file: File, text: String) {
        file.parentFile?.mkdirs()
        file.writeText(text)
    }

    fun createFile(dir: File, name: String): File {
        val f = File(dir, name)
        write(f, "")
        return f
    }

    fun createFolder(dir: File, name: String): File = File(dir, name).apply { mkdirs() }

    fun rename(file: File, newName: String): File {
        val target = File(file.parentFile, newName)
        file.renameTo(target)
        return target
    }

    fun delete(file: File) {
        if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    suspend fun cloneGitHub(url: String, onProgress: (String) -> Unit): File = withContext(Dispatchers.IO) {
        val clean = url.trim().trimEnd('/')
        val rest = clean.removePrefix("https://github.com/").removePrefix("http://github.com/").removePrefix("git@github.com:")
        val parts = rest.split("/")
        require(parts.size >= 2) { "Invalid GitHub URL. Use https://github.com/owner/repo" }
        val owner = parts[0]
        val repo = parts[1].removeSuffix(".git")
        onProgress("Fetching $owner/$repo ...")
        val client = OkHttpClient()
        val req = Request.Builder()
            .url("https://api.github.com/repos/$owner/$repo/zipball")
            .header("User-Agent", "BypassStudio/1.0")
            .build()
        val tmp = File(context.cacheDir, "clone-${System.currentTimeMillis()}.zip")
        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Empty response")
        }
        val dest = File(root, "${repo}-${System.currentTimeMillis()}")
        dest.mkdirs()
        onProgress("Extracting ...")
        ZipInputStream(FileInputStream(tmp)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val raw = entry.name.replace('\\', '/')
                val stripped = if (raw.contains('/')) raw.substringAfter('/') else ""
                val safe = stripped.split('/').none { it == ".." }
                if (safe && stripped.isNotEmpty()) {
                    val out = File(dest, stripped)
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        tmp.delete()
        onProgress("Cloned to ${dest.name}")
        dest
    }

    inner class LocalServer(private val webRoot: File, val port: Int = 8080) {
        private var socket: ServerSocket? = null
        @Volatile var running = false
            private set

        fun isRunning(): Boolean = running

        fun start(onLog: (String) -> Unit) {
            if (running) return
            thread(name = "bypass-http") {
                try {
                    socket = ServerSocket(port)
                    running = true
                    onLog("localhost:$port ready")
                    while (running) {
                        val client = socket?.accept() ?: break
                        thread(name = "bypass-req") { handle(client, onLog) }
                    }
                } catch (e: Exception) {
                    onLog("Server stopped: ${e.message}")
                    running = false
                }
            }
        }

        fun stop() {
            running = false
            try { socket?.close() } catch (_: Exception) {}
            socket = null
        }

        private fun handle(client: Socket, onLog: (String) -> Unit) {
            try {
                val reader = client.getInputStream().bufferedReader()
                val first = reader.readLine() ?: return
                val rawPath = first.split(" ").getOrNull(1) ?: "/"
                val cleanPath = rawPath.substringBefore('?').trimStart('/')
                var file = File(webRoot, cleanPath)
                if (!file.exists() || file.isDirectory) {
                    file = File(file, "index.html")
                }
                val canonical = file.canonicalPath
                val rootCanonical = webRoot.canonicalPath
                if (!canonical.startsWith(rootCanonical) || !file.exists()) {
                    val body = "404 Not Found".toByteArray()
                    client.getOutputStream().use { out ->
                        out.write("HTTP/1.1 404 Not Found\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(body)
                    }
                    onLog("404 $rawPath")
                    return
                }
                val bytes = file.readBytes()
                val type = when (file.extension.lowercase()) {
                    "html", "htm" -> "text/html; charset=utf-8"
                    "css" -> "text/css; charset=utf-8"
                    "js" -> "text/javascript; charset=utf-8"
                    "json" -> "application/json; charset=utf-8"
                    "svg" -> "image/svg+xml"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    else -> "application/octet-stream"
                }
                client.getOutputStream().use { out ->
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n".toByteArray())
                    out.write(bytes)
                }
                onLog("200 $rawPath")
            } catch (e: Exception) {
                onLog("Request error: ${e.message}")
            } finally {
                try { client.close() } catch (_: Exception) {}
            }
        }
    }
}
