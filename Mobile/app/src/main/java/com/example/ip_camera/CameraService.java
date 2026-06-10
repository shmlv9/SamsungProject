package com.example.ip_camera;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.net.URI;

public class CameraService implements ImageAnalysis.Analyzer {

    private static final String TAG = "CameraService";
    private static final long MIN_INTERVAL_MS = 50;
    private static final int UDP_PORT = 8001;

    private final NetworkClient networkClient;
    private final FrameProcessor frameProcessor;
    private final UdpVideoSender udpSender = new UdpVideoSender();
    private final ByteArrayOutputStream jpegBaos = new ByteArrayOutputStream();
    private final String serverUrl;
    private long lastUploadMs = 0;
    private int cameraRotation = 0;
    private byte[] nv21Buffer;
    private int[] reusablePixels;

    private volatile int rotationDegrees = 0;
    private volatile int currentFilter = FilterConstants.NONE;
    private volatile boolean bgBlurEnabled = false;
    private volatile boolean centerLockEnabled = false;
    private volatile boolean mirrorPreview = false;

    public CameraService(String serverUrl) {
        this.serverUrl = serverUrl;
        networkClient = new NetworkClient(serverUrl);
        frameProcessor = new FrameProcessor();
    }

    public void setProcessedFrameListener(FrameProcessor.ProcessedFrameListener listener) {
        frameProcessor.setProcessedFrameListener(listener);
    }

    public void setCommandListener(NetworkClient.CommandListener listener) {
        networkClient.setCommandListener(listener);
    }

    public void setRotation(int degrees) {
        rotationDegrees = degrees % 360;
    }

    public void setFilter(int filter) {
        currentFilter = filter;
    }

    public void setBgBlur(boolean enabled) {
        bgBlurEnabled = enabled;
    }

    public boolean isBgBlurEnabled() {
        return bgBlurEnabled;
    }

    public void setCenterLock(boolean enabled) {
        centerLockEnabled = enabled;
    }

    public boolean isCenterLockEnabled() {
        return centerLockEnabled;
    }

    public void setMirrorPreview(boolean mirror) {
        mirrorPreview = mirror;
    }

    public void setBackgroundBitmap(Bitmap bitmap) {
        frameProcessor.setBackgroundBitmap(bitmap);
    }

    public void sendState(String jsonState) {
        networkClient.sendState(jsonState);
    }

    public void start() {
        networkClient.start();
        String host = extractHost(serverUrl);
        try {
            udpSender.start(host, UDP_PORT);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start UDP sender", e);
        }
    }

    public void stop() {
        networkClient.stop();
        udpSender.stop();
        frameProcessor.close();
    }

    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getFormat() != ImageFormat.YUV_420_888) {
            imageProxy.close();
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastUploadMs < MIN_INTERVAL_MS) {
            imageProxy.close();
            return;
        }

        ImageProxy.PlaneProxy[] planes = imageProxy.getPlanes();
        int width = imageProxy.getWidth();
        int height = imageProxy.getHeight();
        cameraRotation = imageProxy.getImageInfo().getRotationDegrees();

        nv21Buffer = ImageUtils.yuv420ToNv21(planes, width, height, nv21Buffer);
        imageProxy.close();
        if (nv21Buffer == null) return;

        boolean needsProcessing = rotationDegrees != 0
                || currentFilter != FilterConstants.NONE
                || bgBlurEnabled
                || centerLockEnabled
                || cameraRotation != 0;

        byte[] jpegBytes;

        if (needsProcessing) {
            int frameSize = width * height;
            if (reusablePixels == null || reusablePixels.length < frameSize) {
                reusablePixels = new int[frameSize];
            }
            Bitmap bitmap = ImageUtils.nv21ToBitmap(nv21Buffer, width, height, reusablePixels);
            bitmap = frameProcessor.processBitmap(bitmap, currentFilter,
                    bgBlurEnabled, centerLockEnabled, cameraRotation,
                    mirrorPreview, rotationDegrees);
            jpegBytes = ImageUtils.bitmapToJpeg(bitmap, jpegBaos);
            bitmap.recycle();
        } else {
            jpegBytes = ImageUtils.yuvToJpeg(nv21Buffer, width, height, jpegBaos);
        }

        if (jpegBytes != null) {
            lastUploadMs = now;
            udpSender.sendFrame(jpegBytes);
        }
    }

    private static String extractHost(String url) {
        try {
            URI uri = new URI(url.replace("/upload", ""));
            return uri.getHost();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }
}
