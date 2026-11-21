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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MemoryScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().background(p.bg).verticalScroll(scroll).padding(12.dp)
    ) {
        SectionTitle("Memory", trailing = { GhostButton("Back") { vm.tab.value = 5 } })
        Spacer(Modifier.height(10.dp))
        Text(
            "Persistent facts injected into every AI request. Auto-saved from <memory_save> markers.",
            color = p.muted, fontSize = 11.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = vm.memoryQuery.value,
                onValueChange = { vm.memoryQuery.value = it },
                placeholder = { Text("Search memory", color = p.muted, fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(Modifier.width(6.dp))
            GhostButton("Search") { vm.refreshMemory(vm.memoryQuery.value) }
            Spacer(Modifier.width(6.dp))
            GhostButton("All") {
                vm.memoryQuery.value = ""
                vm.refreshMemory("")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            GhostButton("Refresh") { vm.refreshMemory(vm.memoryQuery.value) }
            Spacer(Modifier.width(6.dp))
            GhostButton("Clear all") { vm.clearMemory() }
        }
        Spacer(Modifier.height(12.dp))
        if (vm.memoryRows.isEmpty()) {
            Text("No memories stored yet.", color = p.muted, fontSize = 12.sp)
        }
        vm.memoryRows.forEach { mem ->
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = p.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(mem.key, color = p.accent, fontSize = 10.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(mem.value, color = p.text, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(formatMemoryTime(mem.ts), color = p.muted, fontSize = 9.sp, modifier = Modifier.weight(1f))
                        GhostButton("Delete") { vm.deleteMemory(mem.key) }
                    }
                }
            }
        }
    }
}

private fun formatMemoryTime(ts: Long): String = try {
    SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))
} catch (_: Exception) {
    ts.toString()
}
