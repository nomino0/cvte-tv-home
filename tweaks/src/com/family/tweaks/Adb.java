package com.family.tweaks;

import android.os.Handler;
import android.os.HandlerThread;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Runs shell commands through the TV's own adbd on 127.0.0.1:5555 (these TVs need no key), on a
 * background thread. Just enough of the ADB wire protocol for one stream at a time.
 */
final class Adb {
    private static final int A_CNXN = 0x4e584e43, A_OPEN = 0x4e45504f, A_OKAY = 0x59414b4f,
            A_CLSE = 0x45534c43, A_WRTE = 0x45545257, A_AUTH = 0x48545541;
    private static final int MAX_PAYLOAD = 256 * 1024;

    private final HandlerThread thread =
            new HandlerThread("adb", android.os.Process.THREAD_PRIORITY_BACKGROUND);
    private final Handler bg;
    private final byte[] head = new byte[24];
    private final byte[] payload = new byte[MAX_PAYLOAD];
    private int lastCmd, lastArg0, lastLen;

    Adb() {
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    /** Runs one shell command, retrying while adbd starts after boot. */
    void shell(final String cmd) {
        bg.post(new Runnable() {
            int tries;

            @Override
            public void run() {
                try {
                    open("exec:" + cmd);
                } catch (Exception e) {
                    if (++tries < 6) bg.postDelayed(this, 10000);
                    else android.util.Log.w("Tweaks", cmd + " failed: " + e);
                }
            }
        });
    }

    /** Restarts adbd as root until the next restart, for the commands that need it. */
    void root() {
        bg.post(() -> {
            try {
                open("root:");
            } catch (IOException e) {
                // adbd drops the connection while it restarts
            }
            android.os.SystemClock.sleep(4000);
        });
    }

    /** Opens one stream on the local adbd and waits for it to end, ignoring its output. */
    private void open(String service) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress("127.0.0.1", 5555), 1500);
            s.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(s.getInputStream(), 16 * 1024);
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
                    send(out, A_OKAY, local, remote, null);
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
}
