package com.example.ip_camera;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

class UdpVideoSender {
    private static final String TAG = "UdpVideoSender";
    private static final int MAX_PACKET = 1400;
    private static final int HEADER_SIZE = 10;
    private static final int PAYLOAD_SIZE = MAX_PACKET - HEADER_SIZE;

    private DatagramSocket socket;
    private InetAddress address;
    private int port;
    private int frameId;
    private boolean active;
    private final byte[] headerBuf = new byte[HEADER_SIZE];

    void start(String host, int port) throws IOException {
        socket = new DatagramSocket();
        address = InetAddress.getByName(host);
        this.port = port;
        active = true;
        Log.d(TAG, "UDP sender started: " + host + ":" + port);
    }

    void stop() {
        active = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    void sendFrame(byte[] jpeg) {
        if (socket == null || socket.isClosed() || !active) return;

        int fid = frameId++;
        int total = (jpeg.length + PAYLOAD_SIZE - 1) / PAYLOAD_SIZE;

        try {
            for (int i = 0; i < total; i++) {
                int off = i * PAYLOAD_SIZE;
                int len = Math.min(PAYLOAD_SIZE, jpeg.length - off);

                ByteBuffer.wrap(headerBuf)
                        .putInt(fid)
                        .putShort((short) total)
                        .putShort((short) i)
                        .putShort((short) len);

                byte[] buf = new byte[HEADER_SIZE + len];
                System.arraycopy(headerBuf, 0, buf, 0, HEADER_SIZE);
                System.arraycopy(jpeg, off, buf, HEADER_SIZE, len);

                socket.send(new DatagramPacket(buf, buf.length, address, port));
            }
        } catch (IOException e) {
            Log.e(TAG, "UDP send failed", e);
        }
    }
}
