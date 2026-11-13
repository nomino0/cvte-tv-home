package com.family.home;

import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * YouTube-style ambient light from the live HDMI picture.
 *
 * The HDMI video sits on a hardware layer that normal app capture (MediaProjection) sees as black,
 * but the TV's own `screencap` sees it. This TV's ADB needs no key (ro.adb.secure=0), so every few
 * seconds we ask the local adbd for `exec:screencap` and read only ~150 pixels of the preview area
 * from the stream as it goes by: nothing is decoded or stored. If ADB is off, it quietly stops.
 */
final class LiveAmbient {
    interface Listener {
        void onColors(int left, int right);
    }

    static final long PERIOD_MS = 3500;
    static final long IDLE_MS = 10 * 60 * 1000; // no remote presses for 10 min: stop sampling
    static final long BUSY_MS = 2000;           // someone is moving around: don't compete with the UI

    static volatile long lastActivity = System.currentTimeMillis();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread =
            new HandlerThread("live-ambient", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler bg;
    private final Listener listener;
    private volatile Rect area; // the preview, in screen pixels
    private volatile boolean running;
    private int failures;

    LiveAmbient(Listener l) {
        listener = l;
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    void setArea(Rect r) {
        area = r;
    }

    void start() {
        if (running) return;
        running = true;
        failures = 0;
        bg.post(loop);
    }

    void stop() {
        running = false;
        bg.removeCallbacks(loop);
    }

    void quit() {
        stop();
        thread.quitSafely();
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Rect r = area;
            long since = System.currentTimeMillis() - lastActivity;
            boolean idle = since > IDLE_MS, busy = since < BUSY_MS;
            if (busy) {
                if (running) bg.postDelayed(this, BUSY_MS - since + 100);
                return;
            }
            if (r != null && !idle) {
                try {
                    final int[] c = sample(r);
                    failures = 0;
                    if (c != null) main.post(() -> {
                        if (running) listener.onColors(c[0], c[1]);
                    });
                    else android.util.Log.w("LiveAmbient", "no samples in " + r);
                } catch (Exception e) {
                    if (failures == 0) android.util.Log.w("LiveAmbient", "capture failed: " + e);
                    failures++;
                }
            }
            // ADB off or refusing: back off instead of retrying every few seconds.
            long next = failures == 0 ? PERIOD_MS : failures < 3 ? PERIOD_MS * 2 : 60000;
            if (running) bg.postDelayed(this, next);
        }
    };

    // --- ADB wire protocol (just enough for one exec stream) ---

    private static final int A_CNXN = 0x4e584e43, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
            A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_AUTH = 0x48545541;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private final byte[] head = new byte[24];
    private final byte[] payload = new byte[MAX_PAYLOAD];
    private int lastCmd, lastArg0, lastLen;

    private int[] sample(Rect r) throws IOException {
        Sampler sm = new Sampler(r);
        // Lowest CPU priority: the capture always yields to whatever is on screen.
        exec("exec:nice -n 19 screencap", sm);
        return sm.result();
    }

    /** Runs one shell command through the TV's own ADB in the background, retrying while adbd starts. */
    void shell(final String cmd) {
        bg.post(new Runnable() {
            int tries;

            @Override
            public void run() {
                try {
                    exec("exec:" + cmd, null);
                } catch (Exception e) {
                    if (++tries < 6) bg.postDelayed(this, 10000);
                    else android.util.Log.w("LiveAmbient", cmd + " failed: " + e);
                }
            }
        });
    }

    /** Restarts the TV's adbd as root until the next restart, for the commands that need it. */
    void root() {
        bg.post(() -> {
            try {
                exec("root:", null);
            } catch (IOException e) {
                // adbd drops the connection while it restarts
            }
            android.os.SystemClock.sleep(4000);
        });
    }

    /** Opens one stream (exec:..., root:) on the local adbd and feeds its output to sm, or just waits for it to end. */
    private void exec(String service, Sampler sm) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
            s.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(s.getInputStream(), 64 * 1024);
            OutputStream out = s.getOutputStream();

            send(out, A_CNXN, 0x01000000, MAX_PAYLOAD, "host::\0".getBytes(StandardCharsets.US_ASCII));
            read(in);
            if (lastCmd == A_AUTH) throw new IOException("adb wants a key");
            if (lastCmd != A_CNXN) throw new IOException("no adb");

            final int local = 1;
            send(out, A_OPEN, local, 0, (service + "\0").getBytes(StandardCharsets.US_ASCII));
            int remote = 0;
            while (true) {
                read(in);
                if (lastCmd == A_OKAY) {
                    remote = lastArg0;
                } else if (lastCmd == A_WRTE) {
                    boolean done = sm != null && sm.feed(payload, lastLen);
                    send(out, A_OKAY, local, remote, null);
                    if (done) {
                        send(out, A_CLSE, local, remote, null);
                        break;
                    }
                } else if (lastCmd == A_CLSE) {
                    break;
                }
            }
        }
    }

    private static void send(OutputStream out, int cmd, int a0, int a1, byte[] data) throws IOException {
        int len = data == null ? 0 : data.length;
        int sum = 0;
        if (data != null) for (byte b : data) sum += b & 0xFF;
        byte[] h = new byte[24 + len];
        put(h, 0, cmd);
        put(h, 4, a0);
        put(h, 8, a1);
        put(h, 12, len);
        put(h, 16, sum);
        put(h, 20, ~cmd);
        if (len > 0) System.arraycopy(data, 0, h, 24, len);
        out.write(h);
        out.flush();
    }

    private void read(InputStream in) throws IOException {
        fill(in, head, 24);
        lastCmd = get(head, 0);
        lastArg0 = get(head, 4);
        lastLen = get(head, 12);
        if (lastLen < 0 || lastLen > MAX_PAYLOAD) throw new IOException("bad packet");
        fill(in, payload, lastLen);
    }

    private static void fill(InputStream in, byte[] b, int n) throws IOException {
        int off = 0;
        while (off < n) {
            int k = in.read(b, off, n - off);
            if (k < 0) throw new EOFException();
            off += k;
        }
    }

    private static void put(byte[] b, int o, int v) {
        b[o] = (byte) v;
        b[o + 1] = (byte) (v >> 8);
        b[o + 2] = (byte) (v >> 16);
        b[o + 3] = (byte) (v >> 24);
    }

    private static int get(byte[] b, int o) {
        return (b[o] & 0xFF) | (b[o + 1] & 0xFF) << 8 | (b[o + 2] & 0xFF) << 16 | (b[o + 3] & 0xFF) << 24;
    }

    /**
     * Reads screencap's raw RGBA stream as it arrives and keeps a 16x9 grid of pixels inside the
     * preview, averaged into a left and a right colour. Stops reading once the last one is passed.
     */
    private static final class Sampler {
        private static final int COLS = 16, ROWS = 9;
        private final Rect area;
        // The start of the stream: this TV's screencap prints a log line before the image, and its
        // header is 16 bytes (width, height, format, dataspace) where stock Android 9 has 12.
        private final byte[] pre = new byte[512];
        private int preLen;
        private long pos;
        private long[] offsets; // stream offset of each sample, sorted
        private boolean[] isLeft;
        private int next;
        private long lr, lg, lb, ln, rr, rg, rb, rn;

        Sampler(Rect area) {
            this.area = area;
        }

        /** Returns true once every sample has been read (or the stream can't be understood). */
        boolean feed(byte[] b, int len) {
            int i = 0;
            if (offsets == null) {
                while (i < len && preLen < pre.length) pre[preLen++] = b[i++];
                pos = preLen;
                int start = headerStart();
                if (start < 0 || preLen < start + 16) {
                    if (preLen < pre.length) return false; // wait for more bytes
                    offsets = new long[0];
                    isLeft = new boolean[0];
                    return true;
                }
                // Opaque screen: with a 12-byte header, byte 15 is the first pixel's alpha (0xFF).
                int headerSize = (pre[start + 15] & 0xFF) == 0xFF ? 12 : 16;
                plan(get(pre, start), get(pre, start + 4), start + headerSize);
            }
            long end = pos + (len - i);
            long base = pos - i; // stream offset of b[0]
            while (next < offsets.length && offsets[next] + 2 < end) {
                int k = (int) (offsets[next] - base);
                if (k >= 0) {
                    int r = b[k] & 0xFF, g = b[k + 1] & 0xFF, bl = b[k + 2] & 0xFF;
                    if (isLeft[next]) { lr += r; lg += g; lb += bl; ln++; }
                    else { rr += r; rg += g; rb += bl; rn++; }
                }
                next++;
            }
            pos = end;
            return next >= offsets.length;
        }

        /** Where the binary header begins, after any text lines; -1 if a line isn't finished yet. */
        private int headerStart() {
            int s = 0;
            while (s < preLen && pre[s] >= 0x20 && pre[s] < 0x7F) {
                int nl = -1;
                for (int k = s; k < preLen; k++) {
                    if (pre[k] == '\n') {
                        nl = k;
                        break;
                    }
                }
                if (nl < 0) return -1;
                s = nl + 1;
            }
            return s;
        }

        private void plan(int w, int h, int dataStart) {
            if (w <= 0 || h <= 0 || w > 8192 || h > 8192) {
                offsets = new long[0];
                isLeft = new boolean[0];
                return;
            }
            // Screen pixels to capture pixels (the panel can be larger than the 1280x720 UI).
            float sx = w / 1280f, sy = h / 720f;
            int n = COLS * ROWS;
            long[] off = new long[n];
            boolean[] left = new boolean[n];
            Integer[] order = new Integer[n];
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    int idx = row * COLS + col;
                    int x = Math.round((area.left + (col + 0.5f) * area.width() / COLS) * sx);
                    int y = Math.round((area.top + (row + 0.5f) * area.height() / ROWS) * sy);
                    x = Math.max(0, Math.min(w - 1, x));
                    y = Math.max(0, Math.min(h - 1, y));
                    off[idx] = dataStart + ((long) y * w + x) * 4;
                    left[idx] = col < COLS / 2;
                    order[idx] = idx;
                }
            }
            Arrays.sort(order, (a, z) -> Long.compare(off[a], off[z]));
            offsets = new long[n];
            isLeft = new boolean[n];
            for (int k = 0; k < n; k++) {
                offsets[k] = off[order[k]];
                isLeft[k] = left[order[k]];
            }
        }

        int[] result() {
            if (ln == 0 || rn == 0) return null;
            int a = Color.rgb((int) (lr / ln), (int) (lg / ln), (int) (lb / ln));
            int b = Color.rgb((int) (rr / rn), (int) (rg / rn), (int) (rb / rn));
            return new int[] {a, b};
        }
    }
}
