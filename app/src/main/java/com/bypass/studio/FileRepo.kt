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
        val parsed = GitHubZipResolver.parseRepo(url)
        val client = OkHttpClient()
        onProgress("Detecting default branch for ${parsed.owner}/${parsed.repo} ...")
        val metaRequest = Request.Builder()
            .url(GitHubZipResolver.defaultBranchApiUrl(parsed))
            .header("User-Agent", "BypassStudio/2.1")
            .header("Accept", "application/vnd.github+json")
            .build()
        val branch = client.newCall(metaRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub metadata HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            GitHubZipResolver.parseDefaultBranch(body)
        }
        val zipUrl = GitHubZipResolver.archiveUrl(parsed, branch)
        onProgress("Downloading $zipUrl")
        val zipRequest = Request.Builder()
            .url(zipUrl)
            .header("User-Agent", "BypassStudio/2.1")
            .build()
        val tmp = File(context.cacheDir, "clone-${System.currentTimeMillis()}.zip")
        client.newCall(zipRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException("GitHub archive HTTP ${response.code}")
            response.body?.byteStream()?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("Empty archive response")
        }
        val dest = File(root, "${parsed.repo}-${System.currentTimeMillis()}")
        dest.mkdirs()
        onProgress("Extracting archive (branch $branch) ...")
        ZipInputStream(tmp.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val stripped = GitHubZipResolver.safeRelativeEntry(entry.name)
                if (!stripped.isNullOrBlank()) {
                    val out = File(dest, stripped)
                    val canonical = out.canonicalPath
                    val rootCanonical = dest.canonicalPath
                    if (canonical.startsWith(rootCanonical)) {
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        tmp.delete()
        onProgress("Cloned ${parsed.owner}/${parsed.repo} to ${dest.name}")
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
