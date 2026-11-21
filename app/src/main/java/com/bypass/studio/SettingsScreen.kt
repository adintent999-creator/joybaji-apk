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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    var providerMenu by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().background(p.bg).padding(12.dp)) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                SectionTitle("Theme (live switch)")
                Spacer(Modifier.height(6.dp))
                Row {
                    StudioTheme.values().forEach { t ->
                        GhostButton(t.label.replace("Midnight ", "").replace(" Pro", "")) { vm.setTheme(t) }
                        Spacer(Modifier.width(5.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            item {
                SectionTitle("AI provider")
                Spacer(Modifier.height(6.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = p.surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxWidth().clickable { providerMenu = true }) {
                    Text(vm.provider.value.label, color = p.text, fontSize = 13.sp, modifier = Modifier.padding(11.dp))
                }
                DropdownMenu(expanded = providerMenu, onDismissRequest = { providerMenu = false }) {
                    Provider.values().forEach { pr ->
                        DropdownMenuItem(text = { Text(pr.label) }, onClick = { vm.provider.value = pr; providerMenu = false })
                    }
                }
                Spacer(Modifier.height(7.dp))
                OutlinedTextField(value = vm.apiKey.value, onValueChange = { vm.apiKey.value = it },
                    placeholder = { Text("API key (OpenRouter / Gemini)", color = p.muted, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(11.dp))
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = vm.model.value, onValueChange = { vm.model.value = it },
                    label = { Text("Model") }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(11.dp))
                if (vm.provider.value != Provider.GEMINI) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = vm.endpoint.value, onValueChange = { vm.endpoint.value = it },
                        label = { Text("Endpoint") }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(11.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("Reasoning budget", color = p.muted, fontSize = 11.sp)
                Row {
                    listOf(12000, 6000, 2000, 0).forEach { b ->
                        GhostButton("${b / 1000}k") { vm.budget.value = b }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
            }
            item {
                SectionTitle("Voice & memory")
                Spacer(Modifier.height(8.dp))
                Row {
                    listOf("bn", "hi", "en").forEach { lang ->
                        GhostButton(lang) { vm.language.value = lang }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row {
                    listOf("Emma", "Swara", "Anaya", "Aarav", "Rohan").forEach { voice ->
                        GhostButton(voice) { vm.persona.value = voice }
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("Speed ${vm.speed.value}", color = p.text, fontSize = 12.sp)
                Slider(value = vm.speed.value, onValueChange = { vm.speed.value = it },
                    valueRange = 1.0f..2.0f, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
            }
            item {
                SectionTitle("Advanced")
                ToggleRow("Auto-approve this session", vm.autoApprove.value) { vm.autoApprove.value = it }
                ToggleRow("Dynamic Island capsule", vm.capsule.value) { vm.capsule.value = it }
                ToggleRow("Background assistant", vm.backgroundAssistant.value) { vm.backgroundAssistant.value = it }
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = p.surface, border = androidx.compose.foundation.BorderStroke(1.dp, p.border)) {
                    Column(Modifier.padding(11.dp)) {
                        Text("APK SIGNATURE SHA-256", color = p.muted, fontSize = 10.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(vm.apkSignatureSha256(), color = p.accent, fontFamily = monoFont, fontSize = 9.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                GhostButton("Save settings") { vm.persistSettings() }
                Spacer(Modifier.height(14.dp))
            }
            item {
                SectionTitle("Voice diagnostics")
                Spacer(Modifier.height(5.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = p.surface, border = androidx.compose.foundation.BorderStroke(1.dp, p.border)) {
                    LazyColumn(Modifier.height(140.dp).padding(9.dp)) {
                        items(vm.console.takeLast(80)) { line ->
                            Text(line, color = p.muted, fontFamily = monoFont, fontSize = 9.sp, lineHeight = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
