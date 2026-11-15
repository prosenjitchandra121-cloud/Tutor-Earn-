package com.tutor.earn.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Enforces the 24-hour Prime Survey reward claim limit (1050 virtual coins),
 * prevents duplicate claims and device-clock-manipulation bypasses, tracks
 * non-repeating daily session questions, and integrates with Firebase Auth /
 * Firebase Realtime Database when Firebase configuration is available.
 */
public class PrimeSurveyRepository {

    public static final int PRIME_SURVEY_REWARD_COINS = 1050;
    public static final long COOLDOWN_DURATION_MS = 24L * 60L * 60L * 1000L; // 24 hours

    private static final String PREFS_NAME = "tutor_earn_prime_survey_ledger";
    private static final String KEY_LAST_CLAIM_SERVER_MS = "last_claim_server_ms";
    private static final String KEY_LAST_CLAIM_ELAPSED_MS = "last_claim_elapsed_ms";
    private static final String KEY_LAST_CLAIM_BOOT_COUNT = "last_claim_boot_count";
    private static final String KEY_LAST_CLAIM_TX_ID = "last_claim_tx_id";
    private static final String KEY_TOTAL_COINS = "total_verified_coins";
    private static final String KEY_INTEGRITY_MAC = "integrity_mac";
    private static final String KEY_SESSION_DAY = "session_day_utc";
    private static final String KEY_SESSION_ANSWERED_IDS = "session_answered_ids";
    private static final String KEYSTORE_ALIAS = "tutor_earn_reward_hmac_v1";

    public static final class EligibilityState {
        public final boolean canClaim;
        public final boolean requiresNetworkVerification;
        public final long remainingCooldownMs;
        public final long authoritativeServerNowMs;
        public final long totalVerifiedCoins;

        public EligibilityState(
                boolean canClaim,
                boolean requiresNetworkVerification,
                long remainingCooldownMs,
                long authoritativeServerNowMs,
                long totalVerifiedCoins
        ) {
            this.canClaim = canClaim;
            this.requiresNetworkVerification = requiresNetworkVerification;
            this.remainingCooldownMs = Math.max(0L, remainingCooldownMs);
            this.authoritativeServerNowMs = authoritativeServerNowMs;
            this.totalVerifiedCoins = totalVerifiedCoins;
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
    private final FirebasePrimeSurveyBackend firebaseBackend;
    private final CoinBalanceRepository coinBalanceRepository;
    private boolean isClaimInFlight = false;

    public PrimeSurveyRepository(@NonNull Context context) {
        this(
                context,
                new RewardTimeProvider.NetworkTimeProvider(),
                new FirebasePrimeSurveyBackend(context),
                CoinBalanceRepository.getInstance(context)
        );
    }

    public PrimeSurveyRepository(
            @NonNull Context context,
            @NonNull RewardTimeProvider timeProvider,
            @NonNull FirebasePrimeSurveyBackend firebaseBackend
    ) {
        this(context, timeProvider, firebaseBackend, CoinBalanceRepository.getInstance(context));
    }

    public PrimeSurveyRepository(
            @NonNull Context context,
            @NonNull RewardTimeProvider timeProvider,
            @NonNull FirebasePrimeSurveyBackend firebaseBackend,
            @NonNull CoinBalanceRepository coinBalanceRepository
    ) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.timeProvider = timeProvider;
        this.firebaseBackend = firebaseBackend;
        this.coinBalanceRepository = coinBalanceRepository;
    }

    @NonNull
    public Set<String> getAnsweredIdsForToday() {
        String todayKey = getUtcDayKey(timeProvider.wallClockMs());
        String savedDay = prefs.getString(KEY_SESSION_DAY, "");
        if (!todayKey.equals(savedDay)) {
            return new HashSet<>();
        }
        Set<String> saved = prefs.getStringSet(KEY_SESSION_ANSWERED_IDS, null);
        return saved != null ? new HashSet<>(saved) : new HashSet<>();
    }

    public void markQuestionAnsweredToday(@NonNull String questionId) {
        String todayKey = getUtcDayKey(timeProvider.wallClockMs());
        String savedDay = prefs.getString(KEY_SESSION_DAY, "");
        Set<String> updated = todayKey.equals(savedDay)
                ? new HashSet<>(getAnsweredIdsForToday())
                : new HashSet<>();
        updated.add(questionId);

        prefs.edit()
                .putString(KEY_SESSION_DAY, todayKey)
                .putStringSet(KEY_SESSION_ANSWERED_IDS, updated)
                .apply();
    }

    public void resetDailyAnsweredSessionIfCompleted() {
        String todayKey = getUtcDayKey(timeProvider.wallClockMs());
        prefs.edit()
                .putString(KEY_SESSION_DAY, todayKey)
                .remove(KEY_SESSION_ANSWERED_IDS)
                .apply();
    }

    public synchronized boolean isClaimInFlight() {
        return isClaimInFlight;
    }

    @NonNull
    public EligibilityState getCachedEligibilityState() {
        return evaluateEligibility(-1L, 0L);
    }

    /**
     * Checks claim eligibility using authoritative server/Firebase timestamps
     * combined with signed monotonic local claim records.
     */
    public void verifyClaimEligibility(@NonNull EligibilityCallback callback) {
        if (firebaseBackend.isConfiguredAndAuthenticated()) {
            firebaseBackend.fetchRemoteClaimStatus((configured, remoteLastClaimMs, remoteServerNowMs, error) -> {
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

    /**
     * Commits the fixed 1050 virtual coin reward ONLY after:
     * 1. Single-flight & duplicate transaction check passes.
     * 2. Rewarded Ad completion was verified by AdMob OnUserEarnedRewardListener.
     * 3. 24-hour cooldown against authoritative server timestamp passes.
     */
    public void commitVerifiedReward(
            boolean rewardedAdVerified,
            @NonNull String transactionId,
            @NonNull ClaimCommitCallback callback
    ) {
        if (!rewardedAdVerified) {
            callback.onClaimResult(
                    false,
                    appContext.getString(R.string.prime_survey_ad_not_completed),
                    getCachedEligibilityState()
            );
            return;
        }

        synchronized (this) {
            if (isClaimInFlight) {
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        getCachedEligibilityState()
                );
                return;
            }
            String lastTx = prefs.getString(KEY_LAST_CLAIM_TX_ID, "");
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

        if (firebaseBackend.isConfiguredAndAuthenticated()) {
            firebaseBackend.fetchRemoteClaimStatus((configured, remoteLastClaimMs, remoteServerNowMs, error) -> {
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
                        PRIME_SURVEY_REWARD_COINS,
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
                                        CoinEarningSource.PRIME_SURVEY,
                                        transactionId,
                                        PRIME_SURVEY_REWARD_COINS
                                );
                            }
                            persistSignedClaim(claimTs, transactionId);
                            EligibilityState updated = evaluateEligibility(claimTs, claimTs);
                            callback.onClaimResult(
                                    true,
                                    appContext.getString(R.string.prime_survey_coins_added),
                                    updated
                            );
                        }
                );
            });
            return;
        }

        // Non-Firebase path: verify authoritative server UTC timestamp before committing reward
        timeProvider.fetchAuthoritativeServerTimeMs(serverUtcNowMs -> {
            if (serverUtcNowMs <= 0L) {
                // Allow claim ONLY if no prior claim exists AND device clock is not needed to bypass cooldown,
                // OR if same-boot monotonic uptime proves >= 24h has genuinely elapsed.
                EligibilityState offlineCheck = evaluateEligibility(-1L, 0L);
                boolean hasPreviousClaim = getVerifiedLastClaimServerMs() > 0L;
                if (hasPreviousClaim && !offlineCheck.canClaim) {
                    releaseInFlight();
                    String msg = offlineCheck.requiresNetworkVerification
                            ? appContext.getString(R.string.prime_survey_time_verification_failed)
                            : appContext.getString(R.string.prime_survey_already_claimed);
                    callback.onClaimResult(false, msg, offlineCheck);
                    return;
                }
                if (hasPreviousClaim && offlineCheck.requiresNetworkVerification) {
                    releaseInFlight();
                    callback.onClaimResult(
                            false,
                            appContext.getString(R.string.prime_survey_time_verification_failed),
                            offlineCheck
                    );
                    return;
                }
                // First-time claim or proven 24h same-boot elapsed uptime
                boolean credited = coinBalanceRepository.commitVerifiedLocalReward(
                        CoinEarningSource.PRIME_SURVEY,
                        transactionId,
                        PRIME_SURVEY_REWARD_COINS
                );
                if (!credited) {
                    releaseInFlight();
                    callback.onClaimResult(
                            false,
                            appContext.getString(R.string.prime_survey_already_claimed),
                            offlineCheck
                    );
                    return;
                }
                long fallbackTimestamp = timeProvider.wallClockMs();
                persistSignedClaim(fallbackTimestamp, transactionId);
                releaseInFlight();
                EligibilityState updated = evaluateEligibility(-1L, 0L);
                callback.onClaimResult(
                        true,
                        appContext.getString(R.string.prime_survey_coins_added),
                        updated
                );
                return;
            }

            EligibilityState serverCheck = evaluateEligibility(serverUtcNowMs, 0L);
            if (!serverCheck.canClaim) {
                releaseInFlight();
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        serverCheck
                );
                return;
            }

            boolean credited = coinBalanceRepository.commitVerifiedLocalReward(
                    CoinEarningSource.PRIME_SURVEY,
                    transactionId,
                    PRIME_SURVEY_REWARD_COINS
            );
            if (!credited) {
                releaseInFlight();
                callback.onClaimResult(
                        false,
                        appContext.getString(R.string.prime_survey_already_claimed),
                        serverCheck
                );
                return;
            }
            persistSignedClaim(serverUtcNowMs, transactionId);
            releaseInFlight();
            EligibilityState updated = evaluateEligibility(serverUtcNowMs, 0L);
            callback.onClaimResult(
                    true,
                    appContext.getString(R.string.prime_survey_coins_added),
                    updated
            );
        });
    }

    @NonNull
    public String generateTransactionId() {
        return "ps_" + UUID.randomUUID().toString().replace("-", "");
    }

    @NonNull
    public static String formatRemainingHms(long remainingMs) {
        long safeMs = Math.max(0L, remainingMs);
        long totalSeconds = (safeMs + 999L) / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    private synchronized void releaseInFlight() {
        isClaimInFlight = false;
    }

    @NonNull
    private EligibilityState evaluateEligibility(long authoritativeServerNowMs, long remoteLastClaimMs) {
        boolean integrityOk = verifyStoredIntegrity();
        long localLastClaimServerMs = prefs.getLong(KEY_LAST_CLAIM_SERVER_MS, 0L);
        long localLastElapsedMs = prefs.getLong(KEY_LAST_CLAIM_ELAPSED_MS, 0L);
        int localLastBootCount = prefs.getInt(KEY_LAST_CLAIM_BOOT_COUNT, -1);
        long totalCoins = coinBalanceRepository.getCurrentBalance();

        // If local ledger was tampered with, lock cooldown defensively for full 24h
        if (!integrityOk && hasAnyClaimKeys()) {
            return new EligibilityState(
                    false,
                    true,
                    COOLDOWN_DURATION_MS,
                    authoritativeServerNowMs,
                    totalCoins
            );
        }

        long effectiveLastClaimMs = Math.max(localLastClaimServerMs, remoteLastClaimMs);
        if (effectiveLastClaimMs <= 0L) {
            return new EligibilityState(true, false, 0L, authoritativeServerNowMs, totalCoins);
        }

        // Case 1: Authoritative server UTC time available -> immune to device clock changes
        if (authoritativeServerNowMs > 0L) {
            if (authoritativeServerNowMs < effectiveLastClaimMs) {
                return new EligibilityState(
                        false,
                        false,
                        COOLDOWN_DURATION_MS,
                        authoritativeServerNowMs,
                        totalCoins
                );
            }
            long elapsed = authoritativeServerNowMs - effectiveLastClaimMs;
            long remaining = Math.max(0L, COOLDOWN_DURATION_MS - elapsed);
            return new EligibilityState(
                    remaining == 0L,
                    false,
                    remaining,
                    authoritativeServerNowMs,
                    totalCoins
            );
        }

        // Case 2: Offline fallback -> use monotonic SystemClock.elapsedRealtime() within same boot
        int currentBootCount = timeProvider.getBootCount(appContext);
        long currentElapsed = timeProvider.elapsedRealtimeMs();
        boolean sameBoot = (localLastBootCount >= 0
                && currentBootCount == localLastBootCount
                && currentElapsed >= localLastElapsedMs);

        if (sameBoot && localLastElapsedMs >= 0L) {
            long monotonicElapsed = currentElapsed - localLastElapsedMs;
            long remaining = Math.max(0L, COOLDOWN_DURATION_MS - monotonicElapsed);
            return new EligibilityState(
                    remaining == 0L,
                    false,
                    remaining,
                    -1L,
                    totalCoins
            );
        }

        // Device rebooted offline or boot count changed: require server time verification
        // while showing conservative remaining cooldown based on wall clock if valid, never < 0
        long wallNow = timeProvider.wallClockMs();
        long wallElapsed = wallNow - effectiveLastClaimMs;
        long conservativeRemaining = (wallElapsed < 0L)
                ? COOLDOWN_DURATION_MS
                : Math.max(0L, COOLDOWN_DURATION_MS - wallElapsed);

        return new EligibilityState(
                false,
                true,
                conservativeRemaining,
                -1L,
                totalCoins
        );
    }

    private long getVerifiedLastClaimServerMs() {
        if (!verifyStoredIntegrity()) {
            return 0L;
        }
        return prefs.getLong(KEY_LAST_CLAIM_SERVER_MS, 0L);
    }

    private void persistSignedClaim(long serverTimestampMs, @NonNull String transactionId) {
        long newTotal = coinBalanceRepository.getCurrentBalance();
        long elapsedNow = timeProvider.elapsedRealtimeMs();
        int bootCount = timeProvider.getBootCount(appContext);

        String mac = computeIntegrityMac(
                serverTimestampMs,
                elapsedNow,
                bootCount,
                transactionId,
                newTotal
        );

        prefs.edit()
                .putLong(KEY_LAST_CLAIM_SERVER_MS, serverTimestampMs)
                .putLong(KEY_LAST_CLAIM_ELAPSED_MS, elapsedNow)
                .putInt(KEY_LAST_CLAIM_BOOT_COUNT, bootCount)
                .putString(KEY_LAST_CLAIM_TX_ID, transactionId)
                .putLong(KEY_TOTAL_COINS, newTotal)
                .putString(KEY_INTEGRITY_MAC, mac)
                .commit();
    }

    private boolean hasAnyClaimKeys() {
        return prefs.contains(KEY_LAST_CLAIM_SERVER_MS)
                || prefs.contains(KEY_TOTAL_COINS)
                || prefs.contains(KEY_LAST_CLAIM_TX_ID);
    }

    private boolean verifyStoredIntegrity() {
        if (!hasAnyClaimKeys()) {
            return true;
        }
        String storedMac = prefs.getString(KEY_INTEGRITY_MAC, "");
        if (storedMac == null || storedMac.isEmpty()) {
            return false;
        }
        long serverMs = prefs.getLong(KEY_LAST_CLAIM_SERVER_MS, 0L);
        long elapsedMs = prefs.getLong(KEY_LAST_CLAIM_ELAPSED_MS, 0L);
        int bootCount = prefs.getInt(KEY_LAST_CLAIM_BOOT_COUNT, -1);
        String txId = prefs.getString(KEY_LAST_CLAIM_TX_ID, "");
        long totalCoins = prefs.getLong(KEY_TOTAL_COINS, 0L);

        String expectedMac = computeIntegrityMac(serverMs, elapsedMs, bootCount, txId != null ? txId : "", totalCoins);
        return constantTimeEquals(storedMac, expectedMac);
    }

    @NonNull
    private String computeIntegrityMac(
            long serverMs,
            long elapsedMs,
            int bootCount,
            @NonNull String txId,
            long totalCoins
    ) {
        String payload = serverMs + "|" + elapsedMs + "|" + bootCount + "|" + txId + "|" + totalCoins;
        try {
            SecretKey key = getOrCreateHmacKey();
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Throwable t) {
            return Integer.toHexString(payload.hashCode());
        }
    }

    @NonNull
    private SecretKey getOrCreateHmacKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.Entry entry = keyStore.getEntry(KEYSTORE_ALIAS, null);
            if (entry instanceof KeyStore.SecretKeyEntry) {
                return ((KeyStore.SecretKeyEntry) entry).getSecretKey();
            }
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_HMAC_SHA256,
                    "AndroidKeyStore"
            );
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                        KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY
                ).build();
                keyGenerator.init(spec);
            }
            return keyGenerator.generateKey();
        } catch (Throwable fallbackInTestEnv) {
            // Deterministic fallback when AndroidKeyStore is unavailable (e.g., Robolectric JVM)
            String seed = appContext.getPackageName() + ":TutorEarnPrimeSurveyIntegrityV1";
            return new SecretKeySpec(seed.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        }
    }

    private static boolean constantTimeEquals(@NonNull String a, @NonNull String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    @NonNull
    private static String getUtcDayKey(long epochMs) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(epochMs));
    }
}
