package com.example;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.ads.MobileAds;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application entry point for Tutor Earn.
 * Initializes the default FirebaseApp and performs single-flight initialization of the
 * official Google Mobile Ads SDK at application startup.
 */
public class TutorEarnApplication extends Application {

    private static final String TAG = "TutorEarnApplication";
    private static final String ADMOB_APP_ID_META_KEY = "com.google.android.gms.ads.APPLICATION_ID";

    private static final Object AD_INIT_LOCK = new Object();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ExecutorService AD_INIT_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "tutorearn-admob-init");
        thread.setDaemon(true);
        return thread;
    });
    private static final List<Runnable> pendingAdInitCallbacks = new ArrayList<>();
    private static boolean mobileAdsInitStarted = false;
    private static boolean mobileAdsInitialized = false;

    @VisibleForTesting
    public interface MobileAdsInitDriver {
        void initialize(@NonNull Context context, @NonNull Runnable onInitialized);
    }

    @VisibleForTesting
    public static final class AdInitHolder {
        @Nullable
        private static MobileAdsInitDriver testDriverOverride;

        private AdInitHolder() {
        }

        @VisibleForTesting
        public static void setDriverOverride(@Nullable MobileAdsInitDriver driver) {
            synchronized (AD_INIT_LOCK) {
                testDriverOverride = driver;
            }
        }

        @NonNull
        static MobileAdsInitDriver resolveDriver() {
            synchronized (AD_INIT_LOCK) {
                if (testDriverOverride != null) {
                    return testDriverOverride;
                }
            }
            return REAL_MOBILE_ADS_INIT_DRIVER;
        }
    }

    private static final MobileAdsInitDriver REAL_MOBILE_ADS_INIT_DRIVER = (context, onInitialized) ->
            AD_INIT_EXECUTOR.execute(() -> {
                try {
                    MobileAds.initialize(context, initializationStatus -> onInitialized.run());
                } catch (Throwable t) {
                    Log.w(TAG, "Google Mobile Ads SDK initialization skipped/failed: " + t.getMessage());
                    onInitialized.run();
                }
            });

    @Override
    public void onCreate() {
        super.onCreate();
        ensureFirebaseInitialized(this);
        ensureMobileAdsInitialized(this, null);
    }

    /**
     * Initializes the Google Mobile Ads SDK at most once per application process.
     * Subsequent calls while initialization is in-flight or completed will never invoke
     * MobileAds.initialize() again, avoiding duplicate initialization.
     */
    public static void ensureMobileAdsInitialized(
            @NonNull Context context,
            @Nullable Runnable onReady
    ) {
        Context appContext = context.getApplicationContext();
        final boolean shouldStartInit;
        final MobileAdsInitDriver driver;

        synchronized (AD_INIT_LOCK) {
            if (mobileAdsInitialized) {
                if (onReady != null) {
                    runOnMainThread(onReady);
                }
                return;
            }
            if (onReady != null) {
                pendingAdInitCallbacks.add(onReady);
            }
            if (mobileAdsInitStarted) {
                return;
            }
            mobileAdsInitStarted = true;
            driver = AdInitHolder.resolveDriver();
        }

        if (driver == REAL_MOBILE_ADS_INIT_DRIVER && !hasConfiguredAdMobAppId(appContext)) {
            Log.w(TAG, "AdMob Application ID meta-data is empty; skipping MobileAds.initialize().");
            completeMobileAdsInitialization();
            return;
        }

        shouldStartInit = true;
        if (shouldStartInit) {
            driver.initialize(appContext, TutorEarnApplication::completeMobileAdsInitialization);
        }
    }

    private static void completeMobileAdsInitialization() {
        List<Runnable> callbacksToRun;
        synchronized (AD_INIT_LOCK) {
            mobileAdsInitialized = true;
            callbacksToRun = new ArrayList<>(pendingAdInitCallbacks);
            pendingAdInitCallbacks.clear();
        }
        for (Runnable callback : callbacksToRun) {
            runOnMainThread(callback);
        }
    }

    private static void runOnMainThread(@NonNull Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            MAIN_HANDLER.post(runnable);
        }
    }

    @VisibleForTesting
    public static boolean isMobileAdsInitialized() {
        synchronized (AD_INIT_LOCK) {
            return mobileAdsInitialized;
        }
    }

    @VisibleForTesting
    public static void resetMobileAdsForTest() {
        synchronized (AD_INIT_LOCK) {
            mobileAdsInitStarted = false;
            mobileAdsInitialized = false;
            pendingAdInitCallbacks.clear();
        }
    }

    public static boolean hasConfiguredAdMobAppId(@NonNull Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            Bundle metaData = appInfo.metaData;
            if (metaData == null) {
                return false;
            }
            String appId = metaData.getString(ADMOB_APP_ID_META_KEY);
            return appId != null && !appId.trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    @NonNull
    public static synchronized FirebaseApp ensureFirebaseInitialized(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        if (!FirebaseApp.getApps(appContext).isEmpty()) {
            return FirebaseApp.getInstance();
        }
        FirebaseOptions options = buildFirebaseOptions(appContext);
        return FirebaseApp.initializeApp(appContext, options);
    }

    @NonNull
    public static FirebaseOptions buildFirebaseOptions(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        return new FirebaseOptions.Builder()
                .setProjectId(appContext.getString(R.string.tutor_firebase_project_id))
                .setGcmSenderId(appContext.getString(R.string.tutor_firebase_project_number))
                .setDatabaseUrl(appContext.getString(R.string.tutor_firebase_database_url))
                .setStorageBucket(appContext.getString(R.string.tutor_firebase_storage_bucket))
                .setApplicationId(appContext.getString(R.string.tutor_firebase_app_id))
                .setApiKey(appContext.getString(R.string.tutor_firebase_api_key))
                .build();
    }
}
