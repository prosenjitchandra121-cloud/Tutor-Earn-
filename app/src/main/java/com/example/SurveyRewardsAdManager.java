package com.example;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;

/**
 * Manages official Google Mobile Ads Rewarded Ads exclusively for the Survey Rewards screen.
 * - Uses BuildConfig.ADMOB_SURVEY_REWARDS_AD_UNIT_ID (ca-app-pub-8972616814126419/5563489220).
 * - Never uses Interstitial Ads or simulated ads.
 * - Verifies completion strictly via RewardedAd.OnUserEarnedRewardListener.
 */
public class SurveyRewardsAdManager {

    private static final String TAG = "SurveyRewardsAdManager";
    private static final long REWARDED_LOAD_TIMEOUT_MS = 12_000L;

    public interface RewardedAdOutcomeListener {
        void onLoadingStarted();

        void onRewardVerified();

        void onRewardNotEarned();

        void onAdUnavailableOrFailed(@NonNull String reason);
    }

    @VisibleForTesting
    public interface LoadedRewardedHandle {
        void setFullScreenContentCallback(@Nullable FullScreenContentCallback callback);

        void show(@NonNull Activity activity, @NonNull Runnable onUserEarnedReward);
    }

    @VisibleForTesting
    public interface RewardedLoadCallback {
        void onLoaded(@NonNull LoadedRewardedHandle handle);

        void onFailed(@NonNull String message, int code);
    }

    @VisibleForTesting
    public interface RewardedDriver {
        void loadRewarded(
                @NonNull Context context,
                @NonNull String adUnitId,
                @NonNull RewardedLoadCallback callback
        );
    }

    @VisibleForTesting
    public static final class Holder {
        @Nullable
        private static RewardedDriver testDriverOverride;

        private Holder() {
        }

        @VisibleForTesting
        public static void setRewardedDriverOverride(@Nullable RewardedDriver driver) {
            testDriverOverride = driver;
        }

        @NonNull
        static RewardedDriver resolveDriver() {
            if (testDriverOverride != null) {
                return testDriverOverride;
            }
            return REAL_REWARDED_DRIVER;
        }
    }

    private static final RewardedDriver REAL_REWARDED_DRIVER = (context, adUnitId, callback) -> {
        try {
            AdRequest adRequest = new AdRequest.Builder().build();
            RewardedAd.load(
                    context,
                    adUnitId,
                    adRequest,
                    new RewardedAdLoadCallback() {
                        @Override
                        public void onAdLoaded(@NonNull RewardedAd ad) {
                            callback.onLoaded(new LoadedRewardedHandle() {
                                @Override
                                public void setFullScreenContentCallback(
                                        @Nullable FullScreenContentCallback fullScreenCallback
                                ) {
                                    ad.setFullScreenContentCallback(fullScreenCallback);
                                }

                                @Override
                                public void show(
                                        @NonNull Activity activity,
                                        @NonNull Runnable onUserEarnedReward
                                ) {
                                    ad.show(activity, rewardItem -> onUserEarnedReward.run());
                                }
                            });
                        }

                        @Override
                        public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                            callback.onFailed(loadAdError.getMessage(), loadAdError.getCode());
                        }
                    }
            );
        } catch (Throwable t) {
            String msg = t.getMessage() != null ? t.getMessage() : "Rewarded ad load error";
            callback.onFailed(msg, -1);
        }
    };

    private final Context appContext;
    private final RewardedDriver rewardedDriver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean initRequested = false;
    private boolean isDestroyed = false;

    @Nullable
    private LoadedRewardedHandle preloadedRewarded;
    @Nullable
    private LoadedRewardedHandle activeRewarded;
    private boolean isLoadingRewarded = false;
    private boolean isShowingRewarded = false;
    private long currentLoadToken = 0L;

    @Nullable
    private WeakReference<Activity> pendingActivityRef;
    @Nullable
    private RewardedAdOutcomeListener pendingListener;

    private final Runnable loadTimeoutRunnable = () -> {
        if (isDestroyed || pendingListener == null || isShowingRewarded) {
            return;
        }
        currentLoadToken++;
        isLoadingRewarded = false;
        RewardedAdOutcomeListener listener = pendingListener;
        clearPendingRequest();
        listener.onAdUnavailableOrFailed("Rewarded advertisement request timed out.");
    };

    public SurveyRewardsAdManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.rewardedDriver = Holder.resolveDriver();
    }

    @NonNull
    public String getRewardedAdUnitId() {
        String configured = BuildConfig.ADMOB_SURVEY_REWARDS_AD_UNIT_ID;
        return configured != null ? configured.trim() : "";
    }

    public boolean isUsingDistinctRewardedUnit() {
        String rewardedId = getRewardedAdUnitId();
        String interstitialId = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID != null
                ? BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID.trim()
                : "";
        return !rewardedId.isEmpty() && !rewardedId.equals(interstitialId);
    }

    public void initializeAndPreload() {
        if (isDestroyed || !isUsingDistinctRewardedUnit()) {
            return;
        }
        if (!initRequested) {
            initRequested = true;
            TutorEarnApplication.ensureMobileAdsInitialized(appContext, () -> {
                if (!isDestroyed) {
                    preloadRewardedAd();
                }
            });
        } else {
            preloadRewardedAd();
        }
    }

    public void preloadRewardedAd() {
        if (isDestroyed || !isUsingDistinctRewardedUnit() || preloadedRewarded != null || isLoadingRewarded) {
            return;
        }
        loadRewardedInternal();
    }

    public void loadAndShowRewardedAd(
            @NonNull Activity activity,
            @NonNull RewardedAdOutcomeListener listener
    ) {
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed()) {
            listener.onAdUnavailableOrFailed("Activity is no longer active.");
            return;
        }
        if (isShowingRewarded || pendingListener != null) {
            return;
        }

        if (!isUsingDistinctRewardedUnit()) {
            listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID is unavailable or invalid.");
            return;
        }

        if (preloadedRewarded != null) {
            LoadedRewardedHandle readyAd = preloadedRewarded;
            preloadedRewarded = null;
            presentRewardedAd(activity, readyAd, listener);
            return;
        }

        pendingActivityRef = new WeakReference<>(activity);
        pendingListener = listener;
        listener.onLoadingStarted();
        scheduleLoadTimeout();

        if (!isLoadingRewarded) {
            loadRewardedInternal();
        }
    }

    private void loadRewardedInternal() {
        if (isDestroyed || isLoadingRewarded) {
            return;
        }
        String adUnitId = getRewardedAdUnitId();
        if (!isUsingDistinctRewardedUnit()) {
            if (pendingListener != null) {
                RewardedAdOutcomeListener listener = pendingListener;
                clearPendingRequest();
                listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID is unavailable.");
            }
            return;
        }

        isLoadingRewarded = true;
        final long loadToken = ++currentLoadToken;
        rewardedDriver.loadRewarded(appContext, adUnitId, new RewardedLoadCallback() {
            @Override
            public void onLoaded(@NonNull LoadedRewardedHandle handle) {
                mainHandler.post(() -> handleRewardedLoaded(loadToken, handle));
            }

            @Override
            public void onFailed(@NonNull String message, int code) {
                mainHandler.post(() -> handleRewardedLoadFailed(loadToken, message));
            }
        });
    }

    private void handleRewardedLoaded(long loadToken, @NonNull LoadedRewardedHandle handle) {
        if (loadToken != currentLoadToken) {
            handle.setFullScreenContentCallback(null);
            return;
        }
        isLoadingRewarded = false;
        mainHandler.removeCallbacks(loadTimeoutRunnable);

        if (isDestroyed) {
            handle.setFullScreenContentCallback(null);
            return;
        }

        RewardedAdOutcomeListener listener = pendingListener;
        Activity activity = pendingActivityRef != null ? pendingActivityRef.get() : null;
        clearPendingRequest();

        if (listener == null || activity == null || activity.isFinishing() || activity.isDestroyed() || isShowingRewarded) {
            if (preloadedRewarded == null) {
                preloadedRewarded = handle;
            }
            return;
        }

        presentRewardedAd(activity, handle, listener);
    }

    private void handleRewardedLoadFailed(long loadToken, @NonNull String message) {
        if (loadToken != currentLoadToken) {
            return;
        }
        isLoadingRewarded = false;
        preloadedRewarded = null;
        mainHandler.removeCallbacks(loadTimeoutRunnable);
        Log.w(TAG, "Rewarded ad failed to load: " + message);

        if (isDestroyed) {
            clearPendingRequest();
            return;
        }

        RewardedAdOutcomeListener listener = pendingListener;
        clearPendingRequest();
        if (listener != null) {
            listener.onAdUnavailableOrFailed(message);
        }
    }

    private void presentRewardedAd(
            @NonNull Activity activity,
            @NonNull LoadedRewardedHandle adToShow,
            @NonNull RewardedAdOutcomeListener listener
    ) {
        mainHandler.removeCallbacks(loadTimeoutRunnable);
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed() || isShowingRewarded) {
            clearPendingRequest();
            listener.onAdUnavailableOrFailed("Unable to display rewarded advertisement.");
            return;
        }

        final boolean[] rewardEarned = {false};
        activeRewarded = adToShow;
        isShowingRewarded = true;

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                mainHandler.removeCallbacks(loadTimeoutRunnable);
                isShowingRewarded = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeRewarded == adToShow) {
                    activeRewarded = null;
                }
                clearPendingRequest();
                if (isDestroyed) {
                    return;
                }
                preloadRewardedAd();
                if (rewardEarned[0]) {
                    listener.onRewardVerified();
                } else {
                    listener.onRewardNotEarned();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                mainHandler.removeCallbacks(loadTimeoutRunnable);
                isShowingRewarded = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeRewarded == adToShow) {
                    activeRewarded = null;
                }
                clearPendingRequest();
                Log.w(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                if (!isDestroyed) {
                    preloadRewardedAd();
                    listener.onAdUnavailableOrFailed(adError.getMessage());
                }
            }
        });

        try {
            // Coins are granted ONLY when RewardedAd.OnUserEarnedRewardListener confirms completion
            adToShow.show(activity, () -> rewardEarned[0] = true);
        } catch (Throwable t) {
            mainHandler.removeCallbacks(loadTimeoutRunnable);
            isShowingRewarded = false;
            adToShow.setFullScreenContentCallback(null);
            if (activeRewarded == adToShow) {
                activeRewarded = null;
            }
            clearPendingRequest();
            if (!isDestroyed) {
                listener.onAdUnavailableOrFailed(t.getMessage() != null ? t.getMessage() : "Unable to display ad.");
            }
        }
    }

    private void scheduleLoadTimeout() {
        mainHandler.removeCallbacks(loadTimeoutRunnable);
        mainHandler.postDelayed(loadTimeoutRunnable, REWARDED_LOAD_TIMEOUT_MS);
    }

    private void clearPendingRequest() {
        pendingActivityRef = null;
        pendingListener = null;
    }

    public void destroy() {
        isDestroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        clearPendingRequest();
        if (preloadedRewarded != null) {
            preloadedRewarded.setFullScreenContentCallback(null);
            preloadedRewarded = null;
        }
        if (activeRewarded != null) {
            activeRewarded.setFullScreenContentCallback(null);
            activeRewarded = null;
        }
        isLoadingRewarded = false;
        isShowingRewarded = false;
    }
}
