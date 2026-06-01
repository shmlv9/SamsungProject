package com.example.ip_camera;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.google.mlkit.vision.segmentation.Segmentation;
import com.google.mlkit.vision.segmentation.SegmentationMask;
import com.google.mlkit.vision.segmentation.Segmenter;
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions;

import java.nio.ByteBuffer;

class FrameProcessor {
    private static final String TAG = "FrameProcessor";
    private static final int FACE_DETECTION_MAX_DIM = 960;
    private static final int CENTER_LOCK_FRAME_INTERVAL = 2;

    private Segmenter segmenter;
    private FaceDetector faceDetector;
    private final Paint filterPaint = new Paint();
    private Rect lastCenterCropRect = null;
    private int centerLockFrameCounter = 0;
    private ProcessedFrameListener processedFrameListener;

    interface ProcessedFrameListener {
        void onFrame(Bitmap bitmap);
    }

    void setProcessedFrameListener(ProcessedFrameListener listener) {
        this.processedFrameListener = listener;
    }

    Bitmap processBitmap(Bitmap bitmap, int filter, boolean bgBlur, boolean centerLock,
                         int cameraRotation, boolean mirrorPreview, int rotationDegrees) {
        if (bgBlur) {
            Bitmap blurred = applyBgBlur(bitmap);
            if (blurred != bitmap) {
                bitmap.recycle();
                bitmap = blurred;
            }
        }

        if (filter != FilterConstants.NONE) {
            Bitmap filtered = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), bitmap.getConfig());
            Canvas canvas = new Canvas(filtered);
            filterPaint.setColorFilter(new ColorMatrixColorFilter(FilterConstants.getColorMatrix(filter)));
            canvas.drawBitmap(bitmap, 0, 0, filterPaint);
            bitmap.recycle();
            bitmap = filtered;
        }

        if (centerLock) {
            int preCropW = bitmap.getWidth();
            int preCropH = bitmap.getHeight();
            Bitmap cropped = applyCenterLock(bitmap);
            if (cropped != bitmap) {
                Bitmap scaled = Bitmap.createScaledBitmap(cropped, preCropW, preCropH, true);
                cropped.recycle();
                bitmap.recycle();
                bitmap = scaled;
            }
        }

        if ((bgBlur || centerLock) && processedFrameListener != null) {
            Bitmap preview = bitmap;
            if (cameraRotation != 0) {
                Matrix rot = new Matrix();
                rot.postRotate(cameraRotation);
                preview = Bitmap.createBitmap(preview, 0, 0, preview.getWidth(), preview.getHeight(), rot, true);
            }
            if (mirrorPreview) {
                Matrix mirror = new Matrix();
                mirror.preScale(-1, 1);
                Bitmap mirrored = Bitmap.createBitmap(preview, 0, 0, preview.getWidth(), preview.getHeight(), mirror, false);
                if (preview != bitmap) preview.recycle();
                preview = mirrored;
            }
            Bitmap previewCopy = preview.copy(Bitmap.Config.ARGB_8888, false);
            if (preview != bitmap) preview.recycle();
            new Handler(Looper.getMainLooper()).post(() -> processedFrameListener.onFrame(previewCopy));
        }

        if (rotationDegrees != 0) {
            Matrix matrix = new Matrix();
            matrix.postRotate(-rotationDegrees);
            Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0,
                    bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap.recycle();
            bitmap = rotated;
        }

        return bitmap;
    }

    private Bitmap applyBgBlur(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        try {
            InputImage inputImage = InputImage.fromBitmap(original, 0);
            SegmentationMask mask = Tasks.await(getSegmenter().process(inputImage));
            ByteBuffer maskBuffer = mask.getBuffer();
            maskBuffer.rewind();
            int maskWidth = mask.getWidth();
            int maskHeight = mask.getHeight();

            Bitmap blurred = scaleBitmapPass(original, width, height);
            int[] originalPixels = new int[width * height];
            int[] blurredPixels = new int[width * height];
            original.getPixels(originalPixels, 0, width, 0, 0, width, height);
            blurred.getPixels(blurredPixels, 0, width, 0, 0, width, height);
            blurred.recycle();

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int mx = x * maskWidth / width;
                    int my = y * maskHeight / height;
                    float confidence = maskBuffer.getFloat((my * maskWidth + mx) * 4);

                    int idx = y * width + x;
                    int bp = blurredPixels[idx];
                    int op = originalPixels[idx];

                    if (confidence > 0.5f) {
                        blurredPixels[idx] = op;
                        continue;
                    }

                    float bgStrength = Math.max(0, 1f - confidence * 2);
                    int a = (int)((op >> 24 & 0xFF) * (1 - bgStrength) + (bp >> 24 & 0xFF) * bgStrength);
                    int r = (int)((op >> 16 & 0xFF) * (1 - bgStrength) + (bp >> 16 & 0xFF) * bgStrength);
                    int g = (int)((op >> 8 & 0xFF) * (1 - bgStrength) + (bp >> 8 & 0xFF) * bgStrength);
                    int b = (int)((op & 0xFF) * (1 - bgStrength) + (bp & 0xFF) * bgStrength);
                    blurredPixels[idx] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }

            return Bitmap.createBitmap(blurredPixels, width, height, Bitmap.Config.ARGB_8888);
        } catch (Exception e) {
            Log.e(TAG, "BgBlur failed: " + e.getMessage());
            return original;
        }
    }

    private static Bitmap scaleBitmapPass(Bitmap src, int width, int height) {
        Bitmap tmp = Bitmap.createScaledBitmap(src, width / 2, height / 2, true);
        Bitmap out = Bitmap.createScaledBitmap(tmp, width / 4, height / 4, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width / 2, height / 2, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width, height, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width / 2, height / 2, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width / 4, height / 4, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width / 2, height / 2, true);
        tmp.recycle();
        tmp = out;
        out = Bitmap.createScaledBitmap(tmp, width, height, true);
        tmp.recycle();
        return out;
    }

    private Bitmap applyCenterLock(Bitmap bitmap) {
        try {
            int origW = bitmap.getWidth();
            int origH = bitmap.getHeight();
            Rect cropRect = null;

            centerLockFrameCounter++;
            if (centerLockFrameCounter % CENTER_LOCK_FRAME_INTERVAL == 0 || lastCenterCropRect == null) {
                float scale = Math.min(1f, (float) FACE_DETECTION_MAX_DIM / Math.max(origW, origH));
                Bitmap smallBitmap = bitmap;
                if (scale < 1f) {
                    smallBitmap = Bitmap.createScaledBitmap(bitmap,
                            Math.round(origW * scale), Math.round(origH * scale), true);
                }
                java.util.List<Face> faces = Tasks.await(
                        getFaceDetector().process(InputImage.fromBitmap(smallBitmap, 0)));
                if (smallBitmap != bitmap) smallBitmap.recycle();

                if (!faces.isEmpty()) {
                    Rect box = faces.get(0).getBoundingBox();
                    if (scale < 1f) {
                        box = new Rect(
                                Math.round(box.left / scale), Math.round(box.top / scale),
                                Math.round(box.right / scale), Math.round(box.bottom / scale));
                    }
                    int faceSize = Math.max(box.width(), box.height());
                    float origAspect = (float) origW / origH;
                    float baseSize = faceSize * 1.8f;

                    int cropW = origAspect >= 1f
                            ? Math.round(baseSize * origAspect) : Math.round(baseSize);
                    int cropH = origAspect >= 1f
                            ? Math.round(baseSize) : Math.round(baseSize / origAspect);
                    cropW = Math.min(cropW, origW);
                    cropH = Math.min(cropH, origH);

                    int left = Math.max(0, Math.min(box.centerX() - cropW / 2, origW - cropW));
                    int top = Math.max(0, Math.min(box.centerY() - cropH / 2, origH - cropH));
                    cropRect = new Rect(left, top, left + cropW, top + cropH);
                    lastCenterCropRect = cropRect;
                }
            }

            if (cropRect == null) {
                if (lastCenterCropRect != null && lastCenterCropRect.left >= 0
                        && lastCenterCropRect.top >= 0
                        && lastCenterCropRect.right <= origW
                        && lastCenterCropRect.bottom <= origH) {
                    cropRect = lastCenterCropRect;
                } else {
                    return bitmap;
                }
            }
            return Bitmap.createBitmap(bitmap, cropRect.left, cropRect.top,
                    cropRect.width(), cropRect.height());
        } catch (Exception e) {
            Log.e(TAG, "CenterLock failed: " + e.getMessage());
            return bitmap;
        }
    }

    private Segmenter getSegmenter() {
        if (segmenter == null) {
            SelfieSegmenterOptions options = new SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
                    .build();
            segmenter = Segmentation.getClient(options);
        }
        return segmenter;
    }

    private FaceDetector getFaceDetector() {
        if (faceDetector == null) {
            FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                    .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                    .build();
            faceDetector = FaceDetection.getClient(options);
        }
        return faceDetector;
    }

    void close() {
        if (segmenter != null) {
            segmenter.close();
            segmenter = null;
        }
        if (faceDetector != null) {
            faceDetector.close();
            faceDetector = null;
        }
    }
}
