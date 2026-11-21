package com.bypass.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EditorScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val tabScroll = rememberScrollState()
    val lineScroll = rememberScrollState()
    val codeScroll = rememberScrollState()
    val lines = vm.fileContent.value.lines().size.coerceAtMost(2000)

    Column(modifier = Modifier.fillMaxSize().background(p.bg)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(tabScroll).padding(horizontal = 8.dp, vertical = 6.dp)) {
            vm.openTabs.forEach { file ->
                Surface(shape = RoundedCornerShape(9.dp),
                    color = if (file == vm.currentFile.value) p.accent else p.surface2,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.padding(end = 5.dp).clickable { vm.openFile(file) }) {
                    Text(file.name, color = if (file == vm.currentFile.value) androidx.compose.ui.graphics.Color.Black else p.text,
                        fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = vm.findText.value, onValueChange = { vm.findText.value = it },
                placeholder = { Text("Find", color = p.muted, fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(5.dp))
            OutlinedTextField(value = vm.replaceText.value, onValueChange = { vm.replaceText.value = it },
                placeholder = { Text("Replace", color = p.muted, fontSize = 11.sp) },
                modifier = Modifier.weight(1f).height(48.dp), shape = RoundedCornerShape(10.dp))
            Spacer(Modifier.width(5.dp))
            GhostButton("Apply") { vm.replaceAll() }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Column(modifier = Modifier.verticalScroll(lineScroll).width(38.dp)) {
                repeat(lines) { index ->
                    Text("${index + 1}", color = p.muted, fontFamily = monoFont, fontSize = 11.sp, lineHeight = 20.sp)
                }
            }
            OutlinedTextField(
                value = vm.fileContent.value,
                onValueChange = { vm.fileContent.value = it },
                modifier = Modifier.weight(1f).fillMaxSize().verticalScroll(codeScroll),
                shape = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = monoFont, fontSize = 11.sp, lineHeight = 20.sp, color = p.text
                )
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(vm.currentFile.value?.name ?: "No file open", color = p.muted, fontSize = 10.sp, modifier = Modifier.weight(1f))
            GhostButton("Save") { vm.saveFile() }
        }
    }
}
