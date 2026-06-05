package com.stealthtrojan.app;

import android.util.Log;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

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

        Request request = new Request.Builder()
            .url(SERVER_URL)
            .build();

        ws = client.newWebSocket(request, this);
    }

    public void sendMessage(String message) {
        if (ws != null) {
            ws.send(message);
            Log.d(TAG, "Message sent: " + message);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        Log.d(TAG, "WebSocket connected");
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        Log.d(TAG, "Message received from server: " + text);
    }

    @Override
    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        Log.e(TAG, "WebSocket connection failed", t);
        webSocket.close(1000, "Connection failure");
        connect();
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        Log.d(TAG, "WebSocket closed: " + code + " - " + reason);
    }
}

