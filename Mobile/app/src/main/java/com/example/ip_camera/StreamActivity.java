package com.example.ip_camera;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StreamActivity extends AppCompatActivity {

    public static final String EXTRA_SERVER_URL = "server_url";

    private PreviewView previewView;
    private TextView streamingText;
    private MaterialButton disconnectBtn;
    private ImageButton btnFlipCamera;
    private ImageButton btnRotate;
    private ImageButton btnSettings;
    private ImageButton btnFilters;
    private ImageButton btnBgd;
    private ImageButton btnCenter;
    private ImageView processedFrame;
    private int currentFilter = FilterConstants.NONE;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private CameraService cameraService;
    private String serverUrl;
    private @CameraSelector.LensFacing int lensFacing = CameraSelector.LENS_FACING_FRONT;
    private int rotationDegrees = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stream);

        previewView = findViewById(R.id.previewView);
        streamingText = findViewById(R.id.streamingText);
        disconnectBtn = findViewById(R.id.disconnectBtn);
        btnFlipCamera = findViewById(R.id.btnFlipCamera);
        btnRotate = findViewById(R.id.btnRotate);
        btnSettings = findViewById(R.id.btnSettings);
        btnFilters = findViewById(R.id.btnFilters);
        btnBgd = findViewById(R.id.btnBgd);
        btnCenter = findViewById(R.id.btnCenter);
        processedFrame = findViewById(R.id.processedFrame);
        previewView.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

        serverUrl = getIntent().getStringExtra(EXTRA_SERVER_URL);
        if (serverUrl == null) {
            finish();
            return;
        }

        String host = serverUrl
                .replace("http://", "")
                .replace("/upload", "");
        streamingText.setText("Видео транслируется на\nкомпьютер с IP:\n" + host);

        cameraExecutor = Executors.newSingleThreadExecutor();
        cameraService = new CameraService(serverUrl);
        cameraService.setMirrorPreview(lensFacing == CameraSelector.LENS_FACING_FRONT);
        cameraService.setProcessedFrameListener(processedFrame::setImageBitmap);

        cameraService.setCommandListener(action -> runOnUiThread(() -> {
            switch (action) {
                case "flip_camera":
                    flipCamera();
                    break;
                case "rotate":
                    rotateFrame();
                    break;
                case "filters": {
                    int next = (currentFilter + 1) % 4;
                    applyFilter(next);
                    break;
                }
                case "filter_none":
                    applyFilter(FilterConstants.NONE);
                    break;
                case "filter_cold":
                    applyFilter(FilterConstants.COLD);
                    break;
                case "filter_warm":
                    applyFilter(FilterConstants.WARM);
                    break;
                case "filter_effect":
                    applyFilter(FilterConstants.EFFECT);
                    break;
                case "bg_blur":
                    applyBgBlur(!cameraService.isBgBlurEnabled(), false);
                    break;
                case "center_lock":
                    applyCenterLock(!cameraService.isCenterLockEnabled(), false);
                    break;
            }
        }));

        btnFlipCamera.setOnClickListener(v -> flipCamera());
        btnRotate.setOnClickListener(v -> rotateFrame());
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
        btnFilters.setOnClickListener(v -> showFilterDialog());
        btnBgd.setOnClickListener(v -> applyBgBlur(!cameraService.isBgBlurEnabled(), true));
        btnCenter.setOnClickListener(v -> applyCenterLock(!cameraService.isCenterLockEnabled(), true));

        disconnectBtn.setOnClickListener(v -> {
            stopStreaming();
            finish();
        });

        getWindow().setStatusBarColor(Color.BLACK);
        cameraService.start();
        startCamera();
        sendFullState();
    }

    private void flipCamera() {
        if (cameraProvider == null) return;

        int newLens = (lensFacing == CameraSelector.LENS_FACING_FRONT)
                ? CameraSelector.LENS_FACING_BACK
                : CameraSelector.LENS_FACING_FRONT;

        CameraSelector newSelector = new CameraSelector.Builder()
                .requireLensFacing(newLens)
                .build();

        try {
            if (!cameraProvider.hasCamera(newSelector)) {
                Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Камера недоступна", Toast.LENGTH_SHORT).show();
            return;
        }

        lensFacing = newLens;
        cameraService.setMirrorPreview(lensFacing == CameraSelector.LENS_FACING_FRONT);
        sendFullState();
        cameraProvider.unbindAll();
        bindCameraUseCases(cameraProvider);
    }

    private void rotateFrame() {
        rotationDegrees = (rotationDegrees + 90) % 360;
        cameraService.setRotation(rotationDegrees);
    }

    private void showFilterDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_filter);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setCanceledOnTouchOutside(true);

        dialog.findViewById(R.id.optionNone).setOnClickListener(v -> {
            applyFilter(FilterConstants.NONE);
            sendFullState();
            dialog.dismiss();
        });
        dialog.findViewById(R.id.optionCold).setOnClickListener(v -> {
            applyFilter(FilterConstants.COLD);
            sendFullState();
            dialog.dismiss();
        });
        dialog.findViewById(R.id.optionWarm).setOnClickListener(v -> {
            applyFilter(FilterConstants.WARM);
            sendFullState();
            dialog.dismiss();
        });
        dialog.findViewById(R.id.optionEffect).setOnClickListener(v -> {
            applyFilter(FilterConstants.EFFECT);
            sendFullState();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void applyFilter(int filter) {
        currentFilter = filter;
        cameraService.setFilter(filter);

        if (filter == FilterConstants.NONE) {
            btnFilters.setBackgroundResource(R.drawable.icon_button_bg);
            previewView.setLayerType(View.LAYER_TYPE_NONE, null);
        } else {
            btnFilters.setBackgroundResource(R.drawable.icon_button_bg_active);
            Paint fp = new Paint();
            fp.setColorFilter(new ColorMatrixColorFilter(FilterConstants.getColorMatrix(filter)));
            previewView.setLayerType(View.LAYER_TYPE_HARDWARE, fp);
        }
    }

    private void applyBgBlur(boolean enabled, boolean sendState) {
        cameraService.setBgBlur(enabled);
        btnBgd.setBackgroundResource(enabled ? R.drawable.icon_button_bg_active : R.drawable.icon_button_bg);
        updatePreviewVisibility();
        if (!enabled && !cameraService.isCenterLockEnabled()) processedFrame.setImageBitmap(null);
        if (sendState) sendFullState();
    }

    private void applyCenterLock(boolean enabled, boolean sendState) {
        cameraService.setCenterLock(enabled);
        btnCenter.setBackgroundResource(enabled ? R.drawable.icon_button_bg_active : R.drawable.icon_button_bg);
        updatePreviewVisibility();
        if (!enabled && !cameraService.isBgBlurEnabled()) processedFrame.setImageBitmap(null);
        if (sendState) sendFullState();
    }

    private void updatePreviewVisibility() {
        boolean showProcessed = cameraService.isBgBlurEnabled() || cameraService.isCenterLockEnabled();
        previewView.setVisibility(showProcessed ? View.INVISIBLE : View.VISIBLE);
        processedFrame.setVisibility(showProcessed ? View.VISIBLE : View.GONE);
    }

    private void sendFullState() {
        String filterName = "none";
        if (currentFilter == FilterConstants.COLD) filterName = "cold";
        else if (currentFilter == FilterConstants.WARM) filterName = "warm";
        else if (currentFilter == FilterConstants.EFFECT) filterName = "effect";

        cameraService.sendState("{\"type\":\"state\",\"mirror\":" + (lensFacing == CameraSelector.LENS_FACING_FRONT)
                + ",\"bg_blur\":" + cameraService.isBgBlurEnabled()
                + ",\"center_lock\":" + cameraService.isCenterLockEnabled()
                + ",\"filter\":\"" + filterName + "\"}");
    }

    private void stopStreaming() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
        if (cameraService != null) {
            cameraService.stop();
            cameraService = null;
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider provider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(cameraExecutor, cameraService);

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        provider.bindToLifecycle(this, cameraSelector, preview, analysis);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraService != null) {
            Bitmap bg = SettingsActivity.hasBackgroundImage(this)
                    ? SettingsActivity.loadBackgroundBitmap(this) : null;
            cameraService.setBackgroundBitmap(bg);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopStreaming();
        cameraExecutor.shutdown();
    }
}
