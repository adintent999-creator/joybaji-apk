package com.bypass.studio

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.SpeechRecognizer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

fun copyText(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("bypass-studio", text))
}

@Composable
fun HomeScreen(vm: StudioViewModel, recognizer: SpeechRecognizer) {
    val p = paletteFor(vm.theme.value)
    val context = LocalContext.current
    var sessionMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    var modeMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(p.bg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = p.surface2,
                border = BorderStroke(1.dp, p.border),
                modifier = Modifier.clickable { sessionMenu = true }
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.session.value, color = p.text, fontSize = 11.sp, maxLines = 1)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.muted, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = sessionMenu, onDismissRequest = { sessionMenu = false }) {
                vm.sessions.forEach { id ->
                    DropdownMenuItem(text = { Text(id) }, onClick = { vm.switchSession(id); sessionMenu = false })
                }
            }
            Spacer(Modifier.width(6.dp))
            StatusPill("READY", p.green)
            Spacer(Modifier.weight(1f))
            ToolIcon(Icons.Filled.Add, "New session", { vm.newSession() }, p.accent)
            ToolIcon(Icons.Filled.ContentCopy, "Copy chat", {
                copyText(context, vm.messages.joinToString("\n") { "${it.role}: ${it.content}" })
            }, p.muted)
            ToolIcon(Icons.Filled.Delete, "Clear chat", { vm.clearChat() }, p.muted)
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(vm.messages) { msg -> MessageBubble(msg, vm, p) }
            if (vm.streaming.value) {
                item {
                    Surface(shape = RoundedCornerShape(14.dp), color = p.surface, border = BorderStroke(1.dp, p.border)) {
                        Text(
                            "Generating...\n${vm.streamBuffer.value.takeLast(600)}",
                            color = p.muted, fontSize = 13.sp, lineHeight = 22.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = vm.input.value,
                onValueChange = { vm.input.value = it },
                modifier = Modifier.weight(1f).height(56.dp),
                placeholder = { Text("Ask Bypass AI...", color = p.muted, fontSize = 13.sp) },
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { vm.startVoiceInput(recognizer) }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Filled.Mic, contentDescription = "Mic", tint = p.accent, modifier = Modifier.size(24.dp))
            }
            IconButton(
                onClick = { if (vm.streaming.value) vm.streamBuffer.value = "" else vm.sendPrompt() },
                modifier = Modifier.size(48.dp).background(p.accent, RoundedCornerShape(14.dp))
            ) {
                Icon(
                    if (vm.streaming.value) Icons.Filled.Stop else Icons.Filled.Send,
                    contentDescription = "Send",
                    tint = androidx.compose.ui.graphics.Color.Black,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(9.dp), color = p.surface2, border = BorderStroke(1.dp, p.border),
                modifier = Modifier.clickable { modelMenu = true }) {
                Text("${vm.provider.value.label}: ${vm.model.value}", color = p.text, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), maxLines = 1)
            }
            DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                listOf(
                    "google/gemini-2.0-flash-001",
                    "google/gemini-2.5-flash",
                    "meta-llama/llama-3.3-70b-instruct:free",
                    "deepseek/deepseek-chat:free"
                ).forEach { m ->
                    DropdownMenuItem(text = { Text(m, fontSize = 11.sp) }, onClick = { vm.model.value = m; modelMenu = false })
                }
            }
            Spacer(Modifier.width(6.dp))
            Surface(shape = RoundedCornerShape(9.dp), color = p.surface2, border = BorderStroke(1.dp, p.border),
                modifier = Modifier.clickable { modeMenu = true }) {
                Text(vm.mode.value, color = p.accent, fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp))
            }
            DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                listOf("Chat", "Builder", "Agent", "Pipeline", "Broadcast").forEach { m ->
                    DropdownMenuItem(text = { Text(m) }, onClick = { vm.mode.value = m; modeMenu = false })
                }
            }
            Spacer(Modifier.weight(1f))
            Text("${vm.budget.value / 1000}k", color = p.muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, vm: StudioViewModel, p: Palette) {
    val context = LocalContext.current
    val isUser = msg.role == "user"
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isUser) p.surface2 else p.surface,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(11.dp)) {
            Text(
                if (isUser) "You" else "Bypass AI",
                color = if (isUser) p.accent else p.green,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(5.dp))
            val parts = msg.content.split("```")
            parts.forEachIndexed { index, part ->
                if (index % 2 == 1) {
                    Surface(shape = RoundedCornerShape(10.dp), color = p.bg, border = BorderStroke(1.dp, p.border)) {
                        Column(Modifier.padding(9.dp)) {
                            Text(part.trim(), fontFamily = monoFont, color = p.text, fontSize = 11.sp, lineHeight = 18.sp)
                            Row {
                                ToolIcon(Icons.Filled.ContentCopy, "Copy code", { copyText(context, part.trim()) }, p.muted)
                                Text("Insert to Editor", color = p.accent, fontSize = 10.sp,
                                    modifier = Modifier.clickable {
                                        val target = vm.currentFile.value ?: File(vm.project.value, "ai_insert.txt")
                                        vm.currentFile.value = target
                                        vm.fileContent.value += "\n" + part.trim()
                                        vm.tab.value = 2
                                    }.padding(vertical = 8.dp))
                            }
                        }
                    }
                } else if (part.isNotBlank()) {
                    Text(
                        part.trim(),
                        color = p.text,
                        fontSize = 13.sp,
                        lineHeight = 22.sp,
                        letterSpacing = 0.sp
                    )
                }
            }
            Row {
                ToolIcon(Icons.Filled.ContentCopy, "Copy", { copyText(context, msg.content) }, p.muted)
                if (!isUser) ToolIcon(Icons.Filled.VolumeUp, "Speak", { vm.speak(msg.content) }, p.muted)
            }
        }
    }
}
