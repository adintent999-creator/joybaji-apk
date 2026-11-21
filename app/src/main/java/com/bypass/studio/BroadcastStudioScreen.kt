package com.bypass.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BroadcastStudioScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).verticalScroll(scroll).padding(12.dp)
    ) {
        SectionTitle("Broadcast Studio", trailing = { GhostButton("Back") { vm.tab.value = 5 } })
        Spacer(Modifier.height(8.dp))
        Text("Multi-voice Bengali video with native MediaCodec/MediaMuxer export.", color = p.muted, fontSize = 11.sp)
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = vm.broadcastScript.value,
            onValueChange = { vm.broadcastScript.value = it },
            label = { Text("Script", color = p.muted) },
            minLines = 6,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(10.dp))

        Text("Voices", color = p.text, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            vm.availablePersonas.forEach { persona ->
                val active = vm.broadcastPersonas.contains(persona)
                Surface(
                    shape = RoundedCornerShape(9.dp),
                    color = if (active) p.accent else p.surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                    modifier = Modifier.padding(end = 4.dp).clickable { vm.toggleBroadcastPersona(persona) }
                ) {
                    Text(
                        persona,
                        color = if (active) androidx.compose.ui.graphics.Color.Black else p.text,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Text("Language", color = p.text, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            listOf("bn" to "Bangla", "en" to "English", "hi" to "Hindi").forEach { (code, label) ->
                GhostButton(if (vm.broadcastLang.value == code) "$label*" else label) {
                    vm.broadcastLang.value = code
                }
                Spacer(Modifier.width(4.dp))
            }
        }
        Spacer(Modifier.height(8.dp))

        Text("Format", color = p.text, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            GhostButton(if (vm.broadcastRatio.value == "9:16") "9:16*" else "9:16") {
                vm.broadcastRatio.value = "9:16"
            }
            Spacer(Modifier.width(4.dp))
            GhostButton(if (vm.broadcastRatio.value == "16:9") "16:9*" else "16:9") {
                vm.broadcastRatio.value = "16:9"
            }
        }
        Spacer(Modifier.height(8.dp))

        Text("Mode", color = p.text, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Row {
            GhostButton(if (!vm.broadcastAllVoices.value) "Dialogue*" else "Dialogue") {
                vm.broadcastAllVoices.value = false
            }
            Spacer(Modifier.width(4.dp))
            GhostButton(if (vm.broadcastAllVoices.value) "All voices*" else "All voices") {
                vm.broadcastAllVoices.value = true
            }
        }
        Spacer(Modifier.height(12.dp))

        AccentButton("Export broadcast", modifier = Modifier.fillMaxWidth()) { vm.exportBroadcast() }
        Spacer(Modifier.height(8.dp))
        Text("Progress: ${vm.broadcastProgress.value}%", color = p.accent, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(vm.broadcastLog.value.takeLast(900), color = p.muted, fontSize = 10.sp)
        Spacer(Modifier.height(10.dp))
        Row {
            GhostButton("Save to Downloads") { vm.saveBroadcastToDownloads() }
            Spacer(Modifier.width(6.dp))
            GhostButton("Share") { vm.shareBroadcast() }
        }
    }
}
