package com.stealthtrojan.app;

import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;

public class MqttService {
    private static final String TAG = "MqttService";
    private static final String BROKER_URL = "tcp://192.168.1.100:1883";
    private static final String CLIENT_ID = "android-client-" + System.currentTimeMillis();

    private IMqttClient mqttClient;
    private boolean isConnected = false;

    public MqttService() {
        try {
            mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, null);
        } catch (Exception e) {
            Log.e(TAG, "MQTT client init failed", e);
        }
    }

    public void connect() throws MqttException {
        if (isConnected) return;

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setKeepAliveInterval(60);
        options.setWill("client/lost", "offline".getBytes(), 0, false);
        options.setUserName("admin");
        options.setPassword("password".toCharArray());

        mqttClient.connect(options);
        isConnected = true;
        Log.d(TAG, "MQTT connected");
    }

    public void subscribe(String topic) throws MqttException {
        if (!isConnected) return;
        mqttClient.subscribe(topic, 0);
        Log.d(TAG, "Subscribed to topic: " + topic);
    }

    public void publish(String topic, String message) throws MqttException {
        if (!isConnected) return;
        MqttMessage m = new MqttMessage(message.getBytes());
        m.setQos(0);
        mqttClient.publish(topic, m);
        Log.d(TAG, "Message published to topic: " + topic);
    }

    public void disconnect() throws MqttException {
        if (mqttClient != null && mqttClient.isConnected()) {
            mqttClient.disconnect();
            isConnected = false;
            Log.d(TAG, "MQTT disconnected");
        }
    }
}

