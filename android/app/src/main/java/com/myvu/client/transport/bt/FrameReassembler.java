package com.myvu.client.transport.bt;

import com.myvu.client.core.BufferPool;
import com.myvu.client.protocol.Pb;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Feed raw stream bytes in; get complete (post-magic, post-PREFIX) frames out. */
public class FrameReassembler {

    /** magic(4) + length(4). */
    private static final int HEADER = 8;

    /**
     * A frame body must carry at least the 2-byte PREFIX.
     */
    private static final int MIN_FRAME = RfcommFraming.PREFIX.length;

    /**
     * Largest frame body we will accept. Real traffic is small -- app actions,
     * nav frames and 240-byte audio packets -- so this is deliberately generous.
     * Anything bigger on the wire means a corrupt length field, not a real frame.
     */
    public static final int MAX_FRAME = 64 * 1024;

    private byte[] buf = BufferPool.obtain(256);
    private int bufLen = 0;

    public List<byte[]> feed(byte[] data) {
        if (data != null && data.length > 0) {
            int needed = bufLen + data.length;
            if (needed > buf.length) {
                byte[] old = buf;
                buf = BufferPool.obtain(needed);
                if (bufLen > 0) {
                    System.arraycopy(old, 0, buf, 0, bufLen);
                }
                BufferPool.recycle(old);
            }
            System.arraycopy(data, 0, buf, bufLen, data.length);
            bufLen = needed;
        }

        List<byte[]> out = new ArrayList<>();
        while (true) {
            int idx = indexOfMagic(buf, bufLen);
            if (idx < 0) {
                // Keep only a possible partial magic straddling the read boundary.
                if (bufLen > RfcommFraming.MAGIC.length) {
                    int keep = RfcommFraming.MAGIC.length;
                    System.arraycopy(buf, bufLen - keep, buf, 0, keep);
                    bufLen = keep;
                }
                break;
            }
            if (idx > 0) {
                int remaining = bufLen - idx;
                System.arraycopy(buf, idx, buf, 0, remaining);
                bufLen = remaining;
            }
            if (bufLen < HEADER) break;

            int length = ByteBuffer.wrap(buf, 4, 4).order(ByteOrder.BIG_ENDIAN).getInt();

            // NEVER trust this length. It is attacker- or (far more likely)
            // corruption-controlled, and every unchecked use of it is a fault:
            //   negative / < 2  -> copyOfRange(from > to) throws, killing the rx thread
            //   near MAX_VALUE  -> HEADER + length overflows to negative, same throw
            //   huge but valid  -> the frame never completes, so buf grows on every
            //                      read until OutOfMemoryError (an Error, which the
            //                      rx loop's catch(Exception) would NOT contain)
            // Bounding it here makes `HEADER + length` overflow-safe and caps how
            // much we can ever retain. A bad length means we matched noise that
            // looked like magic, so resync past it rather than stalling forever.
            if (length < MIN_FRAME || length > MAX_FRAME) {
                int skip = RfcommFraming.MAGIC.length;
                int remaining = bufLen - skip;
                System.arraycopy(buf, skip, buf, 0, remaining);
                bufLen = remaining;
                continue;
            }

            int total = HEADER + length; // safe: length is bounded above
            if (bufLen < total) break; // plausible, just incomplete -- wait

            byte[] frame = new byte[length - MIN_FRAME];
            System.arraycopy(buf, HEADER + MIN_FRAME, frame, 0, frame.length); // strip PREFIX
            out.add(frame);

            int remaining = bufLen - total;
            System.arraycopy(buf, total, buf, 0, remaining);
            bufLen = remaining;
        }
        return out;
    }

    private static int indexOfMagic(byte[] data, int length) {
        byte[] magic = RfcommFraming.MAGIC;
        if (length < magic.length) return -1;
        outer:
        for (int i = 0; i <= length - magic.length; i++) {
            for (int j = 0; j < magic.length; j++) {
                if (data[i + j] != magic[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    public void reset() {
        if (buf != null) {
            BufferPool.recycle(buf);
            buf = BufferPool.obtain(256);
        }
        bufLen = 0;
    }
}
