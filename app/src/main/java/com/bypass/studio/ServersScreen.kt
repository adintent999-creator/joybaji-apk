package com.bypass.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ServersScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).verticalScroll(scroll).padding(12.dp)
    ) {
        SectionTitle("Localhost servers", trailing = { GhostButton("Back") { vm.tab.value = 5 } })
        Spacer(Modifier.height(8.dp))
        Text("Active servers inside Bypass Studio. Preview opens in the WebView tab.", color = p.muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.serverPortInput.value,
                onValueChange = { vm.serverPortInput.value = it },
                placeholder = { Text("Port", color = p.muted, fontSize = 11.sp) },
                modifier = Modifier.weight(0.35f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.width(6.dp))
            OutlinedTextField(
                value = vm.serverPathInput.value,
                onValueChange = { vm.serverPathInput.value = it },
                placeholder = { Text("Project folder", color = p.muted, fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row {
            AccentButton("Start", modifier = Modifier.weight(1f)) { vm.startManagedServer() }
            Spacer(Modifier.width(6.dp))
            GhostButton("Refresh") { vm.refreshManagedServers() }
            Spacer(Modifier.width(6.dp))
            GhostButton("Stop all") { vm.stopAllManagedServers() }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.servers.isEmpty()) {
            Text("No servers registered.", color = p.muted, fontSize = 12.sp)
        }
        vm.servers.forEach { server ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = p.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(server.name, color = p.text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        StatusPill(if (server.running) "RUNNING" else "STOPPED", if (server.running) p.green else p.muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(server.url, color = p.accent, fontSize = 10.sp)
                    Spacer(Modifier.height(6.dp))
                    Row {
                        GhostButton("Preview") {
                            vm.previewUrl.value = server.url
                            vm.tab.value = 4
                        }
                        Spacer(Modifier.width(6.dp))
                        GhostButton("Stop") { vm.stopManagedServer(server.port) }
                    }
                }
            }
        }
    }
}
