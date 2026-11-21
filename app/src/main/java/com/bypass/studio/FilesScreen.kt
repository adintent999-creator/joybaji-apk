package com.bypass.studio

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
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

@Composable
fun FilesScreen(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val context = LocalContext.current
    var projectMenu by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var cloneUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(p.bg).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = p.surface2, border = BorderStroke(1.dp, p.border),
                modifier = Modifier.clickable { projectMenu = true }) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vm.project.value.name, color = p.text, fontSize = 12.sp, maxLines = 1)
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = p.muted)
                }
            }
            DropdownMenu(expanded = projectMenu, onDismissRequest = { projectMenu = false }) {
                vm.projects.forEach { dir ->
                    DropdownMenuItem(text = { Text(dir.name) }, onClick = { vm.switchProject(dir); projectMenu = false })
                }
            }
            Spacer(Modifier.width(6.dp))
            GhostButton("New project") {
                if (newName.isNotBlank()) { vm.createProject(newName.trim()); newName = "" }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = cloneUrl,
                onValueChange = { cloneUrl = it },
                placeholder = { Text("https://github.com/owner/repo", color = p.muted, fontSize = 12.sp) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            AccentButton("Clone", onClick = { if (cloneUrl.isNotBlank()) { vm.cloneRepo(cloneUrl.trim()); cloneUrl = "" } })
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = { Text("new file or folder", color = p.muted, fontSize = 12.sp) },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.width(6.dp))
            GhostButton("+ File") { if (newName.isNotBlank()) { vm.createFile(newName.trim()); newName = "" } }
            Spacer(Modifier.width(4.dp))
            GhostButton("+ Dir") { if (newName.isNotBlank()) { vm.createFolder(newName.trim()); newName = "" } }
        }
        Spacer(Modifier.height(10.dp))
        Text("PROJECT FILES", color = p.muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(repoFiles(vm)) { file ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (file == vm.currentFile.value) p.surface2 else p.surface,
                    border = BorderStroke(1.dp, p.border),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { if (file.isFile) vm.openFile(file) }
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (file.isDirectory) "▸" else "·", color = p.accent, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(file.name, color = p.text, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        androidx.compose.material3.IconButton(onClick = { copyText(context, file.absolutePath) }) {
                            Icon(Icons.Filled.ContentCopy, null, tint = p.muted)
                        }
                        androidx.compose.material3.IconButton(onClick = { vm.deleteFile(file) }) {
                            Icon(Icons.Filled.Delete, null, tint = p.muted)
                        }
                    }
                }
            }
        }
    }
}

private fun repoFiles(vm: StudioViewModel): List<File> = vm.project.value.listFiles()?.toList().orEmpty()
