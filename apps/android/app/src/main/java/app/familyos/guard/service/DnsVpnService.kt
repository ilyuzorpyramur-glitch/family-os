package app.familyos.guard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import app.familyos.guard.MainActivity
import app.familyos.guard.R
import app.familyos.guard.api.ApiClient
import app.familyos.guard.data.GuardStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Локальный DNS-фильтр: перехватывает запросы имён и блокирует нежелательные домены.
 * Трафик не расшифровывается и не сохраняется — только имена доменов.
 */
class DnsVpnService : VpnService() {

    private var tun: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: GuardStore

    private val blocked = mutableSetOf<String>()
    private val allowed = mutableSetOf<String>()
    private val patterns = mutableSetOf<String>()
    private val visited = ConcurrentHashMap.newKeySet<String>()

    override fun onCreate() {
        super.onCreate()
        store = GuardStore(this)
        loadRules()
        createChannel()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(2002, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(2002, notification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) startTunnel()
        return START_STICKY
    }

    private fun loadRules() {
        val raw = store.vpnRules ?: return
        runCatching {
            val root = JSONObject(raw)
            blocked.clear(); allowed.clear(); patterns.clear()
            root.optJSONArray("block")?.let { array -> for (i in 0 until array.length()) blocked += array.optString(i) }
            root.optJSONArray("allow")?.let { array -> for (i in 0 until array.length()) allowed += array.optString(i) }
            root.optJSONArray("patterns")?.let { array -> for (i in 0 until array.length()) patterns += array.optString(i) }
        }
    }

    private fun startTunnel() {
        val descriptor = runCatching {
            Builder()
                .setSession("Family OS · фильтр сайтов")
                .addAddress("10.111.222.1", 32)
                .addDnsServer("10.111.222.2")
                .addRoute("10.111.222.2", 32)
                .setBlocking(false)
                .establish()
        }.getOrNull() ?: run {
            stopSelf()
            return
        }

        tun = descriptor
        running = true
        worker = Thread { loop(descriptor) }.also { it.start() }
        scope.launch { refreshRules() }
    }

    private suspend fun refreshRules() {
        runCatching {
            val data = ApiClient(store).siteRules()
            store.vpnRules = data.toString()
            loadRules()
        }
    }

    private fun loop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (error: Exception) {
                break
            }
            if (length <= 0) continue
            val packet = buffer.copyOf(length)
            try {
                val reply = handle(packet)
                if (reply != null) {
                    output.write(reply)
                }
            } catch (error: Exception) {
                // битый пакет — пропускаем
            }
        }
        runCatching { reportVisited() }
    }

    private fun handle(packet: ByteArray): ByteArray? {
        if (packet.size < 28) return null
        val version = (packet[0].toInt() shr 4) and 0x0F
        if (version != 4) return null
        val ihl = (packet[0].toInt() and 0x0F) * 4
        if (packet[9].toInt() != 17) return null // только UDP

        val srcPort = ((packet[ihl].toInt() and 0xFF) shl 8) or (packet[ihl + 1].toInt() and 0xFF)
        val dstPort = ((packet[ihl + 2].toInt() and 0xFF) shl 8) or (packet[ihl + 3].toInt() and 0xFF)
        if (dstPort != 53) return null

        val dnsOffset = ihl + 8
        if (packet.size < dnsOffset + 13) return null

        val domain = parseQuestion(packet, dnsOffset) ?: return null
        visited += domain
        if (visited.size >= 25) scope.launch { reportVisited() }

        val query = packet.copyOfRange(dnsOffset, packet.size)
        val blockedNow = isBlocked(domain)

        if (blockedNow) {
            val response = buildBlockedResponse(query)
            return buildReply(packet, ihl, srcPort, dstPort, response)
        }

        val answer = forward(query) ?: return null
        return buildReply(packet, ihl, srcPort, dstPort, answer)
    }

    private fun isBlocked(domain: String): Boolean {
        val value = domain.lowercase()
        if (allowed.any { value == it || value.endsWith(".$it") }) return false
        if (blocked.any { value == it || value.endsWith(".$it") }) return true
        return patterns.any { value.contains(it) }
    }

    private fun parseQuestion(packet: ByteArray, offset: Int): String? {
        var index = offset + 12
        val labels = mutableListOf<String>()
        var guard = 0
        while (index < packet.size && guard < 64) {
            val size = packet[index].toInt() and 0xFF
            if (size == 0) break
            if (size > 63 || index + size >= packet.size) return null
            labels += String(packet, index + 1, size, Charsets.US_ASCII)
            index += size + 1
            guard += 1
        }
        val name = labels.joinToString(".")
        return if (name.isBlank()) null else name
    }

    private fun forward(query: ByteArray): ByteArray? {
        return runCatching {
            DatagramSocket().use { socket ->
                protect(socket)
                socket.soTimeout = 4000
                val address = InetAddress.getByName("1.1.1.1")
                socket.send(DatagramPacket(query, query.size, address, 53))
                val response = ByteArray(4096)
                val incoming = DatagramPacket(response, response.size)
                socket.receive(incoming)
                incoming.data.copyOf(incoming.length)
            }
        }.getOrNull()
    }

    private fun buildBlockedResponse(query: ByteArray): ByteArray {
        // Заголовок: ответ, отказ + A 0.0.0.0
        val header = query.copyOfRange(0, 12).also {
            it[2] = 0x81.toByte()
            it[3] = 0x80.toByte()
            it[6] = 0
            it[7] = 1
            it[8] = 0; it[9] = 0
            it[10] = 0; it[11] = 0
        }
        var questionEnd = 12
        while (questionEnd < query.size && query[questionEnd].toInt() != 0) {
            questionEnd += (query[questionEnd].toInt() and 0xFF) + 1
        }
        questionEnd += 5
        val question = query.copyOfRange(12, minOf(questionEnd, query.size))

        val answer = byteArrayOf(
            0xC0.toByte(), 0x0C,
            0x00, 0x01,
            0x00, 0x01,
            0x00, 0x00, 0x00, 0x3C,
            0x00, 0x04,
            0x00, 0x00, 0x00, 0x00,
        )
        return header + question + answer
    }

    private fun buildReply(packet: ByteArray, ihl: Int, srcPort: Int, dstPort: Int, payload: ByteArray): ByteArray {
        val total = ihl + 8 + payload.size
        val reply = ByteArray(total)

        System.arraycopy(packet, 0, reply, 0, ihl)
        // меняем местами адреса
        for (i in 0 until 4) {
            reply[12 + i] = packet[16 + i]
            reply[16 + i] = packet[12 + i]
        }
        reply[8] = 64 // TTL
        reply[10] = 0; reply[11] = 0
        reply[2] = ((total shr 8) and 0xFF).toByte()
        reply[3] = (total and 0xFF).toByte()

        // контрольная сумма IP-заголовка
        var sum = 0
        for (i in 0 until ihl step 2) {
            sum += ((reply[i].toInt() and 0xFF) shl 8) or (reply[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val checksum = sum.inv() and 0xFFFF
        reply[10] = ((checksum shr 8) and 0xFF).toByte()
        reply[11] = (checksum and 0xFF).toByte()

        // UDP-заголовок
        val udp = ihl
        reply[udp] = ((dstPort shr 8) and 0xFF).toByte()
        reply[udp + 1] = (dstPort and 0xFF).toByte()
        reply[udp + 2] = ((srcPort shr 8) and 0xFF).toByte()
        reply[udp + 3] = (srcPort and 0xFF).toByte()
        val udpLength = 8 + payload.size
        reply[udp + 4] = ((udpLength shr 8) and 0xFF).toByte()
        reply[udp + 5] = (udpLength and 0xFF).toByte()
        reply[udp + 6] = 0; reply[udp + 7] = 0

        System.arraycopy(payload, 0, reply, udp + 8, payload.size)
        return reply
    }

    private fun reportVisited() {
        if (visited.isEmpty()) return
        val domains = visited.toList()
        visited.clear()
        scope.launch {
            runCatching { ApiClient(store).sendSites(domains) }
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { worker?.interrupt() }
        runCatching { tun?.close() }
        scope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        running = false
        stopSelf()
        super.onRevoke()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel("vpn", "Фильтр сайтов", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    private fun notification(): Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, "vpn")
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle("Family OS · фильтр сайтов")
            .setContentText("Нежелательные сайты блокируются")
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }
}
