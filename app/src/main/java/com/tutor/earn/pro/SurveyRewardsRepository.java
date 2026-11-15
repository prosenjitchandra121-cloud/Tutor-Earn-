package com.tutor.earn.pro;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Enforces the 24-hour Survey Rewards claim limit (500 virtual coins) per Firebase account,
 * prevents duplicate claims and device-clock-manipulation bypasses, and integrates with
 * Firebase Auth / Firebase Realtime Database and CoinBalanceRepository.
 */
public class SurveyRewardsRepository {

    public static final int SURVEY_REWARDS_COINS = 500;
    public static final long COOLDOWN_DURATION_MS = 24L * 60L * 60L * 1000L; // 24 hours

    private static final String PREFS_NAME = "tutor_earn_survey_rewards_ledger";
    private static final String KEY_LAST_CLAIM_SERVER_MS_PREFIX = "last_claim_server_ms_";
    private static final String KEY_LAST_CLAIM_ELAPSED_MS_PREFIX = "last_claim_elapsed_ms_";
    private static final String KEY_LAST_CLAIM_BOOT_COUNT_PREFIX = "last_claim_boot_count_";
    private static final String KEY_LAST_CLAIM_TX_ID_PREFIX = "last_claim_tx_id_";
    private static final String LOCAL_FALLBACK_UID = "__local_guest__";

    @VisibleForTesting
    public static final class Holder {
        @Nullable
        private static RewardTimeProvider timeProviderOverride;
        @Nullable
        private static FirebaseSurveyRewardsBackend backendOverride;

        private Holder() {
        }

        @VisibleForTesting
        public static void setTimeProviderOverride(@Nullable RewardTimeProvider provider) {
            timeProviderOverride = provider;
        }

        @VisibleForTesting
        public static void setBackendOverride(@Nullable FirebaseSurveyRewardsBackend backend) {
            backendOverride = backend;
        }

        @NonNull
        static RewardTimeProvider resolveTimeProvider() {
            return timeProviderOverride != null
                    ? timeProviderOverride
                    : new RewardTimeProvider.NetworkTimeProvider();
        }

        @NonNull
        static FirebaseSurveyRewardsBackend resolveBackend(@NonNull Context context) {
            return backendOverride != null
                    ? backendOverride
                    : new FirebaseSurveyRewardsBackend(context);
        }
    }

    public static final class EligibilityState {
        public final boolean canClaim;
        public final boolean requiresNetworkVerification;
        public final long remainingCooldownMs;
        public final long authoritativeServerNowMs;

        public EligibilityState(
                boolean canClaim,
                boolean requiresNetworkVerification,
                long remainingCooldownMs,
                long authoritativeServerNowMs
        ) {
            this.canClaim = canClaim;
            this.requiresNetworkVerification = requiresNetworkVerification;
            this.remainingCooldownMs = Math.max(0L, remainingCooldownMs);
            this.authoritativeServerNowMs = authoritativeServerNowMs;
        }
    }

    public interface EligibilityCallback {
        void onResult(@NonNull EligibilityState state);
    }

    public interface ClaimCommitCallback {
        void onClaimResult(boolean success, @NonNull String message, @NonNull EligibilityState updatedState);
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final RewardTimeProvider timeProvider;
    private final FirebaseSurveyRewardsBackend firebaseBackend;
    private final CoinBalanceRepository coinBalanceRepository;
    private boolean isClaimInFlight = false;

    public SurveyRewardsRepository(@NonNull Context context) {
        this(
                context,
                Holder.resolveTimeProvider(),
                Holder.resolveBackend(context),
                CoinBalanceRepository.getInstance(context)
        );
    }

    public SurveyRewardsRepository(
            @NonNull Context context,
            @NonNull RewardTimeProvider timeProvider,
            @NonNull FirebaseSurveyRewardsBackend firebaseBackend,
            @NonNull CoinBalanceRepository coinBalanceRepository
    ) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.timeProvider = timeProvider;
        this.firebaseBackend = firebaseBackend;
        this.coinBalanceRepository = coinBalanceRepository;
    }

    public synchronized boolean isClaimInFlight() {
        return isClaimInFlight;
    }

    @NonNull
    public EligibilityState getCachedEligibilityState() {
        return evaluateEligibility(-1L, 0L);
    }

    public void verifyClaimEligibility(@NonNull EligibilityCallback callback) {
        if (firebaseBackend.isConfiguredAndAuthenticated() && !CoinBalanceRepository.hasTestDriverOverride()) {
            firebaseBackend.fetchRemoteClaimStatus((configured, remoteLastClaimMs, totalClaims, totalCoins, remoteServerNowMs, error) -> {
                if (configured && remoteServerNowMs > 0L) {
                    callback.onResult(evaluateEligibility(remoteServerNowMs, remoteLastClaimMs));
                } else {
                    checkHttpTimeAndEvaluate(remoteLastClaimMs, callback);
                }
            });
            return;
        }
        checkHttpTimeAndEvaluate(0L, callback);
    }

    private void checkHttpTimeAndEvaluate(long remoteLastClaimMs, @NonNull EligibilityCallback callback) {
        timeProvider.fetchAuthoritativeServerTimeMs(serverUtcNowMs ->
                callback.onResult(evaluateEligibility(serverUtcNowMs, remoteLastClaimMs))
        );
    }

    public void commitVerifiedReward(
            boolean rewardedAdVerified,
            @NonNull String transactionId,
            @NonNull ClaimCommitCallback callback
    ) {
        if (!rewardedAdVerified) {
            callback.onClaimResult(
                    false,
                    appContext.getString(R.string.survey_rewards_ad_not_completed),
                    getCachedEligibilityState()
            );
            return;
        }

        final String accountKey = getActiveAccountKey();
        synchronized (this) {
            if (isClaimInFlight) {
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        getCachedEligibilityState()
                );
                return;
            }
            String lastTx = prefs.getString(KEY_LAST_CLAIM_TX_ID_PREFIX + accountKey, "");
            if (transactionId.equals(lastTx) && !lastTx.isEmpty()) {
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        getCachedEligibilityState()
                );
                return;
            }
            isClaimInFlight = true;
        }

        if (firebaseBackend.isConfiguredAndAuthenticated() && !CoinBalanceRepository.hasTestDriverOverride()) {
            firebaseBackend.fetchRemoteClaimStatus((configured, remoteLastClaimMs, totalClaims, totalCoins, remoteServerNowMs, error) -> {
                if (!configured || remoteServerNowMs <= 0L) {
                    releaseInFlight();
                    callback.onClaimResult(
                            false,
                            appContext.getString(R.string.prime_survey_time_verification_failed),
                            getCachedEligibilityState()
                    );
                    return;
                }
                EligibilityState preCheck = evaluateEligibility(remoteServerNowMs, remoteLastClaimMs);
                if (!preCheck.canClaim) {
                    releaseInFlight();
                    callback.onClaimResult(
                            false,
                            appContext.getString(R.string.prime_survey_already_claimed),
                            preCheck
                    );
                    return;
                }
                firebaseBackend.recordVerifiedRewardTransaction(
                        transactionId,
                        SURVEY_REWARDS_COINS,
                        COOLDOWN_DURATION_MS,
                        remoteServerNowMs,
                        (success, committedServerTs, committedBalance, txError) -> {
                            releaseInFlight();
                            if (!success) {
                                callback.onClaimResult(
                                        false,
                                        txError != null ? txError : appContext.getString(R.string.prime_survey_already_claimed),
                                        evaluateEligibility(remoteServerNowMs, remoteLastClaimMs)
                                );
                                return;
                            }
                            long claimTs = committedServerTs > 0L ? committedServerTs : remoteServerNowMs;
                            String uid = firebaseBackend.getAuthenticatedUid();
                            if (uid != null && committedBalance >= 0L) {
                                coinBalanceRepository.recordRemoteCommittedReward(uid, transactionId, committedBalance);
                            } else {
                                coinBalanceRepository.commitVerifiedLocalReward(
                                        CoinEarningSource.SURVEY_REWARDS,
                                        transactionId,
                                        SURVEY_REWARDS_COINS
                                );
                            }
                            persistLocalClaim(accountKey, claimTs, transactionId);
                            callback.onClaimResult(
                                    true,
                                    appContext.getString(R.string.survey_rewards_success),
                                    new EligibilityState(false, false, COOLDOWN_DURATION_MS, claimTs)
                            );
                        }
                );
            });
            return;
        }

        timeProvider.fetchAuthoritativeServerTimeMs(serverNowMs -> {
            EligibilityState state = evaluateEligibility(serverNowMs, 0L);
            if (!state.canClaim) {
                releaseInFlight();
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        state
                );
                return;
            }

            coinBalanceRepository.creditVerifiedEarning(
                    CoinEarningSource.SURVEY_REWARDS,
                    transactionId,
                    SURVEY_REWARDS_COINS,
                    true,
                    (success, updatedBalance, message) -> {
                        releaseInFlight();
                        if (!success) {
                            callback.onClaimResult(false, message, state);
                            return;
                        }
                        long claimTs = serverNowMs > 0L ? serverNowMs : timeProvider.wallClockMs();
                        persistLocalClaim(accountKey, claimTs, transactionId);
                        callback.onClaimResult(
                                true,
                                appContext.getString(R.string.survey_rewards_success),
                                new EligibilityState(false, false, COOLDOWN_DURATION_MS, claimTs)
                        );
                    }
            );
        });
    }

    private synchronized void releaseInFlight() {
        isClaimInFlight = false;
    }

    @NonNull
    private String getActiveAccountKey() {
        String uid = firebaseBackend.getAuthenticatedUid();
        if (uid != null && !uid.trim().isEmpty()) {
            return uid.trim();
        }
        return LOCAL_FALLBACK_UID;
    }

    private void persistLocalClaim(
            @NonNull String accountKey,
            long serverTimestampMs,
            @NonNull String transactionId
    ) {
        prefs.edit()
                .putLong(KEY_LAST_CLAIM_SERVER_MS_PREFIX + accountKey, serverTimestampMs)
                .putLong(KEY_LAST_CLAIM_ELAPSED_MS_PREFIX + accountKey, timeProvider.elapsedRealtimeMs())
                .putInt(KEY_LAST_CLAIM_BOOT_COUNT_PREFIX + accountKey, timeProvider.getBootCount(appContext))
                .putString(KEY_LAST_CLAIM_TX_ID_PREFIX + accountKey, transactionId)
                .apply();
    }

    private EligibilityState evaluateEligibility(long serverNowMs, long remoteLastClaimMs) {
        String accountKey = getActiveAccountKey();
        long localLastClaimMs = prefs.getLong(KEY_LAST_CLAIM_SERVER_MS_PREFIX + accountKey, 0L);
        long localLastElapsedMs = prefs.getLong(KEY_LAST_CLAIM_ELAPSED_MS_PREFIX + accountKey, 0L);
        int localLastBootCount = prefs.getInt(KEY_LAST_CLAIM_BOOT_COUNT_PREFIX + accountKey, -1);

        long effectiveLastClaimMs = Math.max(remoteLastClaimMs, localLastClaimMs);
        if (effectiveLastClaimMs <= 0L) {
            return new EligibilityState(true, false, 0L, serverNowMs);
        }

        if (serverNowMs > 0L) {
            long elapsedSinceLastClaim = serverNowMs - effectiveLastClaimMs;
            if (elapsedSinceLastClaim >= COOLDOWN_DURATION_MS) {
                return new EligibilityState(true, false, 0L, serverNowMs);
            }
            long remaining = COOLDOWN_DURATION_MS - Math.max(0L, elapsedSinceLastClaim);
            return new EligibilityState(false, false, remaining, serverNowMs);
        }

        // Offline fallback: use monotonic elapsedRealtime ONLY (never trust wall clock!)
        int currentBootCount = timeProvider.getBootCount(appContext);
        long currentElapsed = timeProvider.elapsedRealtimeMs();
        if (localLastElapsedMs > 0L
                && localLastBootCount == currentBootCount
                && currentElapsed >= localLastElapsedMs) {
            long monotonicElapsedSinceClaim = currentElapsed - localLastElapsedMs;
            if (monotonicElapsedSinceClaim < COOLDOWN_DURATION_MS) {
                return new EligibilityState(
                        false,
                        true,
                        COOLDOWN_DURATION_MS - monotonicElapsedSinceClaim,
                        -1L
                );
            }
            return new EligibilityState(true, false, 0L, -1L);
        }

        // Across reboot without authoritative server time, remain safely locked until online check
        return new EligibilityState(false, true, COOLDOWN_DURATION_MS, -1L);
    }
}
