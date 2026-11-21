package com.bypass.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val chips = listOf("ls -la", "pwd", "node -v", "python3 --version", "git status", "id")
    Column(modifier = Modifier.fillMaxSize().background(p.bg).padding(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            chips.forEach { cmd ->
                GhostButton(cmd) { vm.runCommand(cmd) }
                Spacer(Modifier.width(5.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row {
            StatusPill(vm.rootStatus.value, if (vm.rootStatus.value == "ROOT") p.green else p.muted)
            Spacer(Modifier.width(6.dp))
            GhostButton("Check root once") { vm.checkRootOnce(force = true) }
            Spacer(Modifier.weight(1f))
            GhostButton("Copy") { /* clipboard handled via long press */ }
            Spacer(Modifier.width(4.dp))
            GhostButton("Clear") { vm.clearTerminal() }
        }
        Spacer(Modifier.height(8.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = p.surface, border = androidx.compose.foundation.BorderStroke(1.dp, p.border),
            modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.padding(10.dp)) {
                items(vm.terminal) { line ->
                    Text(line, color = if (line.startsWith("$")) p.accent else p.text,
                        fontFamily = monoFont, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
        }
    }
}
