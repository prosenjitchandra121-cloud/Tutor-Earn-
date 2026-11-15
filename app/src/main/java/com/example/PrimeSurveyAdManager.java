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
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/**
 * Manages official Google AdMob Interstitial and Rewarded ads for Prime Survey.
 * - Uses BuildConfig AdMob Ad Unit IDs (official Google test IDs in debug, production IDs in release).
 * - Uses strictly separate Ad Unit IDs for Interstitial vs Rewarded formats.
 * - Delegates SDK initialization to TutorEarnApplication's single-flight initializer (no duplicate init).
 * - Preloads the next interstitial ad ahead of scheduled even questions (2, 4, 6, 8, 10, 12, 14).
 * - Blocks advancement while a required interstitial is loading or showing.
 * - Never pretends an ad was displayed when loading or showing fails.
 * - Credits Rewarded Ad rewards ONLY when OnUserEarnedRewardListener confirms completion.
 * - Releases all handlers, listeners, and callbacks cleanly in destroy().
 */
public class PrimeSurveyAdManager {

    private static final String TAG = "PrimeSurveyAdManager";
    private static final long AD_LOAD_TIMEOUT_MS = 12_000L;
    private static final long[] INTERSTITIAL_AUTO_RETRY_DELAYS_MS = {3_000L, 6_000L, 10_000L};
    private static final String COMPACT_AD_UNAVAILABLE_MESSAGE =
            "Advertisement unavailable. Please try again.";

    public interface InterstitialFlowListener {
        void onAdLoading();

        void onAdDismissed();

        void onAdLoadFailed(@NonNull String errorMessage, int attemptCount);
    }

    public interface RewardedAdOutcomeListener {
        void onLoadingStarted();

        void onRewardVerified();

        void onRewardNotEarned();

        void onAdUnavailableOrFailed(@NonNull String reason);
    }

    @VisibleForTesting
    public interface LoadedInterstitialHandle {
        void setFullScreenContentCallback(@Nullable FullScreenContentCallback callback);

        void show(@NonNull Activity activity);
    }

    @VisibleForTesting
    public interface InterstitialLoadCallback {
        void onLoaded(@NonNull LoadedInterstitialHandle handle);

        void onFailed(@NonNull String message, int code);
    }

    @VisibleForTesting
    public interface InterstitialDriver {
        void initialize(@NonNull Context context, @NonNull Runnable onInitialized);

        void loadInterstitial(
                @NonNull Context context,
                @NonNull String adUnitId,
                @NonNull InterstitialLoadCallback callback
        );
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
        private static InterstitialDriver testDriverOverride;
        @Nullable
        private static RewardedDriver testRewardedDriverOverride;

        private Holder() {
        }

        @VisibleForTesting
        public static void setInterstitialDriverOverride(@Nullable InterstitialDriver driver) {
            testDriverOverride = driver;
        }

        @VisibleForTesting
        public static void setRewardedDriverOverride(@Nullable RewardedDriver driver) {
            testRewardedDriverOverride = driver;
        }

        @NonNull
        static InterstitialDriver resolveDriver() {
            if (testDriverOverride != null) {
                return testDriverOverride;
            }
            return REAL_INTERSTITIAL_DRIVER;
        }

        @NonNull
        static RewardedDriver resolveRewardedDriver() {
            if (testRewardedDriverOverride != null) {
                return testRewardedDriverOverride;
            }
            return REAL_REWARDED_DRIVER;
        }
    }

    private static final InterstitialDriver REAL_INTERSTITIAL_DRIVER = new InterstitialDriver() {
        @Override
        public void initialize(@NonNull Context context, @NonNull Runnable onInitialized) {
            TutorEarnApplication.ensureMobileAdsInitialized(context, onInitialized);
        }

        @Override
        public void loadInterstitial(
                @NonNull Context context,
                @NonNull String adUnitId,
                @NonNull InterstitialLoadCallback callback
        ) {
            try {
                AdRequest adRequest = new AdRequest.Builder().build();
                InterstitialAd.load(
                        context,
                        adUnitId,
                        adRequest,
                        new InterstitialAdLoadCallback() {
                            @Override
                            public void onAdLoaded(@NonNull InterstitialAd ad) {
                                callback.onLoaded(new LoadedInterstitialHandle() {
                                    @Override
                                    public void setFullScreenContentCallback(
                                            @Nullable FullScreenContentCallback fullScreenCallback
                                    ) {
                                        ad.setFullScreenContentCallback(fullScreenCallback);
                                    }

                                    @Override
                                    public void show(@NonNull Activity activity) {
                                        ad.show(activity);
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
                String msg = t.getMessage() != null ? t.getMessage() : "Interstitial load error";
                callback.onFailed(msg, -1);
            }
        }
    };

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
    private final InterstitialDriver interstitialDriver;
    private final RewardedDriver rewardedDriver;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Set<Integer> displayedQuestionNumbers = new HashSet<>();

    private boolean initRequested = false;
    private boolean isDestroyed = false;

    @Nullable
    private LoadedInterstitialHandle preloadedInterstitial;
    @Nullable
    private LoadedInterstitialHandle activeInterstitialAd;
    private boolean isLoadingInterstitial = false;
    private boolean isShowingFullScreenAd = false;
    private long currentInterstitialLoadToken = 0L;

    private int pendingQuestionNumber = -1;
    private int lastAttemptedQuestionNumber = -1;
    private int currentQuestionAttemptCount = 0;
    private int interstitialAutoRetryCount = 0;
    @Nullable
    private WeakReference<Activity> pendingActivityRef;
    @Nullable
    private InterstitialFlowListener pendingInterstitialListener;

    @Nullable
    private LoadedRewardedHandle preloadedRewarded;
    @Nullable
    private LoadedRewardedHandle activeRewardedAd;
    private boolean isLoadingRewarded = false;
    private long currentRewardedLoadToken = 0L;
    @Nullable
    private WeakReference<Activity> pendingRewardedActivityRef;
    @Nullable
    private RewardedAdOutcomeListener pendingRewardedListener;

    private final Runnable interstitialTimeoutRunnable = () -> {
        if (isDestroyed || pendingInterstitialListener == null || isShowingFullScreenAd) {
            return;
        }
        currentInterstitialLoadToken++;
        isLoadingInterstitial = false;
        notifyInterstitialAttemptFailed();
    };

    private final Runnable interstitialAutoRetryRunnable = () -> {
        if (isDestroyed || isShowingFullScreenAd) {
            return;
        }
        InterstitialFlowListener listener = pendingInterstitialListener;
        Activity activity = pendingActivityRef != null ? pendingActivityRef.get() : null;
        int questionNumber = pendingQuestionNumber;
        if (listener == null || questionNumber <= 0
                || activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        if (displayedQuestionNumbers.contains(questionNumber)) {
            clearPendingInterstitialRequest();
            listener.onAdDismissed();
            return;
        }
        currentQuestionAttemptCount++;
        if (preloadedInterstitial != null) {
            LoadedInterstitialHandle readyAd = preloadedInterstitial;
            preloadedInterstitial = null;
            presentInterstitial(activity, questionNumber, readyAd, listener);
            return;
        }
        listener.onAdLoading();
        scheduleInterstitialTimeout();
        if (!isLoadingInterstitial) {
            loadInterstitialInternal();
        }
    };

    private final Runnable rewardedTimeoutRunnable = () -> {
        if (isDestroyed || pendingRewardedListener == null || isShowingFullScreenAd) {
            return;
        }
        currentRewardedLoadToken++;
        isLoadingRewarded = false;
        RewardedAdOutcomeListener listener = pendingRewardedListener;
        clearPendingRewardedRequest();
        listener.onAdUnavailableOrFailed("Rewarded video request timed out.");
    };

    public PrimeSurveyAdManager(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.interstitialDriver = Holder.resolveDriver();
        this.rewardedDriver = Holder.resolveRewardedDriver();
    }

    public boolean isTestAdMode() {
        return BuildConfig.IS_TEST_AD_MODE;
    }

    @NonNull
    public String getInterstitialAdUnitId() {
        String configured = BuildConfig.ADMOB_INTERSTITIAL_AD_UNIT_ID;
        return configured != null ? configured.trim() : "";
    }

    @NonNull
    public String getRewardedAdUnitId() {
        String configured = BuildConfig.ADMOB_REWARDED_AD_UNIT_ID;
        return configured != null ? configured.trim() : "";
    }

    public boolean areAdUnitIdsDistinct() {
        String interstitialId = getInterstitialAdUnitId();
        String rewardedId = getRewardedAdUnitId();
        if (interstitialId.isEmpty() || rewardedId.isEmpty()) {
            return true;
        }
        return !interstitialId.equals(rewardedId);
    }

    public void initializeAndPreload() {
        if (isDestroyed) {
            return;
        }
        String interstitialId = getInterstitialAdUnitId();
        String rewardedId = getRewardedAdUnitId();
        if (interstitialId.isEmpty() && rewardedId.isEmpty()) {
            Log.w(TAG, "AdMob ad unit IDs are not configured for this build.");
            return;
        }
        if (!areAdUnitIdsDistinct()) {
            Log.e(TAG, "Interstitial and Rewarded Ad Unit IDs must never be identical.");
            return;
        }

        if (!initRequested) {
            initRequested = true;
            interstitialDriver.initialize(appContext, () -> {
                if (!isDestroyed) {
                    preloadInterstitial();
                }
            });
        } else {
            preloadInterstitial();
        }
    }

    public void preloadInterstitial() {
        if (isDestroyed) {
            return;
        }
        String adUnitId = getInterstitialAdUnitId();
        if (adUnitId.isEmpty() || !areAdUnitIdsDistinct() || preloadedInterstitial != null || isLoadingInterstitial) {
            return;
        }
        loadInterstitialInternal();
    }

    /**
     * Presents a preloaded interstitial ad for the given scheduled question, or loads one on demand.
     * Never bypasses, advances, or fakes display if the ad is still loading or fails to load.
     */
    public void showOrLoadInterstitialForQuestion(
            @NonNull Activity activity,
            int questionNumber,
            @NonNull InterstitialFlowListener listener
    ) {
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        // Only consider already completed if the ad actually showed & finished for this question
        if (displayedQuestionNumbers.contains(questionNumber)) {
            listener.onAdDismissed();
            return;
        }

        // Ignore duplicate requests while an ad is already showing or awaiting completion for the same question
        if (isShowingFullScreenAd) {
            return;
        }
        if (pendingQuestionNumber == questionNumber && pendingInterstitialListener != null) {
            return;
        }

        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);

        if (lastAttemptedQuestionNumber != questionNumber) {
            lastAttemptedQuestionNumber = questionNumber;
            currentQuestionAttemptCount = 1;
        } else if (currentQuestionAttemptCount == 0) {
            currentQuestionAttemptCount = 1;
        }
        interstitialAutoRetryCount = 0;

        if (!areAdUnitIdsDistinct()) {
            listener.onAdLoadFailed(COMPACT_AD_UNAVAILABLE_MESSAGE, currentQuestionAttemptCount);
            return;
        }

        if (preloadedInterstitial != null) {
            LoadedInterstitialHandle readyAd = preloadedInterstitial;
            preloadedInterstitial = null;
            pendingQuestionNumber = questionNumber;
            pendingActivityRef = new WeakReference<>(activity);
            pendingInterstitialListener = listener;
            presentInterstitial(activity, questionNumber, readyAd, listener);
            return;
        }

        pendingActivityRef = new WeakReference<>(activity);
        pendingQuestionNumber = questionNumber;
        pendingInterstitialListener = listener;

        listener.onAdLoading();
        scheduleInterstitialTimeout();

        if (!isLoadingInterstitial) {
            loadInterstitialInternal();
        }
    }

    /**
     * Explicitly retries loading and presenting the interstitial ad after a previous failure.
     * Resets the bounded auto-retry schedule so the user receives another clean retry cycle.
     */
    public void retryInterstitialForQuestion(
            @NonNull Activity activity,
            int questionNumber,
            @NonNull InterstitialFlowListener listener
    ) {
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed() || isShowingFullScreenAd) {
            return;
        }
        if (displayedQuestionNumbers.contains(questionNumber)) {
            listener.onAdDismissed();
            return;
        }

        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
        interstitialAutoRetryCount = 0;

        if (lastAttemptedQuestionNumber == questionNumber) {
            currentQuestionAttemptCount++;
        } else {
            lastAttemptedQuestionNumber = questionNumber;
            currentQuestionAttemptCount = 1;
        }

        if (!areAdUnitIdsDistinct()) {
            listener.onAdLoadFailed(COMPACT_AD_UNAVAILABLE_MESSAGE, currentQuestionAttemptCount);
            return;
        }

        if (preloadedInterstitial != null) {
            LoadedInterstitialHandle readyAd = preloadedInterstitial;
            preloadedInterstitial = null;
            pendingQuestionNumber = questionNumber;
            pendingActivityRef = new WeakReference<>(activity);
            pendingInterstitialListener = listener;
            presentInterstitial(activity, questionNumber, readyAd, listener);
            return;
        }

        pendingActivityRef = new WeakReference<>(activity);
        pendingQuestionNumber = questionNumber;
        pendingInterstitialListener = listener;

        listener.onAdLoading();
        scheduleInterstitialTimeout();

        if (!isLoadingInterstitial) {
            loadInterstitialInternal();
        }
    }

    private void loadInterstitialInternal() {
        if (isDestroyed || isLoadingInterstitial) {
            return;
        }
        String adUnitId = getInterstitialAdUnitId();
        if (adUnitId.isEmpty() || !areAdUnitIdsDistinct()) {
            if (pendingInterstitialListener != null) {
                notifyInterstitialAttemptFailed();
            }
            return;
        }

        isLoadingInterstitial = true;
        final long loadToken = ++currentInterstitialLoadToken;
        interstitialDriver.loadInterstitial(appContext, adUnitId, new InterstitialLoadCallback() {
            @Override
            public void onLoaded(@NonNull LoadedInterstitialHandle handle) {
                mainHandler.post(() -> handleInterstitialLoaded(loadToken, handle));
            }

            @Override
            public void onFailed(@NonNull String message, int code) {
                mainHandler.post(() -> handleInterstitialLoadFailed(loadToken, message));
            }
        });
    }

    private void handleInterstitialLoaded(long loadToken, @NonNull LoadedInterstitialHandle handle) {
        if (loadToken != currentInterstitialLoadToken) {
            handle.setFullScreenContentCallback(null);
            return;
        }
        isLoadingInterstitial = false;
        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);

        if (isDestroyed) {
            handle.setFullScreenContentCallback(null);
            return;
        }

        InterstitialFlowListener listener = pendingInterstitialListener;
        Activity activity = pendingActivityRef != null ? pendingActivityRef.get() : null;
        int questionNumber = pendingQuestionNumber;

        if (listener == null || activity == null || activity.isFinishing() || activity.isDestroyed() || isShowingFullScreenAd) {
            // Cache for the upcoming scheduled question instead of dropping or popping unexpectedly
            if (preloadedInterstitial == null) {
                preloadedInterstitial = handle;
            }
            return;
        }

        if (displayedQuestionNumbers.contains(questionNumber)) {
            if (preloadedInterstitial == null) {
                preloadedInterstitial = handle;
            }
            clearPendingInterstitialRequest();
            listener.onAdDismissed();
            return;
        }

        presentInterstitial(activity, questionNumber, handle, listener);
    }

    private void handleInterstitialLoadFailed(long loadToken, @NonNull String message) {
        if (loadToken != currentInterstitialLoadToken) {
            return;
        }
        isLoadingInterstitial = false;
        preloadedInterstitial = null;
        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        Log.w(TAG, "Interstitial failed to load: " + message);

        if (isDestroyed) {
            clearPendingInterstitialRequest();
            return;
        }

        if (pendingInterstitialListener != null) {
            notifyInterstitialAttemptFailed();
        }
    }

    private void notifyInterstitialAttemptFailed() {
        InterstitialFlowListener listener = pendingInterstitialListener;
        if (listener == null || isDestroyed) {
            return;
        }
        listener.onAdLoadFailed(COMPACT_AD_UNAVAILABLE_MESSAGE, currentQuestionAttemptCount);
        scheduleInterstitialAutoRetry();
    }

    private void scheduleInterstitialAutoRetry() {
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
        if (isDestroyed || pendingInterstitialListener == null || pendingQuestionNumber <= 0
                || displayedQuestionNumbers.contains(pendingQuestionNumber)) {
            return;
        }
        if (interstitialAutoRetryCount >= INTERSTITIAL_AUTO_RETRY_DELAYS_MS.length) {
            // Bounded retry limit reached; remain cleanly on manual RETRY without an infinite rapid loop
            return;
        }
        long delayMs = INTERSTITIAL_AUTO_RETRY_DELAYS_MS[interstitialAutoRetryCount];
        interstitialAutoRetryCount++;
        mainHandler.postDelayed(interstitialAutoRetryRunnable, delayMs);
    }

    private void presentInterstitial(
            @NonNull Activity activity,
            int questionNumber,
            @NonNull LoadedInterstitialHandle adToShow,
            @NonNull InterstitialFlowListener listener
    ) {
        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed()) {
            clearPendingInterstitialRequest();
            return;
        }
        if (displayedQuestionNumbers.contains(questionNumber)) {
            clearPendingInterstitialRequest();
            listener.onAdDismissed();
            return;
        }

        activeInterstitialAd = adToShow;
        isShowingFullScreenAd = true;
        final boolean[] showedFullScreen = {false};

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdShowedFullScreenContent() {
                showedFullScreen[0] = true;
                displayedQuestionNumbers.add(questionNumber);
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                mainHandler.removeCallbacks(interstitialTimeoutRunnable);
                mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
                isShowingFullScreenAd = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeInterstitialAd == adToShow) {
                    activeInterstitialAd = null;
                }
                if (showedFullScreen[0]) {
                    displayedQuestionNumbers.add(questionNumber);
                }
                clearPendingInterstitialRequest();
                lastAttemptedQuestionNumber = -1;
                currentQuestionAttemptCount = 0;
                interstitialAutoRetryCount = 0;

                if (!isDestroyed) {
                    if (PrimeSurveyAdSchedule.shouldPreloadInterstitialForQuestion(questionNumber)) {
                        preloadInterstitial();
                    }
                    listener.onAdDismissed();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                mainHandler.removeCallbacks(interstitialTimeoutRunnable);
                mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
                isShowingFullScreenAd = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeInterstitialAd == adToShow) {
                    activeInterstitialAd = null;
                }
                Log.w(TAG, "Interstitial failed to show: " + adError.getMessage());

                if (!isDestroyed) {
                    // Never pretend the ad was displayed when showing fails
                    notifyInterstitialAttemptFailed();
                }
            }
        });

        try {
            adToShow.show(activity);
        } catch (Throwable t) {
            mainHandler.removeCallbacks(interstitialTimeoutRunnable);
            mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
            isShowingFullScreenAd = false;
            adToShow.setFullScreenContentCallback(null);
            if (activeInterstitialAd == adToShow) {
                activeInterstitialAd = null;
            }
            Log.w(TAG, "Interstitial show threw exception: " + t.getMessage());
            if (!isDestroyed) {
                notifyInterstitialAttemptFailed();
            }
        }
    }

    private void scheduleInterstitialTimeout() {
        mainHandler.removeCallbacks(interstitialTimeoutRunnable);
        mainHandler.postDelayed(interstitialTimeoutRunnable, AD_LOAD_TIMEOUT_MS);
    }

    private void clearPendingInterstitialRequest() {
        mainHandler.removeCallbacks(interstitialAutoRetryRunnable);
        pendingQuestionNumber = -1;
        pendingActivityRef = null;
        pendingInterstitialListener = null;
        interstitialAutoRetryCount = 0;
    }

    @VisibleForTesting
    public int getDisplayedInterstitialCount() {
        return displayedQuestionNumbers.size();
    }

    @VisibleForTesting
    public boolean wasInterstitialDisplayedForQuestion(int questionNumber) {
        return displayedQuestionNumbers.contains(questionNumber);
    }

    @VisibleForTesting
    public boolean isShowingFullScreenAd() {
        return isShowingFullScreenAd;
    }

    public void preloadRewardedAd() {
        if (isDestroyed) {
            return;
        }
        String adUnitId = getRewardedAdUnitId();
        if (adUnitId.isEmpty() || !areAdUnitIdsDistinct() || preloadedRewarded != null || isLoadingRewarded) {
            return;
        }
        loadRewardedInternal();
    }

    /**
     * Loads (if not already preloaded) and presents an AdMob Rewarded Ad.
     * Coins are NEVER granted unless OnUserEarnedRewardListener fires before dismissal.
     */
    public void loadAndShowRewardedAd(
            @NonNull Activity activity,
            @NonNull RewardedAdOutcomeListener listener
    ) {
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed()) {
            listener.onAdUnavailableOrFailed("Activity is no longer active.");
            return;
        }
        if (isShowingFullScreenAd || pendingRewardedListener != null) {
            return;
        }

        String adUnitId = getRewardedAdUnitId();
        if (adUnitId.isEmpty()) {
            listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID is not configured.");
            return;
        }
        if (!areAdUnitIdsDistinct()) {
            listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID must differ from Interstitial Ad Unit ID.");
            return;
        }

        if (preloadedRewarded != null) {
            LoadedRewardedHandle readyAd = preloadedRewarded;
            preloadedRewarded = null;
            presentRewardedAd(activity, readyAd, listener);
            return;
        }

        pendingRewardedActivityRef = new WeakReference<>(activity);
        pendingRewardedListener = listener;
        listener.onLoadingStarted();
        scheduleRewardedTimeout();

        if (!isLoadingRewarded) {
            loadRewardedInternal();
        }
    }

    private void loadRewardedInternal() {
        if (isDestroyed || isLoadingRewarded) {
            return;
        }
        String adUnitId = getRewardedAdUnitId();
        if (adUnitId.isEmpty()) {
            if (pendingRewardedListener != null) {
                RewardedAdOutcomeListener listener = pendingRewardedListener;
                clearPendingRewardedRequest();
                listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID is not configured.");
            }
            return;
        }
        if (!areAdUnitIdsDistinct()) {
            if (pendingRewardedListener != null) {
                RewardedAdOutcomeListener listener = pendingRewardedListener;
                clearPendingRewardedRequest();
                listener.onAdUnavailableOrFailed("Rewarded Ad Unit ID must differ from Interstitial Ad Unit ID.");
            }
            return;
        }

        isLoadingRewarded = true;
        final long loadToken = ++currentRewardedLoadToken;
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
        if (loadToken != currentRewardedLoadToken) {
            handle.setFullScreenContentCallback(null);
            return;
        }
        isLoadingRewarded = false;
        mainHandler.removeCallbacks(rewardedTimeoutRunnable);

        if (isDestroyed) {
            handle.setFullScreenContentCallback(null);
            return;
        }

        RewardedAdOutcomeListener listener = pendingRewardedListener;
        Activity activity = pendingRewardedActivityRef != null ? pendingRewardedActivityRef.get() : null;
        clearPendingRewardedRequest();

        if (listener == null || activity == null || activity.isFinishing() || activity.isDestroyed() || isShowingFullScreenAd) {
            if (preloadedRewarded == null) {
                preloadedRewarded = handle;
            }
            return;
        }

        presentRewardedAd(activity, handle, listener);
    }

    private void handleRewardedLoadFailed(long loadToken, @NonNull String message) {
        if (loadToken != currentRewardedLoadToken) {
            return;
        }
        isLoadingRewarded = false;
        preloadedRewarded = null;
        mainHandler.removeCallbacks(rewardedTimeoutRunnable);
        Log.w(TAG, "Rewarded ad failed to load: " + message);

        if (isDestroyed) {
            clearPendingRewardedRequest();
            return;
        }

        RewardedAdOutcomeListener listener = pendingRewardedListener;
        clearPendingRewardedRequest();
        if (listener != null) {
            listener.onAdUnavailableOrFailed(message);
        }
    }

    private void presentRewardedAd(
            @NonNull Activity activity,
            @NonNull LoadedRewardedHandle adToShow,
            @NonNull RewardedAdOutcomeListener listener
    ) {
        mainHandler.removeCallbacks(rewardedTimeoutRunnable);
        if (isDestroyed || activity.isFinishing() || activity.isDestroyed() || isShowingFullScreenAd) {
            clearPendingRewardedRequest();
            listener.onAdUnavailableOrFailed("Unable to display rewarded video right now.");
            return;
        }

        final boolean[] rewardEarned = {false};
        activeRewardedAd = adToShow;
        isShowingFullScreenAd = true;

        adToShow.setFullScreenContentCallback(new FullScreenContentCallback() {
            @Override
            public void onAdDismissedFullScreenContent() {
                mainHandler.removeCallbacks(rewardedTimeoutRunnable);
                isShowingFullScreenAd = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeRewardedAd == adToShow) {
                    activeRewardedAd = null;
                }
                clearPendingRewardedRequest();
                if (isDestroyed) {
                    return;
                }
                if (rewardEarned[0]) {
                    listener.onRewardVerified();
                } else {
                    preloadRewardedAd();
                    listener.onRewardNotEarned();
                }
            }

            @Override
            public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                mainHandler.removeCallbacks(rewardedTimeoutRunnable);
                isShowingFullScreenAd = false;
                adToShow.setFullScreenContentCallback(null);
                if (activeRewardedAd == adToShow) {
                    activeRewardedAd = null;
                }
                clearPendingRewardedRequest();
                Log.w(TAG, "Rewarded ad failed to show: " + adError.getMessage());
                if (!isDestroyed) {
                    preloadRewardedAd();
                    listener.onAdUnavailableOrFailed(adError.getMessage());
                }
            }
        });

        try {
            // Only mark reward earned when official OnUserEarnedRewardListener fires
            adToShow.show(activity, () -> rewardEarned[0] = true);
        } catch (Throwable t) {
            mainHandler.removeCallbacks(rewardedTimeoutRunnable);
            isShowingFullScreenAd = false;
            adToShow.setFullScreenContentCallback(null);
            if (activeRewardedAd == adToShow) {
                activeRewardedAd = null;
            }
            clearPendingRewardedRequest();
            if (!isDestroyed) {
                listener.onAdUnavailableOrFailed(t.getMessage() != null ? t.getMessage() : "Unable to display ad");
            }
        }
    }

    private void scheduleRewardedTimeout() {
        mainHandler.removeCallbacks(rewardedTimeoutRunnable);
        mainHandler.postDelayed(rewardedTimeoutRunnable, AD_LOAD_TIMEOUT_MS);
    }

    private void clearPendingRewardedRequest() {
        pendingRewardedActivityRef = null;
        pendingRewardedListener = null;
    }

    public void destroy() {
        isDestroyed = true;
        mainHandler.removeCallbacksAndMessages(null);
        clearPendingInterstitialRequest();
        clearPendingRewardedRequest();
        if (preloadedInterstitial != null) {
            preloadedInterstitial.setFullScreenContentCallback(null);
            preloadedInterstitial = null;
        }
        if (activeInterstitialAd != null) {
            activeInterstitialAd.setFullScreenContentCallback(null);
            activeInterstitialAd = null;
        }
        if (preloadedRewarded != null) {
            preloadedRewarded.setFullScreenContentCallback(null);
            preloadedRewarded = null;
        }
        if (activeRewardedAd != null) {
            activeRewardedAd.setFullScreenContentCallback(null);
            activeRewardedAd = null;
        }
        isLoadingInterstitial = false;
        isLoadingRewarded = false;
        isShowingFullScreenAd = false;
    }
}
