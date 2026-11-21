package com.bypass.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun AccentButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.Black
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
        )
    }
}

@Composable
fun SectionTitle(text: String, trailing: @Composable (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun ToolIcon(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color) {
    IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun TopHeader(vm: StudioViewModel) {
    val p = paletteFor(vm.theme.value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(p.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, p.border))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = p.accent) {
            Text(
                "B", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 17.sp,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text("Bypass Studio", color = p.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        StatusPill("$ SHELL", p.muted)
        Spacer(Modifier.width(5.dp))
        StatusPill(":8080", if (vm.serverRunning.value) p.green else p.muted)
        IconButton(onClick = { vm.tab.value = 4 }) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.PlayArrow,
                contentDescription = "Preview",
                tint = p.accent
            )
        }
        IconButton(onClick = { vm.tab.value = 5 }) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.Filled.Settings,
                contentDescription = "Settings",
                tint = p.muted
            )
        }
    }
}
