package id.distream.studio.chat

import id.distream.studio.model.LiveEventType
import id.distream.studio.model.LiveFeedEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class TikTokChatBridge {

    private val okHttpClient = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var webSocket: WebSocket? = null

    @Volatile
    var profanityFilterEnabled: Boolean = true

    private val blockedWords = listOf(
        "judol", "slot gacor", "zeus88", "kasino", "anjing", "bangsat", "kontol", "memek"
    )

    private val _events = MutableSharedFlow<LiveFeedEvent>(extraBufferCapacity = 96)
    val events: SharedFlow<LiveFeedEvent> = _events.asSharedFlow()

    private fun nowTimeLabel(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    private fun cleanText(input: String): String {
        if (!profanityFilterEnabled) return input
        var text = input
        blockedWords.forEach { bad ->
            text = text.replace(Regex(Regex.escape(bad), RegexOption.IGNORE_CASE), "***")
        }
        return text
    }

    fun connect(wsEndpoint: String, tiktokUsername: String) {
        disconnect()
        if (wsEndpoint.isBlank()) return

        val cleanUser = tiktokUsername.trim().removePrefix("@")
        val finalUrl = if (cleanUser.isNotEmpty() && !wsEndpoint.contains("uniqueId=")) {
            val sep = if (wsEndpoint.contains("?")) "&" else "?"
            "$wsEndpoint${sep}uniqueId=$cleanUser"
        } else {
            wsEndpoint
        }

        val request = runCatching {
            Request.Builder().url(finalUrl).build()
        }.getOrElse { err ->
            emitEvent(LiveEventType.SYSTEM, "DiStream", "Format URL WebSocket tidak valid: ${err.message}")
            return
        }

        webSocket = okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                emitEvent(LiveEventType.SYSTEM, "DiStream", "Pembaca Live Chat & Event aktif")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                parseIncomingJson(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                emitEvent(
                    LiveEventType.SYSTEM,
                    "DiStream",
                    "Relay eksternal belum aktif — tombol simulasi lokal siap digunakan"
                )
            }
        })
    }

    private fun parseIncomingJson(raw: String) {
        runCatching {
            val root = JSONObject(raw)
            val eventName = root.optString("event", root.optString("type", "chat")).lowercase()
            val data = root.optJSONObject("data") ?: root
            val sender = cleanText(
                data.optString("nickname", data.optString("uniqueId", "Penonton"))
            )

            when (eventName) {
                "gift" -> {
                    val giftName = data.optString("giftName", "Gift")
                    val count = data.optInt("repeatCount", data.optInt("count", 1))
                    val diamonds = data.optInt("diamondCount", count * 25)
                    emitEvent(
                        type = LiveEventType.GIFT,
                        sender = sender,
                        message = "Mengirim $giftName x$count (+$diamonds poin)",
                        amountValue = diamonds
                    )
                }
                "donation", "saweria" -> {
                    val amount = data.optInt("amount", 25000)
                    val note = cleanText(data.optString("message", "Semangat live-nya!"))
                    emitEvent(
                        type = LiveEventType.DONATION,
                        sender = sender,
                        message = "Rp $amount — $note",
                        // amountValue dalam RUPIAH penuh. Sebelumnya dibagi 1000 sehingga
                        // estimasi pendapatan & goal bar tercatat 1000x lebih kecil.
                        amountValue = amount.coerceAtLeast(1)
                    )
                }
                "like" -> {
                    val likes = data.optInt("likeCount", 15)
                    emitEvent(LiveEventType.LIKE, sender, "Tap-tap layar +$likes love", likes)
                }
                "follow" -> {
                    emitEvent(LiveEventType.FOLLOW, sender, "Mulai mengikuti akun")
                }
                else -> {
                    val comment = cleanText(data.optString("comment", data.optString("text", "")))
                    if (comment.isNotBlank()) {
                        emitEvent(LiveEventType.CHAT, sender, comment)
                    }
                }
            }
        }
    }

    fun emitTestGift(sender: String = "Rizky_Pratama", giftName: String = "Paus Samudra", points: Int = 100) {
        emitEvent(LiveEventType.GIFT, sender, "Mengirim $giftName (+$points poin)", points)
    }

    fun emitTestDonation(sender: String = "Hendra_ID", amountIdr: Int = 50_000, msg: String = "Mantap kualitas stream-nya!") {
        emitEvent(LiveEventType.DONATION, sender, "Rp $amountIdr — ${cleanText(msg)}", amountIdr / 1000)
    }

    fun emitTestLoveTap(likes: Int = 250) {
        emitEvent(LiveEventType.LIKE, "Komunitas", "+$likes Tap-Tap Love masuk", likes)
    }

    fun emitTestChat(sender: String = "Dimas_A", comment: String = "Suara mic jernih banget bang, gas mabar") {
        emitEvent(LiveEventType.CHAT, sender, cleanText(comment))
    }

    private fun emitEvent(
        type: LiveEventType,
        sender: String,
        message: String,
        amountValue: Int = 0
    ) {
        _events.tryEmit(
            LiveFeedEvent(
                type = type,
                senderName = sender,
                message = message,
                amountValue = amountValue,
                timestampLabel = nowTimeLabel()
            )
        )
    }

    fun disconnect() {
        webSocket?.close(1000, "Sesi berakhir")
        webSocket = null
    }
}
