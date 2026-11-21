package com.bypass.studio

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

@Composable
fun PreviewScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val context = LocalContext.current
    var url by remember { mutableStateOf("http://127.0.0.1:8080/") }
    var lastUrl by remember { mutableStateOf("") }
    var showConsole by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(if (vm.serverRunning.value) "SERVER LIVE" else "SERVER OFF", if (vm.serverRunning.value) p.green else p.muted)
            Spacer(Modifier.weight(1f))
            GhostButton(if (vm.serverRunning.value) "Stop" else "Start :8080") {
                if (vm.serverRunning.value) vm.stopServer() else vm.startServer()
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(11.dp)
            )
            Spacer(Modifier.width(5.dp))
            GhostButton("Reload") { lastUrl = "" }
        }
        Spacer(Modifier.height(7.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = p.surface,
            border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        webViewClient = WebViewClient()
                    }
                },
                update = { web ->
                    if (url != lastUrl) {
                        web.loadUrl(url)
                        lastUrl = url
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GhostButton(if (showConsole) "Hide console" else "Show console") { showConsole = !showConsole }
            Spacer(Modifier.width(5.dp))
            GhostButton("Open external") {
                ContextCompat.startActivity(context, Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)), null)
            }
            Spacer(Modifier.weight(1f))
            Text("${vm.console.size} logs", color = p.muted, fontSize = 10.sp)
        }
        if (showConsole) {
            Spacer(Modifier.height(5.dp))
            Surface(shape = RoundedCornerShape(10.dp), color = p.surface, border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                modifier = Modifier.height(130.dp).fillMaxWidth()) {
                LazyColumn(Modifier.padding(8.dp)) {
                    items(vm.console.takeLast(60)) { line ->
                        Text(line, color = p.muted, fontFamily = monoFont, fontSize = 9.sp, lineHeight = 15.sp)
                    }
                }
            }
            GhostButton("Send last error to AI") {
                val err = vm.console.lastOrNull { it.contains("error", true) || it.startsWith("404") } ?: vm.console.lastOrNull() ?: "Preview error"
                vm.input.value = "Fix this preview error:\n$err"
                vm.tab.value = 0
            }
        }
    }
}
