package com.example.ip_camera;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import androidx.camera.core.ImageProxy;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public class ImageUtils {
    public static final int JPEG_QUALITY = 85;

    public static byte[] yuv420ToNv21(ImageProxy.PlaneProxy[] planes, int width, int height, byte[] reusableBuffer) {
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        int yRowStride = planes[0].getRowStride();
        int yPixelStride = planes[0].getPixelStride();
        int uRowStride = planes[1].getRowStride();
        int uPixelStride = planes[1].getPixelStride();
        int vRowStride = planes[2].getRowStride();
        int vPixelStride = planes[2].getPixelStride();

        int bufSize = width * height * 3 / 2;
        byte[] nv21 = reusableBuffer;
        if (nv21 == null || nv21.length != bufSize) {
            nv21 = new byte[bufSize];
        }

        int yOffset = 0;
        for (int row = 0; row < height; row++) {
            int yRowStart = row * yRowStride;
            for (int col = 0; col < width; col++) {
                nv21[yOffset++] = yBuffer.get(yRowStart + col * yPixelStride);
            }
        }

        int uvWidth = width / 2;
        int uvHeight = height / 2;
        int uvOffset = width * height;

        for (int row = 0; row < uvHeight; row++) {
            for (int col = 0; col < uvWidth; col++) {
                int vIndex = row * vRowStride + col * vPixelStride;
                int uIndex = row * uRowStride + col * uPixelStride;
                nv21[uvOffset++] = vBuffer.get(vIndex);
                nv21[uvOffset++] = uBuffer.get(uIndex);
            }
        }

        return nv21;
    }

    public static byte[] yuvToJpeg(byte[] nv21, int width, int height, ByteArrayOutputStream baos) {
        baos.reset();
        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), JPEG_QUALITY, baos);
        return baos.toByteArray();
    }

    public static Bitmap nv21ToBitmap(byte[] nv21, int width, int height, int[] reusablePixels) {
        int frameSize = width * height;
        int[] pixels = reusablePixels != null && reusablePixels.length >= frameSize
                ? reusablePixels : new int[frameSize];

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int yIndex = y * width + x;
                int uvIndex = frameSize + (y >> 1) * width + (x >> 1) * 2;
                int Y = nv21[yIndex] & 0xFF;
                int V = nv21[uvIndex] & 0xFF;
                int U = nv21[uvIndex + 1] & 0xFF;

                int r = Y + ((V - 128) * 1436 >> 10);
                int g = Y - ((U - 128) * 352 >> 10) - ((V - 128) * 731 >> 10);
                int b = Y + ((U - 128) * 1815 >> 10);

                r = Math.max(0, Math.min(255, r));
                g = Math.max(0, Math.min(255, g));
                b = Math.max(0, Math.min(255, b));

                pixels[yIndex] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
        }

        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
    }

    public static byte[] bitmapToJpeg(Bitmap bitmap, ByteArrayOutputStream baos) {
        baos.reset();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
        return baos.toByteArray();
    }
}
