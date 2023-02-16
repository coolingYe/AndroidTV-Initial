package com.zeewain.base.utils;

import android.app.Activity;
import android.app.Application;
import android.util.DisplayMetrics;

import javax.security.auth.Destroyable;

public class DensityUtils {
    private final static float WIDTH = 960.f;
    private final static float HEIGHT = 540.f;

    public static void autoWidth(Application application, Activity activity) {
        DisplayMetrics displayMetrics = application.getResources().getDisplayMetrics();

        float targetDensity = displayMetrics.widthPixels / WIDTH;
        int targetDensityDpi = (int) (targetDensity * 160);

        displayMetrics.density = targetDensity;
        displayMetrics.scaledDensity = targetDensity;
        displayMetrics.densityDpi = targetDensityDpi;

        DisplayMetrics activityDisplayMetrics = activity.getResources().getDisplayMetrics();
        activityDisplayMetrics.density = targetDensity;
        activityDisplayMetrics.scaledDensity = targetDensity;
        activityDisplayMetrics.densityDpi = targetDensityDpi;
    }

    public static void autoHeight(Application application, Activity activity) {
        DisplayMetrics displayMetrics = application.getResources().getDisplayMetrics();
        float targetDensity = displayMetrics.heightPixels / HEIGHT;
        int targetDensityDpi = (int) (targetDensity * 160);

        displayMetrics.density = targetDensity;
        displayMetrics.scaledDensity = targetDensity;
        displayMetrics.densityDpi = targetDensityDpi;

        DisplayMetrics activityDisplayMetrics = activity.getResources().getDisplayMetrics();
        activityDisplayMetrics.density = targetDensity;
        activityDisplayMetrics.scaledDensity = targetDensity;
        activityDisplayMetrics.densityDpi = targetDensityDpi;
    }
}