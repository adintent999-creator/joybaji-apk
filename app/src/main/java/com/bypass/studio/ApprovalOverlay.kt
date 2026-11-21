package com.bypass.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ApprovalOverlay(vm: StudioViewModel) {
    val action = vm.pendingAction.value ?: return
    val p = paletteFor(vm.theme.value)
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.74f)).clickable { },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = p.surface,
            border = BorderStroke(1.dp, p.accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(18.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("TOOL APPROVAL", color = p.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(action.title, color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Surface(shape = RoundedCornerShape(10.dp), color = p.bg) {
                    Text(
                        action.detail.take(1200),
                        color = p.muted,
                        fontFamily = monoFont,
                        fontSize = 10.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-approve this session", color = p.text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Switch(checked = vm.autoApprove.value, onCheckedChange = { vm.autoApprove.value = it })
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    GhostButton("Deny", onClick = vm::denyAction)
                    Spacer(Modifier.width(8.dp))
                    AccentButton("Approve & run", modifier = Modifier.weight(1f), onClick = vm::approveAction)
                }
            }
        }
    }
}
