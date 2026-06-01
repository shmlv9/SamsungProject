package com.example.ip_camera;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static final long COMMAND_POLL_INTERVAL_MS = 500;
    private static final long MAX_RECONNECT_DELAY_MS = 30000;

    private final String httpUrl;
    private final String cmdPollUrl;
    private final String stateUrl;
    private final String wsUrl;
    private final OkHttpClient client;

    private volatile WebSocket ws;
    private volatile boolean wsConnected;
    private Handler reconnectHandler;
    private Handler commandPollHandler;
    private boolean running;
    private long reconnectDelayMs = 1000;
    private CommandListener commandListener;

    interface CommandListener {
        void onCommand(String action);
    }

    void setCommandListener(CommandListener listener) {
        this.commandListener = listener;
    }

    NetworkClient(String serverUrl) {
        this.httpUrl = serverUrl;
        this.wsUrl = serverUrl
                .replace("http://", "ws://")
                .replace("https://", "wss://")
                .replace("/upload", "/ws/upload");
        this.cmdPollUrl = serverUrl.replace("/upload", "/command");
        this.stateUrl = serverUrl.replace("/upload", "/state");
        client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build();
    }

    void start() {
        running = true;
        reconnectHandler = new Handler(Looper.getMainLooper());
        commandPollHandler = new Handler(Looper.getMainLooper());
        connectWs();
        startPollingCommands();
    }

    void stop() {
        running = false;
        if (reconnectHandler != null) {
            reconnectHandler.removeCallbacksAndMessages(null);
            reconnectHandler = null;
        }
        if (commandPollHandler != null) {
            commandPollHandler.removeCallbacksAndMessages(null);
            commandPollHandler = null;
        }
        if (ws != null) {
            ws.close(1000, "Client closed");
            ws = null;
        }
        wsConnected = false;
    }

    void sendFrame(byte[] jpegBytes) {
        if (wsConnected && ws != null) {
            ws.send(ByteString.of(jpegBytes));
            return;
        }

        Request request = new Request.Builder()
                .url(httpUrl)
                .post(RequestBody.create(jpegBytes, MediaType.parse("image/jpeg")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "HTTP fallback failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                response.close();
            }
        });
    }

    void sendState(String jsonState) {
        Request request = new Request.Builder()
                .url(stateUrl)
                .post(RequestBody.create(jsonState, MediaType.parse("application/json")))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.d(TAG, "State update failed: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                response.close();
            }
        });
    }

    private void connectWs() {
        if (!running) return;
        Request request = new Request.Builder().url(wsUrl).build();
        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(@NonNull WebSocket webSocket, @NonNull Response response) {
                ws = webSocket;
                wsConnected = true;
                reconnectDelayMs = 1000;
            }

            @Override
            public void onFailure(@NonNull WebSocket webSocket, @NonNull Throwable t, Response response) {
                wsConnected = false;
                ws = null;
                scheduleReconnect();
            }

            @Override
            public void onClosed(@NonNull WebSocket webSocket, int code, @NonNull String reason) {
                wsConnected = false;
                ws = null;
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (!running) return;
        if (reconnectHandler != null) {
            reconnectHandler.postDelayed(this::connectWs, reconnectDelayMs);
            reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
        }
    }

    private void startPollingCommands() {
        if (!running) return;
        pollCommand();
    }

    private void pollCommand() {
        if (!running || commandListener == null) return;

        Request request = new Request.Builder()
                .url(cmdPollUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try {
                    String body = response.body() != null ? response.body().string() : "";
                    if (!body.isEmpty()) {
                        JSONObject json = new JSONObject(body);
                        String cmd = json.optString("command", null);
                        if (cmd != null && !cmd.equals("null")) {
                            JSONObject cmdJson = new JSONObject(cmd);
                            String action = cmdJson.getString("action");
                            commandListener.onCommand(action);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Poll parse error: " + e.getMessage());
                } finally {
                    response.close();
                    scheduleNextPoll();
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                scheduleNextPoll();
            }
        });
    }

    private void scheduleNextPoll() {
        if (!running || commandPollHandler == null) return;
        commandPollHandler.postDelayed(this::pollCommand, COMMAND_POLL_INTERVAL_MS);
    }
}
