@file:OptIn(ExperimentalMaterial3Api::class)

package com.nearchat.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nearchat.app.core.BtController
import com.nearchat.app.core.ChatMeta
import com.nearchat.app.core.Device
import com.nearchat.app.core.LinkState
import com.nearchat.app.core.Media
import com.nearchat.app.core.Msg
import com.nearchat.app.core.PickedFile
import com.nearchat.app.core.Status
import com.nearchat.app.core.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val timeFmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
private val dayFmt = SimpleDateFormat("dd MMM", Locale.getDefault())

private fun listTime(t: Long): String {
    val a = Calendar.getInstance().apply { timeInMillis = t }
    val b = Calendar.getInstance()
    val same = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return if (same) timeFmt.format(Date(t)) else dayFmt.format(Date(t))
}

// ============================================================ HOME (chat list dashboard)

@Composable
fun HomeScreen(
    s: UiState,
    onFind: () -> Unit,
    onVisible: () -> Unit,
    onSettings: () -> Unit,
    onOpenConnected: () -> Unit,
    onOpen: (ChatMeta) -> Unit,
    onDelete: (ChatMeta) -> Unit,
) {
    val connected = s.link == LinkState.CONNECTED
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoMark(44)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text("NearChat", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Private. Local. Offline.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onVisible) { Icon(Icons.Default.Visibility, "Make my phone visible") }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
        }
        if (connected) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp))
                    .background(Online.copy(alpha = 0.14f)).clickable(onClick = onOpenConnected).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusDot(true)
                Spacer(Modifier.size(10.dp))
                Text("Connected to ${s.peerName} \u2022 chat kholein", fontWeight = FontWeight.Medium)
            }
        }
        if (s.chats.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                LogoMark(120, animate = false)
                Spacer(Modifier.height(16.dp))
                Text("Abhi koi chat nahi", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Chat and share files without internet. Neeche button dabakar nearby phone dhoondhein.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            Text(
                "Chats", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                items(s.chats, key = { it.address }) { m ->
                    ChatRow(m, online = connected && s.openAddr == m.address, onClick = { onOpen(m) }, onLong = { onDelete(m) })
                    HorizontalDivider(Modifier.padding(start = 84.dp), color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
            Text(
                "Chat delete karne ke liye usse dabakar rakhein.", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 6.dp),
            )
        }
        GradientButton("Find Nearby Devices", onFind, Modifier.fillMaxWidth().padding(16.dp))
    }
}

@Composable
private fun ChatRow(m: ChatMeta, online: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .pointerInput(m.address) { detectTapGestures(onTap = { onClick() }, onLongPress = { onLong() }) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(m.name, 52, online)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(m.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(listTime(m.lastTime), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(m.lastText, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        }
    }
}

@Composable
fun DeviceCard(d: Device, action: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Bluetooth, null, tint = Electric) }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(d.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (d.paired) "Paired \u2022 " else "") + "Bluetooth \u2022 " + d.address,
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onClick, shape = RoundedCornerShape(14.dp)) { Text(action) }
        }
    }
}

// ============================================================ SCAN

@Composable
fun ScanScreen(
    s: UiState,
    onBack: () -> Unit,
    onRescan: () -> Unit,
    onVisible: () -> Unit,
    onConnect: (Device) -> Unit,
    onCancel: () -> Unit,
) {
    val busy = s.link == LinkState.CONNECTING || s.link == LinkState.WAITING_PEER
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Nearby devices", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onRescan, enabled = !s.scanning && !busy) { Text("Rescan") }
        }
        if (busy) {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Radar(active = true, size = 160)
                Text(
                    if (s.link == LinkState.CONNECTING) "Connecting to ${s.peerName}\u2026" else "Waiting for ${s.peerName} to accept\u2026",
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp),
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            return@Column
        }
        if (s.scanning) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Radar(active = true, size = 56)
                Spacer(Modifier.size(12.dp))
                Text("Scanning\u2026 dusre phone par NearChat khula rakhein.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            items(s.devices, key = { it.address }) { d -> DeviceCard(d, "Connect") { onConnect(d) } }
            if (s.devices.isEmpty() && !s.scanning) {
                item {
                    Text(
                        "Koi device nahi mila. Dusre phone par 'Make my phone visible' dabayein, phir Rescan karein.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp),
                    )
                }
            }
        }
        OutlinedButton(onClick = onVisible, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Make my phone visible (5 min)")
        }
    }
}

// ============================================================ CHAT

@Composable
fun ChatScreen(s: UiState, ctl: BtController, onBack: () -> Unit, onReconnectNeedsPerms: (() -> Unit) -> Unit) {
    var input by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<List<PickedFile>>(emptyList()) }
    var menu by remember { mutableStateOf(false) }
    var attach by remember { mutableStateOf(false) }
    var delMsg by remember { mutableStateOf<Msg?>(null) }
    var delChat by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<Msg?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        pending = pending + uris.mapNotNull { ctl.describe(it) }
    }
    val list = rememberLazyListState()
    LaunchedEffect(s.messages.size) { if (s.messages.isNotEmpty()) list.animateScrollToItem(s.messages.lastIndex) }
    val connected = s.link == LinkState.CONNECTED
    val title = s.openName.ifBlank { s.peerName }

    Column(Modifier.fillMaxSize()) {
        // header
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Avatar(title, 40, connected)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (connected) "Bluetooth \u2022 Encrypted \u2022 Code ${s.code}" else "Not connected",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete chat") }, onClick = { menu = false; delChat = true })
                    if (connected) DropdownMenuItem(text = { Text("Disconnect") }, onClick = { menu = false; ctl.disconnect(); onBack() })
                }
            }
        }
        if (!connected) {
            val busy = s.link == LinkState.CONNECTING || s.link == LinkState.WAITING_PEER
            Row(
                Modifier.fillMaxWidth().background(Offline.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (busy) "Connecting\u2026" else "Connection lost. Reconnect karne ki koshish karein.",
                    modifier = Modifier.weight(1f), fontSize = 14.sp,
                )
                if (!busy) TextButton(onClick = { onReconnectNeedsPerms { ctl.reconnect() } }) { Text("Reconnect") }
            }
        }
        // messages
        LazyColumn(
            state = list, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(s.messages, key = { it.id }) { m ->
                Bubble(m, ctl, onLong = { delMsg = m }, onOpen = { viewing = m })
            }
        }
        // attachment menu
        AnimatedVisibility(attach, enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 2 }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Photos" to "image/*", "Videos" to "video/*", "PDF" to "application/pdf", "Audio" to "audio/*", "Any file" to "*/*")
                    .forEach { (label, type) ->
                        FilterChip(selected = false, onClick = { attach = false; picker.launch(type) }, label = { Text(label) })
                    }
            }
        }
        // pending files preview
        if (pending.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                pending.forEach { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Description, null, tint = Electric)
                        Spacer(Modifier.size(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
                            Text(formatSize(f.size), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { pending = pending - f }) { Icon(Icons.Default.Close, "Remove") }
                    }
                }
            }
        }
        // composer
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { attach = !attach }, enabled = connected) { Icon(Icons.Default.AttachFile, "Attach") }
            TextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text("Message") }, maxLines = 4, shape = RoundedCornerShape(26.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.size(8.dp))
            val canSend = connected && (input.isNotBlank() || pending.isNotEmpty())
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(if (canSend) brand else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)))
                    .clickable(enabled = canSend) {
                        if (pending.isNotEmpty()) { ctl.sendFiles(pending); pending = emptyList() }
                        if (input.isNotBlank()) { ctl.sendText(input.trim().take(20000)); input = "" }
                    },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White) }
        }
    }

    delMsg?.let { m ->
        AlertDialog(
            onDismissRequest = { delMsg = null },
            title = { Text("Message delete karein?") },
            text = {
                Text(
                    if (!m.mine && m.savedPath != null) "Ye message aur is phone par save hui file hat jayegi. Doosre phone par kuch nahi badlega."
                    else "Ye sirf is phone se hatega. Doosre phone par kuch nahi badlega.",
                )
            },
            confirmButton = { TextButton(onClick = { ctl.deleteMessage(m.id); delMsg = null }) { Text("Delete", color = Offline) } },
            dismissButton = { TextButton(onClick = { delMsg = null }) { Text("Cancel") } },
        )
    }
    if (delChat) {
        AlertDialog(
            onDismissRequest = { delChat = false },
            title = { Text("Poori chat delete karein?") },
            text = { Text("$title ke saath ke saare messages, photos ke previews aur is chat ki received files is phone se hat jayengi.") },
            confirmButton = {
                TextButton(onClick = { delChat = false; ctl.deleteChat(s.openAddr); onBack() }) { Text("Delete", color = Offline) }
            },
            dismissButton = { TextButton(onClick = { delChat = false }) { Text("Cancel") } },
        )
    }
    viewing?.let { m -> MediaViewer(m) { viewing = null } }
}

@Composable
private fun MediaViewer(m: Msg, onClose: () -> Unit) {
    val path = if (!m.mine && m.savedPath != null && Media.isImage(m.fileName ?: "")) m.savedPath else m.thumb
    val bmp = rememberBitmap(path, 1600)
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
            if (bmp != null) Image(bmp, m.fileName, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Icon(Icons.Default.Close, "Close", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().background(Color(0x88000000)).padding(14.dp)) {
                Text(m.fileName ?: "", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(formatSize(m.fileSize), color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                if (!m.mine && m.savedPath != null) Text("Saved: ${m.savedPath}", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
            }
        }
    }
}

@Composable
fun Bubble(m: Msg, ctl: BtController, onLong: () -> Unit, onOpen: () -> Unit) {
    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(visibleState = vis, enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
            val shape = RoundedCornerShape(
                topStart = 20.dp, topEnd = 20.dp,
                bottomStart = if (m.mine) 20.dp else 6.dp, bottomEnd = if (m.mine) 6.dp else 20.dp,
            )
            val bg = if (m.mine) Modifier.background(brand, shape) else Modifier.background(MaterialTheme.colorScheme.surface, shape)
            val fg = if (m.mine) Color.White else MaterialTheme.colorScheme.onSurface
            Column(
                Modifier.widthIn(max = 300.dp).then(bg)
                    .pointerInput(m.id) { detectTapGestures(onLongPress = { onLong() }) }
                    .padding(10.dp),
            ) {
                if (m.text != null) {
                    Text(m.text, color = fg, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 2.dp))
                } else {
                    FileBody(m, ctl, fg, onLong, onOpen)
                }
                Row(Modifier.align(Alignment.End).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeFmt.format(Date(m.time)), fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
                    if (m.mine && m.text != null) {
                        val tick = when (m.status) {
                            Status.SENDING -> " \u2026"
                            Status.SENT -> " \u2713"
                            Status.DELIVERED -> " \u2713\u2713"
                            Status.FAILED -> " ! failed"
                            else -> ""
                        }
                        Text(tick, fontSize = 11.sp, color = fg.copy(alpha = 0.85f))
                    }
                }
                if (m.mine && m.text != null && m.status == Status.FAILED) {
                    TextButton(onClick = { ctl.retry(m.id) }) { Text("Retry", color = fg) }
                }
            }
        }
    }
}

@Composable
private fun FileBody(m: Msg, ctl: BtController, fg: Color, onLong: () -> Unit, onOpen: () -> Unit) {
    val name = m.fileName ?: "file"
    val bmp = rememberBitmap(m.thumb, 640)
    if (bmp != null) {
        Box(
            Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp))
                .pointerInput(m.id) { detectTapGestures(onTap = { onOpen() }, onLongPress = { onLong() }) },
            contentAlignment = Alignment.Center,
        ) {
            Image(bmp, name, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
            if (Media.isVideo(name)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, "Video", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }
        }
        Text("$name \u2022 ${formatSize(m.fileSize)}", color = fg.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = fg, modifier = Modifier.size(34.dp))
            Spacer(Modifier.size(10.dp))
            Column {
                Text(name, color = fg, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatSize(m.fileSize), color = fg.copy(alpha = 0.75f), fontSize = 12.sp)
            }
        }
    }
    val statusText = when (m.status) {
        Status.OFFERED -> if (m.mine) "Waiting for them to accept\u2026" else "Incoming file"
        Status.TRANSFERRING -> "${(m.progress * 100).toInt()}%"
        Status.DONE -> if (m.mine) "Sent \u2713" else "Saved \u2713"
        Status.REJECTED -> "Rejected"
        Status.CANCELLED -> "Cancelled"
        Status.FAILED -> "Transfer failed"
        else -> ""
    }
    if (m.status == Status.TRANSFERRING) {
        LinearProgressIndicator(
            progress = { m.progress }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            color = fg, trackColor = fg.copy(alpha = 0.25f),
        )
    }
    if (statusText.isNotEmpty()) Text(statusText, color = fg.copy(alpha = 0.85f), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    Row {
        if (!m.mine && m.status == Status.OFFERED) {
            TextButton(onClick = { ctl.respondToOffer(m.id, false) }) { Text("Reject", color = fg) }
            TextButton(onClick = { ctl.respondToOffer(m.id, true) }) { Text("Accept", color = fg, fontWeight = FontWeight.Bold) }
        }
        if (m.status == Status.TRANSFERRING || (m.mine && m.status == Status.OFFERED)) {
            TextButton(onClick = { ctl.cancelTransfer(m.id) }) { Text("Cancel", color = fg) }
        }
        if (m.mine && m.status == Status.FAILED) {
            TextButton(onClick = { ctl.retry(m.id) }) { Text("Retry", color = fg, fontWeight = FontWeight.Bold) }
        }
    }
}
