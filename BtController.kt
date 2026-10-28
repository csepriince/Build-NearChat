package com.nearchat.app.core

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * All Bluetooth logic: discovery, listening, connecting, encrypted messaging, file transfer,
 * plus saving/deleting chats on the phone. UI only observes [state] and calls the public functions.
 */
@SuppressLint("MissingPermission")
class BtController(private val app: Application) {

    companion object {
        val APP_UUID: UUID = UUID.fromString("7c1b5e2e-3f7a-4d52-9a6b-4e6e0c1f9a11")
        const val CHUNK = 16 * 1024
    }

    private val prefs = app.getSharedPreferences("nearchat", Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? = app.getSystemService(BluetoothManager::class.java)?.adapter
    private val store = ChatStore(app)
    private val thumbDir = File(app.filesDir, "thumbs")

    private val _s = MutableStateFlow(UiState(chats = store.list()))
    val state: StateFlow<UiState> = _s.asStateFlow()
    val themeMode = MutableStateFlow(prefs.getInt("theme", 0)) // 0 system, 1 light, 2 dark

    @Volatile private var conn: Conn? = null
    @Volatile private var pendingConn: Conn? = null
    @Volatile private var server: BluetoothServerSocket? = null
    @Volatile private var connectingSock: BluetoothSocket? = null
    @Volatile private var attempt = 0

    private val sendPool = Executors.newSingleThreadExecutor()
    private val ioPool = Executors.newSingleThreadExecutor()
    private val outgoing = ConcurrentHashMap<Long, Uri>()
    private val cancelled = ConcurrentHashMap.newKeySet<Long>()
    private val offers = ConcurrentHashMap<Long, Pair<String, Long>>()
    private val receiving = ConcurrentHashMap<Long, FileOutputStream>()
    private val paths = ConcurrentHashMap<Long, File>()
    private val counts = ConcurrentHashMap<Long, Long>()
    private val lastPct = ConcurrentHashMap<Long, Int>()

    // ---------------------------------------------------------------- basics

    fun bluetoothOn() = adapter?.isEnabled == true
    fun deviceName(): String = runCatching { adapter?.name }.getOrNull() ?: Build.MODEL
    fun receivedDir(): File =
        File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: app.filesDir, "NearChat").apply { mkdirs() }

    fun report(msg: String) = _s.update { it.copy(error = msg) }
    fun clearError() = _s.update { it.copy(error = null) }

    fun setTheme(mode: Int) {
        prefs.edit().putInt("theme", mode).apply()
        themeMode.value = mode
    }

    private fun upd(id: Long, save: Boolean = true, f: (Msg) -> Msg) {
        _s.update { s -> s.copy(messages = s.messages.map { if (it.id == id) f(it) else it }) }
        if (save) persist()
    }

    private fun addMsg(m: Msg) {
        _s.update { it.copy(messages = it.messages.filter { x -> x.id != m.id } + m) }
        persist()
    }

    private fun pack(id: Long, extra: ByteArray = ByteArray(0)): ByteArray =
        ByteBuffer.allocate(8 + extra.size).putLong(id).put(extra).array()

    private fun idOf(b: ByteArray): Long = ByteBuffer.wrap(b).long

    // ---------------------------------------------------------------- saved chats

    /** Writes the open chat to disk (always the newest state) and refreshes the dashboard list. */
    private fun persist() {
        val s = _s.value
        val addr = s.openAddr
        if (addr.isBlank()) return
        if (s.messages.isEmpty()) {
            ioPool.execute { store.delete(addr) }
            _s.update { st -> st.copy(chats = st.chats.filter { it.address != addr }) }
            return
        }
        ioPool.execute {
            val st = _s.value
            if (st.openAddr == addr && st.messages.isNotEmpty()) store.save(addr, st.openName, st.messages)
        }
        val last = s.messages.last()
        val meta = ChatMeta(addr, s.openName, Media.previewOf(last), last.time)
        _s.update { st -> st.copy(chats = (st.chats.filter { it.address != addr } + meta).sortedByDescending { it.lastTime }) }
    }

    /** Opens a saved chat from the dashboard. Returns false if the phone is busy with another connection. */
    fun openChat(meta: ChatMeta): Boolean {
        val s = _s.value
        if (s.link == LinkState.CONNECTED && s.openAddr != meta.address) {
            report("Pehle current connection disconnect karein, phir doosri chat kholein.")
            return false
        }
        if (s.link == LinkState.CONNECTING || s.link == LinkState.WAITING_PEER) {
            report("Connection ban raha hai, thoda ruk jayein.")
            return false
        }
        if (s.openAddr != meta.address) {
            val st = store.load(meta.address)
            _s.update {
                it.copy(
                    openAddr = meta.address, openName = st?.name ?: meta.name, messages = st?.messages ?: emptyList(),
                    link = LinkState.IDLE, code = "",
                )
            }
        }
        return true
    }

    private fun deleteMedia(m: Msg) {
        m.thumb?.let { runCatching { File(it).delete() } }
        if (!m.mine) m.savedPath?.let { p ->
            runCatching {
                val f = File(p)
                if (f.canonicalPath.startsWith(receivedDir().canonicalPath)) f.delete()
            }
        }
    }

    fun deleteMessage(id: Long) {
        val m = _s.value.messages.firstOrNull { it.id == id } ?: return
        if (m.status == Status.TRANSFERRING || m.status == Status.OFFERED) cancelTransfer(id)
        _s.update { s -> s.copy(messages = s.messages.filter { it.id != id }) }
        ioPool.execute { deleteMedia(m) }
        persist()
    }

    /** Deletes the conversation, its thumbnails and the files received in it. */
    fun deleteChat(addr: String) {
        val s = _s.value
        val msgs = if (s.openAddr == addr) s.messages else store.load(addr)?.messages ?: emptyList()
        if (s.openAddr == addr) {
            if (s.link == LinkState.CONNECTED) cleanupTransfers()
            _s.update { it.copy(messages = emptyList(), chats = it.chats.filter { c -> c.address != addr }) }
        } else {
            _s.update { it.copy(chats = it.chats.filter { c -> c.address != addr }) }
        }
        ioPool.execute {
            msgs.forEach { deleteMedia(it) }
            store.delete(addr)
        }
    }

    fun deleteAllChats() {
        _s.value.chats.map { it.address }.forEach { deleteChat(it) }
    }

    // ---------------------------------------------------------------- discovery

    private val receiver = object : BroadcastReceiver() {
        @Suppress("DEPRECATION")
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val d: BluetoothDevice? =
                        if (Build.VERSION.SDK_INT >= 33)
                            i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (d != null) addFound(d)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> _s.update { it.copy(scanning = false) }
            }
        }
    }

    init {
        val f = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        ContextCompat.registerReceiver(app, receiver, f, ContextCompat.RECEIVER_EXPORTED)
    }

    private fun addFound(d: BluetoothDevice) {
        val dev = Device(d.name ?: "Unknown device", d.address, d.bondState == BluetoothDevice.BOND_BONDED)
        _s.update { s ->
            if (s.devices.any { it.address == dev.address }) s else s.copy(devices = s.devices + dev)
        }
    }

    fun startScan() {
        val a = adapter ?: return report("Is phone mein Bluetooth hardware nahi hai.")
        if (!a.isEnabled) return report("Bluetooth OFF hai. Pehle Bluetooth ON karein.")
        val paired = a.bondedDevices.map { Device(it.name ?: "Unknown device", it.address, true) }
        _s.update { it.copy(devices = paired, scanning = true) }
        a.cancelDiscovery()
        if (!a.startDiscovery()) {
            _s.update { it.copy(scanning = false) }
            report("Scan start nahi ho paya. Android 11 ya kam par Location ON hona zaroori hai.")
        }
    }

    fun stopScan() {
        runCatching { adapter?.cancelDiscovery() }
        _s.update { it.copy(scanning = false) }
    }

    // ---------------------------------------------------------------- listening (receiver side)

    fun startListening() {
        val a = adapter ?: return
        if (!a.isEnabled || server != null) return
        val ss = try {
            a.listenUsingRfcommWithServiceRecord("NearChat", APP_UUID)
        } catch (e: Exception) {
            return
        }
        server = ss
        thread(isDaemon = true, name = "nearchat-accept") {
            while (true) {
                val sock = try { ss.accept() } catch (e: Exception) { break }
                handleIncoming(sock)
            }
            if (server === ss) server = null
        }
    }

    private fun handleIncoming(sock: BluetoothSocket) {
        if (conn != null || pendingConn != null) {
            runCatching { sock.close() }
            return
        }
        try {
            val c = Conn.handshake(sock)
            val (t, body) = c.read()
            if (t != T.HELLO) { c.close(); return }
            pendingConn = c
            _s.update { it.copy(incoming = Incoming(String(body, Charsets.UTF_8).take(60), c.code)) }
        } catch (e: Exception) {
            runCatching { sock.close() }
        }
    }

    /** User tapped Accept / Reject on the connection request. */
    fun respondToRequest(accept: Boolean) {
        val c = pendingConn ?: return
        val name = _s.value.incoming?.deviceName ?: "Device"
        pendingConn = null
        _s.update { it.copy(incoming = null) }
        thread {
            try {
                if (accept) {
                    c.send(T.ACCEPT)
                    onConnected(c, name)
                } else {
                    c.send(T.REJECT)
                    c.close()
                }
            } catch (e: IOException) {
                c.close()
                report("Request accept karte waqt connection toot gaya.")
            }
        }
    }

    // ---------------------------------------------------------------- connecting (sender side)

    fun connect(d: Device) {
        val a = adapter ?: return
        val st = _s.value.link
        if (st == LinkState.CONNECTING || st == LinkState.WAITING_PEER) return
        val fallback = if (st == LinkState.LOST) LinkState.LOST else LinkState.IDLE
        val my = ++attempt
        a.cancelDiscovery()
        _s.update { it.copy(link = LinkState.CONNECTING, peerName = d.name, scanning = false) }
        thread(name = "nearchat-connect") {
            var sock: BluetoothSocket? = null
            try {
                sock = a.getRemoteDevice(d.address).createRfcommSocketToServiceRecord(APP_UUID)
                connectingSock = sock
                sock.connect()
                val c = Conn.handshake(sock)
                c.send(T.HELLO, deviceName().toByteArray(Charsets.UTF_8))
                _s.update { it.copy(link = LinkState.WAITING_PEER) }
                val (t, _) = c.read()
                if (my != attempt) { c.close(); return@thread }
                if (t == T.ACCEPT) {
                    onConnected(c, d.name)
                } else {
                    c.close()
                    _s.update { it.copy(link = fallback) }
                    report("${d.name} ne connection reject kar diya.")
                }
            } catch (e: Exception) {
                runCatching { sock?.close() }
                if (my != attempt) return@thread
                _s.update { it.copy(link = fallback) }
                report("Connect nahi ho paya. Dusre phone par NearChat khula hona chahiye aur Bluetooth ON hona chahiye.")
            } finally {
                if (connectingSock === sock) connectingSock = null
            }
        }
    }

    /** Reconnect to the person whose chat is currently open. */
    fun reconnect() {
        val s = _s.value
        if (s.openAddr.isBlank()) return report("Pehle koi device select karein.")
        connect(Device(s.openName, s.openAddr))
    }

    fun disconnect() {
        attempt++
        runCatching { connectingSock?.close() }
        connectingSock = null
        val c = conn
        conn = null
        cleanupTransfers()
        if (c != null) thread { c.close() }
        _s.update { s ->
            s.copy(
                link = LinkState.IDLE, code = "",
                messages = s.messages.map {
                    if (it.status == Status.TRANSFERRING || it.status == Status.OFFERED || it.status == Status.SENDING) it.copy(status = Status.FAILED) else it
                },
            )
        }
        persist()
    }

    private fun onConnected(c: Conn, peer: String) {
        val addr = runCatching { c.socket.remoteDevice.address }.getOrDefault("")
        conn = c
        val stored = if (addr.isNotBlank()) store.load(addr) else null
        _s.update {
            it.copy(
                link = LinkState.CONNECTED, peerName = peer, code = c.code,
                openAddr = addr, openName = peer, messages = stored?.messages ?: emptyList(),
            )
        }
        thread(name = "nearchat-read") { readLoop(c) }
    }

    private fun onLost(c: Conn) {
        c.close()
        if (conn !== c) return
        conn = null
        cleanupTransfers()
        _s.update { s ->
            s.copy(
                link = LinkState.LOST,
                messages = s.messages.map {
                    if (it.status == Status.TRANSFERRING || it.status == Status.OFFERED || it.status == Status.SENDING) it.copy(status = Status.FAILED) else it
                },
            )
        }
        persist()
    }

    private fun cleanupTransfers() {
        receiving.keys.toList().forEach { closeReceive(it, delete = true) }
        offers.clear(); cancelled.clear()
    }

    // ---------------------------------------------------------------- reading

    private fun readLoop(c: Conn) {
        try {
            while (true) {
                val (t, b) = c.read()
                handle(c, t, b)
            }
        } catch (e: Exception) {
            onLost(c)
        }
    }

    private fun handle(c: Conn, t: Int, b: ByteArray) {
        when (t) {
            T.TEXT -> {
                val id = idOf(b)
                val text = String(b, 8, b.size - 8, Charsets.UTF_8)
                addMsg(Msg(id = id, mine = false, status = Status.DONE, text = text))
                thread { runCatching { c.send(T.ACK, pack(id)) } }
            }
            T.ACK -> upd(idOf(b)) { it.copy(status = Status.DELIVERED) }
            T.OFFER -> {
                val bb = ByteBuffer.wrap(b)
                val id = bb.long
                val size = bb.long
                val name = safeName(String(b, 16, b.size - 16, Charsets.UTF_8))
                offers[id] = name to size
                addMsg(Msg(id = id, mine = false, status = Status.OFFERED, fileName = name, fileSize = size))
            }
            T.FACCEPT -> {
                val id = idOf(b)
                if (cancelled.contains(id)) return
                upd(id) { it.copy(status = Status.TRANSFERRING, progress = 0f) }
                sendPool.execute { streamFile(c, id) }
            }
            T.FREJECT -> {
                val id = idOf(b)
                outgoing.remove(id)
                upd(id) { it.copy(status = Status.REJECTED) }
            }
            T.CHUNK -> onChunk(c, b)
            T.FEND -> onFileEnd(idOf(b))
            T.FCANCEL -> {
                val id = idOf(b)
                if (receiving.containsKey(id)) closeReceive(id, delete = true) else cancelled.add(id)
                offers.remove(id)
                upd(id) { if (it.status == Status.DONE) it else it.copy(status = Status.CANCELLED) }
            }
        }
    }

    private fun onChunk(c: Conn, b: ByteArray) {
        val id = idOf(b)
        val out = receiving[id] ?: return
        val size = offers[id]?.second ?: return
        val total = (counts[id] ?: 0L) + (b.size - 8)
        try {
            if (total > size) throw IOException("too much data")
            out.write(b, 8, b.size - 8)
        } catch (e: IOException) {
            closeReceive(id, delete = true)
            upd(id) { it.copy(status = Status.FAILED) }
            report("File save nahi ho payi (storage ya data error).")
            thread { runCatching { c.send(T.FCANCEL, pack(id)) } }
            return
        }
        counts[id] = total
        val pct = (total * 100 / size).toInt()
        if (lastPct[id] != pct) {
            lastPct[id] = pct
            upd(id, save = false) { it.copy(progress = total.toFloat() / size) }
        }
    }

    private fun onFileEnd(id: Long) {
        val f = paths[id]
        val got = counts[id] ?: 0L
        val size = offers[id]?.second ?: -1L
        val name = offers[id]?.first ?: ""
        closeReceive(id, delete = false)
        offers.remove(id)
        if (f != null && got == size) {
            upd(id) { it.copy(status = Status.DONE, progress = 1f, savedPath = f.absolutePath) }
            if (Media.isImage(name) || Media.isVideo(name)) {
                ioPool.execute {
                    val bmp = if (Media.isImage(name)) Media.decodeFile(f.absolutePath, 1024)
                    else Media.videoFrame { it.setDataSource(f.absolutePath) }
                    val th = bmp?.let { Media.saveThumb(thumbDir, id, it) }
                    if (th != null) upd(id) { it.copy(thumb = th) }
                }
            }
        } else {
            f?.delete()
            upd(id) { it.copy(status = Status.FAILED) }
        }
    }

    private fun closeReceive(id: Long, delete: Boolean) {
        runCatching { receiving.remove(id)?.close() }
        val f = paths.remove(id)
        if (delete) f?.delete()
        counts.remove(id); lastPct.remove(id)
    }

    // ---------------------------------------------------------------- sending text

    fun sendText(text: String) {
        val c = conn ?: return report("Connected nahi hain. Pehle reconnect karein.")
        val id = Random.nextLong()
        addMsg(Msg(id = id, mine = true, status = Status.SENDING, text = text))
        thread { pushText(c, id, text) }
    }

    private fun pushText(c: Conn, id: Long, text: String) {
        try {
            c.send(T.TEXT, pack(id, text.toByteArray(Charsets.UTF_8)))
            upd(id) { if (it.status == Status.SENDING) it.copy(status = Status.SENT) else it }
        } catch (e: IOException) {
            upd(id) { it.copy(status = Status.FAILED) }
        }
    }

    // ---------------------------------------------------------------- sending files

    fun describe(uri: Uri): PickedFile? {
        var name = "file"
        var size = -1L
        runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) {
                    val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = cur.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = cur.getString(ni) ?: name
                    if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
                }
            }
        }
        if (size <= 0) size = runCatching { app.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } }.getOrNull() ?: -1L
        if (size <= 0) { report("File khali hai ya uska size pata nahi chala: $name"); return null }
        return PickedFile(uri, name, size)
    }

    fun sendFiles(files: List<PickedFile>) {
        val c = conn ?: return report("Connected nahi hain. Pehle reconnect karein.")
        for (f in files) {
            val id = Random.nextLong()
            outgoing[id] = f.uri
            addMsg(Msg(id = id, mine = true, status = Status.OFFERED, fileName = f.name, fileSize = f.size))
            thread {
                makeSendThumb(id, f)
                pushOffer(c, id, f.name, f.size)
            }
        }
    }

    private fun makeSendThumb(id: Long, f: PickedFile) {
        val bmp = when {
            Media.isImage(f.name) -> Media.decodeStream({ app.contentResolver.openInputStream(f.uri) }, 1024)
            Media.isVideo(f.name) -> Media.videoFrame { it.setDataSource(app, f.uri) }
            else -> null
        } ?: return
        val th = Media.saveThumb(thumbDir, id, bmp)
        upd(id) { it.copy(thumb = th) }
    }

    private fun pushOffer(c: Conn, id: Long, name: String, size: Long) {
        try {
            val nb = name.toByteArray(Charsets.UTF_8)
            c.send(T.OFFER, ByteBuffer.allocate(16 + nb.size).putLong(id).putLong(size).put(nb).array())
        } catch (e: IOException) {
            upd(id) { it.copy(status = Status.FAILED) }
        }
    }

    private fun streamFile(c: Conn, id: Long) {
        val uri = outgoing[id] ?: return
        val size = _s.value.messages.firstOrNull { it.id == id }?.fileSize ?: return
        try {
            val ins = app.contentResolver.openInputStream(uri) ?: throw IOException("cannot open")
            ins.use {
                val buf = ByteArray(CHUNK)
                var total = 0L
                var pct = -1
                while (true) {
                    if (cancelled.remove(id)) return
                    val n = it.read(buf)
                    if (n < 0) break
                    c.send(T.CHUNK, pack(id, buf.copyOf(n)))
                    total += n
                    val p = (total * 100 / size).toInt()
                    if (p != pct) {
                        pct = p
                        upd(id, save = false) { m -> m.copy(progress = (total.toFloat() / size).coerceAtMost(1f)) }
                    }
                }
            }
            c.send(T.FEND, pack(id))
            outgoing.remove(id)
            upd(id) { it.copy(status = Status.DONE, progress = 1f) }
        } catch (e: Exception) {
            upd(id) { if (it.status == Status.CANCELLED) it else it.copy(status = Status.FAILED) }
        }
    }

    // ---------------------------------------------------------------- receiving files (user decision)

    fun respondToOffer(id: Long, accept: Boolean) {
        val c = conn ?: return report("Connection toot gaya hai.")
        val (name, size) = offers[id] ?: return
        thread {
            try {
                if (!accept) {
                    offers.remove(id)
                    upd(id) { it.copy(status = Status.REJECTED) }
                    c.send(T.FREJECT, pack(id))
                    return@thread
                }
                val dir = receivedDir()
                if (dir.usableSpace < size + 20L * 1024 * 1024) {
                    offers.remove(id)
                    upd(id) { it.copy(status = Status.FAILED) }
                    report("Phone mein itni jagah nahi hai ($name).")
                    c.send(T.FREJECT, pack(id))
                    return@thread
                }
                val f = uniqueFile(dir, name)
                paths[id] = f
                counts[id] = 0L
                receiving[id] = FileOutputStream(f)
                upd(id) { it.copy(status = Status.TRANSFERRING, progress = 0f) }
                c.send(T.FACCEPT, pack(id))
            } catch (e: IOException) {
                closeReceive(id, delete = true)
                upd(id) { it.copy(status = Status.FAILED) }
            }
        }
    }

    fun cancelTransfer(id: Long) {
        val c = conn
        if (receiving.containsKey(id)) closeReceive(id, delete = true) else cancelled.add(id)
        offers.remove(id)
        upd(id) { it.copy(status = Status.CANCELLED) }
        if (c != null) thread { runCatching { c.send(T.FCANCEL, pack(id)) } }
    }

    fun retry(id: Long) {
        val c = conn ?: return report("Pehle reconnect karein, phir retry karein.")
        val m = _s.value.messages.firstOrNull { it.id == id && it.mine } ?: return
        if (m.text != null) {
            upd(id) { it.copy(status = Status.SENDING) }
            thread { pushText(c, id, m.text) }
        } else if (m.fileName != null && outgoing.containsKey(id)) {
            cancelled.remove(id)
            upd(id) { it.copy(status = Status.OFFERED, progress = 0f) }
            thread { pushOffer(c, id, m.fileName, m.fileSize) }
        } else {
            report("Ye file ab dobara nahi bheji ja sakti (app band hone ke baad). Dobara attach karke bhejein.")
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun safeName(raw: String): String =
        raw.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[^\\p{L}\\p{N}._\\- ]"), "_")
            .trim().trimStart('.').take(100).ifBlank { "file" }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        if (!f.exists()) return f
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (f.exists()) { f = File(dir, "$base ($i)$ext"); i++ }
        return f
    }

    fun shutdown() {
        runCatching { app.unregisterReceiver(receiver) }
        runCatching { server?.close() }
        server = null
        runCatching { pendingConn?.close() }
        disconnect()
    }
}
