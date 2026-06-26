package com.taxilink.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public class PermissionHelper {
    public static final int REQUEST_PERMISSIONS = 34;

    public static void requestNeededPermissions(Activity activity) {
        if (Build.VERSION.SDK_INT < 23) return;
        List<String> permissions = new ArrayList<>();
        addIfMissing(activity, permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(activity, permissions, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(activity, permissions, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) addIfMissing(activity, permissions, Manifest.permission.POST_NOTIFICATIONS);
        if (!permissions.isEmpty()) activity.requestPermissions(permissions.toArray(new String[0]), REQUEST_PERMISSIONS);
    }

    public static boolean hasAudio(Activity activity) {
        return Build.VERSION.SDK_INT < 23 || activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasLocation(Activity activity) {
        return Build.VERSION.SDK_INT < 23 || activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static void addIfMissing(Activity activity, List<String> permissions, String permission) {
        if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) permissions.add(permission);
    }
}
