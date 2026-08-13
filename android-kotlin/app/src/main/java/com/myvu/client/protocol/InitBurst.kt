package com.myvu.client.protocol

import com.myvu.client.core.Hex
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.ArrayList

/**
 * The captured init burst, ported from AppLayerMixin._load_init_script /
 * send_init_burst in myvu_client/myvu/applayer.py.
 */
object InitBurst {
    const val ASSET_NAME: String = "captured_init.txt"

    /** One replayable message: the relay body plus the routing fields to reuse. */
    class Entry internal constructor(
        @JvmField val frame: String,
        @JvmField val msgBody: ByteArray,
        @JvmField val needCallback: Int,
        @JvmField val category: Int,
        @JvmField val appUniteCode: Int
    ) {
        fun bodyText(): String {
            return String(msgBody, StandardCharsets.UTF_8)
        }
    }

    /**
     * Parses the capture and returns only the messages that should be replayed:
     * data frames (msgType == SEND) whose body is not stale state.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun load(`in`: InputStream): List<Entry> {
        val out = ArrayList<Entry>()
        val r = BufferedReader(InputStreamReader(`in`, StandardCharsets.UTF_8))
        var line: String?
        while (r.readLine().also { line = it } != null) {
            val l = line!!.trim()
            if (l.isEmpty() || l.startsWith("#")) continue

            val parts = l.split("\t")
            if (parts.size < 3) continue
            val frame = parts[0]
            val content = Hex.decode(parts[2])

            val m = Relay.parseFrame(content)
            if (m == null || m.msgType != MsgType.SEND) continue

            val bodyText = String(m.msgBody, StandardCharsets.UTF_8)
            if (bodyText.contains("SyncOffSetTime") || bodyText.contains("sync_clone_data")) {
                continue
            }
            out.add(Entry(frame, m.msgBody, m.needCallback, m.category, m.appUniteCode))
        }
        return out
    }
}
