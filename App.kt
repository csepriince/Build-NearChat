package com.nearchat.app.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.nearchat.app.core.BtController
import com.nearchat.app.core.ChatMeta
import com.nearchat.app.core.LinkState

private enum class Screen { HOME, SCAN, CHAT, SETTINGS }

@Composable
fun NearChatApp(ctl: BtController) {
    val s by ctl.state.collectAsState()
    val mode by ctl.themeMode.collectAsState()
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    NearChatTheme(dark) { Root(ctl, s) }
}

@Composable
private fun Root(ctl: BtController, s: com.nearchat.app.core.UiState) {
    val ctx = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val snack = remember { SnackbarHostState() }
    var rationale by remember { mutableStateOf(false) }
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    var deleteTarget by remember { mutableStateOf<ChatMeta?>(null) }

    val needed: Array<String> = remember {
        if (Build.VERSION.SDK_INT >= 31) arrayOf(
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE,
        ) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun granted() = needed.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val a = after; after = null
        if (r.resultCode == Activity.RESULT_OK) { ctl.startListening(); a?.invoke() }
        else ctl.report("Bluetooth ON kiye bina NearChat kaam nahi karega.")
    }
    fun withBluetooth(action: () -> Unit) {
        if (ctl.bluetoothOn()) { ctl.startListening(); action() }
        else { after = action; enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) }
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val a = after; after = null
        if (res.values.all { it }) { if (a != null) withBluetooth(a) else if (ctl.bluetoothOn()) ctl.startListening() }
        else ctl.report("Permission ke bina nearby devices nahi mil sakte. Settings > Open app permissions se allow karein.")
    }
    fun ensure(action: () -> Unit) {
        if (granted()) withBluetooth(action) else { after = action; rationale = true }
    }
    val visibleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    fun makeVisible() = ensure {
        visibleLauncher.launch(
            Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300),
        )
    }

    LaunchedEffect(Unit) { if (granted() && ctl.bluetoothOn()) ctl.startListening() }
    LaunchedEffect(s.link) {
        if (s.link == LinkState.CONNECTED) screen = Screen.CHAT
    }
    LaunchedEffect(s.error) { s.error?.let { snack.showSnackbar(it); ctl.clearError() } }
    BackHandler(screen != Screen.HOME) {
        if (screen == Screen.SCAN) ctl.stopScan()
        screen = Screen.HOME
    }

    Scaffold(snackbarHost = { SnackbarHost(snack) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
                label = "nav",
            ) { sc ->
                when (sc) {
                    Screen.HOME -> HomeScreen(
                        s,
                        onFind = { ensure { screen = Screen.SCAN; ctl.startScan() } },
                        onVisible = { makeVisible() },
                        onSettings = { screen = Screen.SETTINGS },
                        onOpenConnected = { screen = Screen.CHAT },
                        onOpen = { m -> if (ctl.openChat(m)) screen = Screen.CHAT },
                        onDelete = { deleteTarget = it },
                    )
                    Screen.SCAN -> ScanScreen(
                        s,
                        onBack = { ctl.stopScan(); screen = Screen.HOME },
                        onRescan = { ensure { ctl.startScan() } },
                        onVisible = { makeVisible() },
                        onConnect = { d -> ensure { ctl.connect(d) } },
                        onCancel = { ctl.disconnect() },
                    )
                    Screen.CHAT -> ChatScreen(s, ctl, onBack = { screen = Screen.HOME }, onReconnectNeedsPerms = { ensure(it) })
                    Screen.SETTINGS -> SettingsScreen(ctl, onBack = { screen = Screen.HOME })
                }
            }
        }
    }

    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Chat delete karein?") },
            text = { Text("${t.name} ke saath ke saare messages, previews aur is chat ki received files is phone se hat jayengi. Doosre phone par kuch nahi badlega.") },
            confirmButton = { TextButton(onClick = { ctl.deleteChat(t.address); deleteTarget = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }

    s.incoming?.let { inc ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Connection request") },
            text = {
                Text(
                    "${inc.deviceName} aapse connect hona chahta hai.\n\n" +
                        "Security code: ${inc.code}\n" +
                        "Dono phones par yahi code dikhna chahiye. Alag ho to Reject karein.",
                )
            },
            confirmButton = { TextButton(onClick = { ctl.respondToRequest(true) }) { Text("Accept") } },
            dismissButton = { TextButton(onClick = { ctl.respondToRequest(false) }) { Text("Reject") } },
        )
    }

    if (rationale) {
        AlertDialog(
            onDismissRequest = { rationale = false; after = null },
            title = { Text("Permission chahiye") },
            text = {
                Text(
                    if (Build.VERSION.SDK_INT >= 31)
                        "NearChat ko 'Nearby devices' permission chahiye taaki wo paas ke phones dhoondh sake, unse connect ho sake, " +
                            "aur aapka phone dusron ko dikha sake. Aapki location use nahi hoti aur internet ki zaroorat nahi."
                    else
                        "Android 11 ya kam par Bluetooth scan ke liye Location permission zaroori hoti hai. " +
                            "NearChat aapki location store ya share nahi karta; ye sirf nearby phones dhoondhne ke liye hai.",
                )
            },
            confirmButton = { TextButton(onClick = { rationale = false; permLauncher.launch(needed) }) { Text("Allow") } },
            dismissButton = { TextButton(onClick = { rationale = false; after = null }) { Text("Not now") } },
        )
    }
}
