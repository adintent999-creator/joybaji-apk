package com.bypass.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    val actions = vm.pendingActions
    if (actions.isEmpty()) return
    val p = paletteFor(vm.theme.value)

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.76f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = p.surface,
            border = BorderStroke(1.dp, p.accent.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    "DIFF QUEUE  /  ${actions.size} change${if (actions.size == 1) "" else "s"}",
                    color = p.accent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text("Review every file before Bypass AI touches the project.", color = p.muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Auto-approve this session", color = p.text, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    Switch(checked = vm.autoApprove.value, onCheckedChange = { vm.autoApprove.value = it })
                }
                Spacer(Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
                ) {
                    items(actions, key = { it.id }) { action ->
                        ActionRow(vm, action)
                    }
                }

                if (vm.actionStatus.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        vm.actionStatus.takeLast(4).joinToString("\n"),
                        color = p.muted,
                        fontFamily = monoFont,
                        fontSize = 9.sp,
                        lineHeight = 14.sp
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    GhostButton("Reject all", modifier = Modifier.weight(1f)) { vm.rejectAll() }
                    Spacer(Modifier.width(5.dp))
                    GhostButton("Keep all", modifier = Modifier.weight(1f)) { vm.keepAll() }
                    Spacer(Modifier.width(5.dp))
                    AccentButton("Approve all", modifier = Modifier.weight(1f)) { vm.approveAll() }
                }
                Spacer(Modifier.height(6.dp))
                GhostButton("Hide queue", modifier = Modifier.fillMaxWidth()) { vm.dismissQueue() }
            }
        }
    }
}

@Composable
private fun ActionRow(vm: StudioViewModel, action: PendingAction) {
    val p = paletteFor(vm.theme.value)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = p.bg,
        border = BorderStroke(1.dp, p.border),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    action.title,
                    color = p.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                StatusPill(
                    action.status,
                    when (action.status) {
                        "DONE", "KEPT" -> p.green
                        "REJECTED", "FAILED" -> Color(0xFFFF6B6B)
                        "RUNNING", "QUEUED" -> p.accent
                        else -> p.muted
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(action.path.ifBlank { action.type }, color = p.accent, fontFamily = monoFont, fontSize = 9.sp)
            Spacer(Modifier.height(5.dp))
            Text(
                action.detail.take(900),
                color = p.muted,
                fontFamily = monoFont,
                fontSize = 9.sp,
                lineHeight = 14.sp
            )
            if (action.status == "PENDING") {
                Spacer(Modifier.height(7.dp))
                Row {
                    GhostButton("Approve") { vm.approveAction(action.id) }
                    Spacer(Modifier.width(6.dp))
                    GhostButton("Reject") { vm.rejectAction(action.id) }
                }
            }
        }
    }
}
