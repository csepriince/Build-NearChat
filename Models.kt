package com.nearchat.app.core

import android.net.Uri

enum class LinkState { IDLE, CONNECTING, WAITING_PEER, CONNECTED, LOST }

enum class Status { SENDING, SENT, DELIVERED, OFFERED, TRANSFERRING, DONE, REJECTED, FAILED, CANCELLED }

data class Device(val name: String, val address: String, val paired: Boolean = false)

data class Incoming(val deviceName: String, val code: String)

data class PickedFile(val uri: Uri, val name: String, val size: Long)

data class Msg(
    val id: Long,
    val mine: Boolean,
    val status: Status,
    val text: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val time: Long = System.currentTimeMillis(),
    val progress: Float = 0f,
    val savedPath: String? = null,
    val thumb: String? = null,
)

/** One row of the chat-list dashboard. */
data class ChatMeta(val address: String, val name: String, val lastText: String, val lastTime: Long)

data class StoredChat(val address: String, val name: String, val messages: List<Msg>)

data class UiState(
    val link: LinkState = LinkState.IDLE,
    val peerName: String = "",
    val code: String = "",
    val scanning: Boolean = false,
    val devices: List<Device> = emptyList(),
    val chats: List<ChatMeta> = emptyList(),
    val openAddr: String = "",
    val openName: String = "",
    val incoming: Incoming? = null,
    val messages: List<Msg> = emptyList(),
    val error: String? = null,
)
