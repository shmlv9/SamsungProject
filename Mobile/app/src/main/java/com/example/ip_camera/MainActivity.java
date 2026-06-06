package com.example.ip_camera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 100;

    private EditText ipInput;
    private MaterialButton connectBtn;
    private ImageButton btnQr;
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .build();
    private boolean pendingConnect = false;
    private String pendingBaseUrl;
    private String pendingServerUrl;

    private final ActivityResultLauncher<Intent> qrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    String url = result.getData().getStringExtra(QrScannerActivity.EXTRA_RESULT);
                    if (url != null) {
                        String hostPort = url.replace("http://", "").replace("https://", "");
                        if (hostPort.contains("/")) {
                            hostPort = hostPort.substring(0, hostPort.indexOf("/"));
                        }
                        Intent intent = new Intent(this, StreamActivity.class);
                        intent.putExtra(StreamActivity.EXTRA_SERVER_URL,
                                "http://" + hostPort + "/upload");
                        startActivity(intent);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ipInput = findViewById(R.id.ipInput);
        connectBtn = findViewById(R.id.connectBtn);
        btnQr = findViewById(R.id.btnQr);

        getWindow().setStatusBarColor(Color.BLACK);
        connectBtn.setOnClickListener(v -> onConnectClick());
        btnQr.setOnClickListener(v ->
                qrLauncher.launch(new Intent(this, QrScannerActivity.class)));
    }

    private void onConnectClick() {
        String input = ipInput.getText().toString().trim();
        if (input.isEmpty()) {
            Toast.makeText(this, "Введите IP адрес и порт", Toast.LENGTH_SHORT).show();
            return;
        }

        String host = input;
        int port = 8000;
        if (input.contains(":")) {
            String[] parts = input.split(":", 2);
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Некорректный порт", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (host.isEmpty()) {
            Toast.makeText(this, "Введите IP адрес", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingBaseUrl = "http://" + host + ":" + port;
        pendingServerUrl = pendingBaseUrl + "/upload";

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            checkServerAndConnect();
        } else {
            pendingConnect = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST);
        }
    }

    private void checkServerAndConnect() {
        Request request = new Request.Builder()
                .url(pendingBaseUrl + "/ping")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                response.close();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> openStreamActivity());
                } else {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "Не удалось подключиться к серверу", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Не удалось подключиться к серверу", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void openStreamActivity() {
        Intent intent = new Intent(this, StreamActivity.class);
        intent.putExtra(StreamActivity.EXTRA_SERVER_URL, pendingServerUrl);
        startActivity(intent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (pendingConnect) {
                    pendingConnect = false;
                    checkServerAndConnect();
                }
            } else {
                Toast.makeText(this, "Требуется разрешение камеры", Toast.LENGTH_LONG).show();
                pendingConnect = false;
            }
        }
    }
}
