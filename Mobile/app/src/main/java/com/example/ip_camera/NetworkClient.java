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

class NetworkClient {
    private static final String TAG = "NetworkClient";
    private static final long COMMAND_POLL_INTERVAL_MS = 500;

    private final String cmdPollUrl;
    private final String stateUrl;
    private final OkHttpClient client;

    private Handler commandPollHandler;
    private volatile boolean running;
    private CommandListener commandListener;

    interface CommandListener {
        void onCommand(String action);
    }

    void setCommandListener(CommandListener listener) {
        this.commandListener = listener;
    }

    NetworkClient(String serverUrl) {
        cmdPollUrl = serverUrl.replace("/upload", "/command");
        stateUrl = serverUrl.replace("/upload", "/state");
        client = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .build();
    }

    void start() {
        running = true;
        commandPollHandler = new Handler(Looper.getMainLooper());
        startPollingCommands();
    }

    void stop() {
        running = false;
        if (commandPollHandler != null) {
            commandPollHandler.removeCallbacksAndMessages(null);
            commandPollHandler = null;
        }
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
