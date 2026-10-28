@file:OptIn(ExperimentalMaterial3Api::class)

package com.nearchat.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nearchat.app.core.BtController

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            content()
        }
    }
}

@Composable
fun SettingsScreen(ctl: BtController, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val mode by ctl.themeMode.collectAsState()
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Section("Theme") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("System", "Light", "Dark").forEachIndexed { i, l ->
                    FilterChip(selected = mode == i, onClick = { ctl.setTheme(i) }, label = { Text(l) })
                }
            }
        }
        Section("This device") {
            Text("Device name: ${ctl.deviceName()}")
            Text("Dusre phone ko aapka Bluetooth naam yahi dikhega.", color = dim, fontSize = 13.sp)
        }
        Section("Received files") {
            Text(ctl.receivedDir().absolutePath, fontSize = 13.sp)
            Text("Files sirf aapke accept karne par is app-folder mein save hoti hain. Photos/videos ka preview app mein dikhta hai; baaki files NearChat khud open nahi karta.", color = dim, fontSize = 13.sp)
        }
        Section("Privacy") {
            Text(
                "• Koi account, phone number ya email nahi.\n" +
                    "• Koi server ya internet use nahi hota; data sirf do phones ke beech jaata hai.\n" +
                    "• Har message aur file AES-256-GCM se encrypt hoti hai (session key sirf RAM mein).\n" +
                    "• Connection aur file tabhi aati hai jab aap Accept karein.\n" +
                    "• Chats sirf aapke phone mein save hoti hain (kahin upload nahi). Kisi bhi chat/message ko delete kar sakte hain.",
                fontSize = 13.sp,
            )
            OutlinedButton(onClick = ctl::deleteAllChats) { Text("Delete all chats") }
        }
        Section("Help & permissions") {
            Text(
                "Nearby devices ke liye Bluetooth permission chahiye (Android 11 ya kam par Location bhi, taaki scan chal sake). " +
                    "Location sirf scan ke liye hai, hum use store ya share nahi karte.",
                fontSize = 13.sp,
            )
            OutlinedButton(onClick = {
                ctx.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }) { Text("Open app permissions") }
        }
        Section("About NearChat") {
            Text("Version 1.0.0 (MVP: Bluetooth)")
            Text("Private. Local. Offline.", color = dim)
        }
        Spacer(Modifier.height(24.dp))
    }
}
