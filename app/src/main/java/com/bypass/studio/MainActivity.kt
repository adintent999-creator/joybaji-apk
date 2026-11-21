package com.bypass.studio

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider

class MainActivity : ComponentActivity() {
    private lateinit var vm: StudioViewModel
    private lateinit var recognizer: SpeechRecognizer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vm = ViewModelProvider(this)[StudioViewModel::class.java]
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val permissions = mutableListOf<String>(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.SYSTEM_ALERT_WINDOW)
        }
        ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 77)

        setContent {
            StudioTheme(vm.theme.value) {
                LaunchedEffect(Unit) { vm.checkRootOnce() }
                LaunchedEffect(vm.capsule.value) { vm.syncOverlay(this@MainActivity) }
                LaunchedEffect(vm.backgroundAssistant.value) { vm.syncBackground(this@MainActivity) }
                Scaffold(
                    topBar = { TopHeader(vm) },
                    bottomBar = { BottomNav(vm) },
                    containerColor = paletteFor(vm.theme.value).bg
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize()) {
                        when (vm.tab.value) {
                            0 -> HomeScreen(vm, recognizer)
                            1 -> FilesScreen(vm)
                            2 -> EditorScreen(vm)
                            3 -> TerminalScreen(vm)
                            4 -> PreviewScreen(vm)
                            5 -> SettingsScreen(vm)
                            6 -> MemoryScreen(vm)
                            7 -> BroadcastStudioScreen(vm)
                            8 -> ServersScreen(vm)
                            else -> HomeScreen(vm, recognizer)
                        }
                        ApprovalOverlay(vm)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.syncOverlay(this)
        vm.syncBackground(this)
    }

    override fun onDestroy() {
        runCatching { recognizer.destroy() }
        if (!isChangingConfigurations) vm.voice.shutdown()
        vm.stopAllManagedServers()
        super.onDestroy()
    }
}

@Composable
private fun BottomNav(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    val items = listOf(
        NavItem("Home", Icons.Filled.Chat),
        NavItem("Files", Icons.Filled.Folder),
        NavItem("Editor", Icons.Filled.Code),
        NavItem("Terminal", Icons.Filled.Terminal),
        NavItem("Preview", Icons.Filled.Public)
    )
    NavigationBar(containerColor = p.surface) {
        items.forEachIndexed { index, item ->
            NavigationBarItem(
                selected = vm.tab.value == index,
                onClick = { vm.tab.value = index },
                icon = { androidx.compose.material3.Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, fontSize = 10.sp) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = p.accent,
                    selectedTextColor = p.accent,
                    indicatorColor = p.surface2,
                    unselectedIconColor = p.muted,
                    unselectedTextColor = p.muted
                )
            )
        }
    }
}

private data class NavItem(val label: String, val icon: ImageVector)
