package com.myvu.client.ai;

import com.myvu.client.core.BufferPool;
import com.myvu.client.core.LogBus;
import com.myvu.client.protocol.Pb;
import com.myvu.client.protocol.PbValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collects the glasses' microphone stream.
 *
 * The glasses capture audio themselves and push it to the phone as a run of
 * code:109 (CODE_RECORD_DATA_TRANS) messages -- one Opus packet each, carried in
 * protobuf field 5 of the StMessage envelope (the same slot
 * StarryNetMessage.setData uses).
 *
 * Format, taken from the official app's OpusDecoder: Opus, 16 kHz, mono, with
 * packets arriving at one of four discrete sizes (40, 83, 120, 240 bytes). Any
 * other length is something we do not understand and is counted, not decoded.
 *
 * This class only accumulates packets; decoding happens in OpusStream. Keeping
 * them separate means capture can be verified on its own.
 */
public class GlassesMicStream {

    public static final class AudioFrame {
        private final byte[] buffer;
        public final int length;
        private final AtomicInteger refCount = new AtomicInteger(1);

        public AudioFrame(byte[] buffer, int length) {
            this.buffer = buffer;
            this.length = length;
        }

        public byte[] buffer() {
            return buffer;
        }

        public void retain() {
            refCount.incrementAndGet();
        }

        public void release() {
            if (refCount.decrementAndGet() == 0 && buffer != null) {
                BufferPool.recycle(buffer);
            }
        }

        public byte[] copyBytes() {
            if (buffer == null || length <= 0) return new byte[0];
            byte[] copy = new byte[length];
            System.arraycopy(buffer, 0, copy, 0, length);
            return copy;
        }
    }

    /** Packet sizes the device actually emits, per OpusDecoder.Companion.a(). */
    private static final int[] KNOWN_PACKET_SIZES = { 40, 83, 120, 240 };

    /** Field 5 of the StMessage envelope carries the binary payload. */
    private static final int FIELD_AUDIO = 5;

    /** Guards against unbounded growth if an utterance never ends. */
    private static final int MAX_PACKETS = 2000; // ~40s at 20ms per packet

    private final List<AudioFrame> packets = new ArrayList<>();
    private boolean capturing;
    private volatile AudioFrame lastFrame;
    private final List<AudioFrame> justAdded = new ArrayList<>();
    private int unknownSizeCount;
    /** Distinct payload sizes seen, to learn what the device really sends. */
    private final java.util.Set<Integer> observedSizes = new java.util.TreeSet<>();

    /** Begins a new utterance, discarding anything previously buffered. */
    public void start() {
        for (AudioFrame frame : packets) {
            frame.release();
        }
        packets.clear();
        lastFrame = null;
        justAdded.clear();
        unknownSizeCount = 0;
        observedSizes.clear();
        capturing = true;
    }

    public void stop() {
        capturing = false;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public int packetCount() {
        return packets.size();
    }

    /**
     * Offers a code:109 relay body. Returns true if it contained audio.
     *
     * Safe to call when not capturing -- the glasses stream whenever they are
     * listening, which is not always when we want to record.
     */
    public boolean offer(byte[] relayBody) {
        byte[] field5 = extractAudio(relayBody);
        if (field5 == null) {
            rejected++;
            return false;
        }
        if (!capturing) return true; // recognised, but deliberately discarded

        justAdded.clear();
        int i = 0;
        while (i + 2 <= field5.length) {
            int len = ((field5[i] & 0xFF) << 8) | (field5[i + 1] & 0xFF);
            i += 2;
            if (len <= 0 || i + len > field5.length) {
                unknownSizeCount++;
                break;
            }
            byte[] poolBuf = BufferPool.obtain(len);
            System.arraycopy(field5, i, poolBuf, 0, len);
            AudioFrame frame = new AudioFrame(poolBuf, len);
            i += len;

            if (packets.size() >= MAX_PACKETS) {
                LogBus.warn("glasses mic buffer full (" + MAX_PACKETS + ") -- stopping");
                frame.release();
                capturing = false;
                break;
            }
            observedSizes.add(frame.length);
            packets.add(frame);
            justAdded.add(frame);
        }
        if (!justAdded.isEmpty()) lastFrame = justAdded.get(justAdded.size() - 1);
        return true;
    }

    /** The Opus frames extracted from the most recent payload. */
    public List<AudioFrame> justAddedFrames() {
        return justAdded;
    }

    public List<byte[]> justAdded() {
        List<byte[]> list = new ArrayList<>();
        for (AudioFrame frame : justAdded) {
            list.add(frame.copyBytes());
        }
        return list;
    }

    /** The most recently accepted frame, for incremental decoding. */
    public byte[] lastPacket() {
        return lastFrame != null ? lastFrame.copyBytes() : null;
    }

    /** The Opus packets captured so far, oldest first. */
    public List<byte[]> packets() {
        List<byte[]> list = new ArrayList<>();
        for (AudioFrame frame : packets) {
            list.add(frame.copyBytes());
        }
        return list;
    }

    public int unknownSizeCount() {
        return unknownSizeCount;
    }

    /** The distinct payload sizes seen this utterance. */
    public java.util.Set<Integer> observedSizes() {
        return observedSizes;
    }

    /** Counts code:109 messages whose field 5 could not be read at all. */
    public int rejectedCount() {
        return rejected;
    }

    private int rejected;

    /** Pulls field 5 out of the StMessage envelope, or null if absent. */
    private byte[] extractAudio(byte[] relayBody) {
        try {
            Map<Integer, List<PbValue>> fields = Pb.parse(relayBody);
            if (!structureLogged) {
                structureLogged = true;
                StringBuilder sb = new StringBuilder("code:109 envelope fields:");
                for (Map.Entry<Integer, List<PbValue>> e : fields.entrySet()) {
                    PbValue v = e.getValue().get(0);
                    sb.append(' ').append(e.getKey()).append('=')
                      .append(v.isVarint() ? "varint" : (v.asBytes().length + "B"));
                }
                LogBus.log(sb.toString());
            }
            byte[] audio = Pb.firstBytes(fields, FIELD_AUDIO, null);
            return (audio != null && audio.length > 0) ? audio : null;
        } catch (Exception e) {
            // Inbound radio data; never let a malformed frame propagate.
            return null;
        }
    }

    /** Dumped once per process, purely to confirm where the audio actually is. */
    private boolean structureLogged;

    private static boolean isKnownSize(int length) {
        for (int size : KNOWN_PACKET_SIZES) {
            if (size == length) return true;
        }
        return false;
    }
}
