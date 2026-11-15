package com.example;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 * Single centralized repository for the user's Tutor Earn coin balance.
 * - All earning features (Prime Survey, Survey Rewards, Read and Rewards, Earn With AI)
 *   deposit verified rewards into one shared per-user balance.
 * - Uses Firebase Auth + Firebase Realtime Database:
 *     users/{uid}/coins/balance
 *     users/{uid}/coinTransactions/{transactionId}
 * - Uses atomic Firebase RTDB transactions and immutable transaction IDs to prevent duplicates.
 * - Exposes no client-side direct balance-setter or unverified coin-addition APIs.
 * - Maintains an HMAC-SHA256 signed local cache keyed by UID so the balance survives
 *   navigation and restarts without ever leaking across user accounts.
 */
public class CoinBalanceRepository {

    private static final String TAG = "CoinBalanceRepository";
    private static final String PREFS_NAME = "tutor_earn_central_coin_ledger";
    private static final String KEY_PREFIX_BALANCE = "balance_";
    private static final String KEY_PREFIX_TX_IDS = "tx_ids_";
    private static final String KEY_PREFIX_MAC = "mac_";
    private static final String LOCAL_FALLBACK_UID = "__local_guest__";
    private static final String KEYSTORE_ALIAS = "tutor_earn_central_coins_hmac_v1";

    public interface BalanceListener {
        void onBalanceUpdated(long balance);
    }

    public interface CreditCallback {
        void onResult(boolean success, long updatedBalance, @NonNull String message);
    }

    @VisibleForTesting
    public interface RemoteBalanceObserver {
        void onBalanceChanged(long balance);
    }

    @VisibleForTesting
    public interface RemoteTransactionCallback {
        void onComplete(boolean success, long updatedBalance, @Nullable String error);
    }

    @VisibleForTesting
    public interface CoinDatabaseDriver {
        boolean isAuthenticated(@NonNull Context context);

        @Nullable
        String getAuthenticatedUid(@NonNull Context context);

        void observeBalance(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull RemoteBalanceObserver observer
        );

        void stopObserving(@NonNull String uid);

        void fetchBalanceOnce(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull RemoteBalanceObserver observer
        );

        void commitVerifiedTransaction(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull CoinEarningSource source,
                @NonNull String transactionId,
                int rewardCoins,
                @NonNull RemoteTransactionCallback callback
        );
    }

    @VisibleForTesting
    public static final class Holder {
        @Nullable
        private static CoinDatabaseDriver testDriverOverride;

        private Holder() {
        }

        @VisibleForTesting
        public static void setDriverOverride(@Nullable CoinDatabaseDriver driver) {
            testDriverOverride = driver;
            if (instance != null) {
                instance.resetListenerBindingsForTest();
            }
        }
    }

    public static boolean hasTestDriverOverride() {
        return Holder.testDriverOverride != null;
    }

    @Nullable
    private static CoinBalanceRepository instance;

    @NonNull
    public static synchronized CoinBalanceRepository getInstance(@NonNull Context context) {
        if (instance == null) {
            instance = new CoinBalanceRepository(context.getApplicationContext());
        }
        return instance;
    }

    @VisibleForTesting
    public static synchronized void resetForTest(@NonNull Context context) {
        if (instance != null) {
            instance.detachRemoteObserver();
        }
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit();
        instance = null;
    }

    private final Context appContext;
    private final SharedPreferences prefs;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<BalanceListener> listeners = new CopyOnWriteArraySet<>();
    private final CoinDatabaseDriver defaultFirebaseDriver = new FirebaseCoinDatabaseDriver();

    @Nullable
    private String observedRemoteUid;

    private CoinBalanceRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private CoinDatabaseDriver getDriver() {
        return Holder.testDriverOverride != null ? Holder.testDriverOverride : defaultFirebaseDriver;
    }

    @NonNull
    public String getActiveAccountKey() {
        CoinDatabaseDriver driver = getDriver();
        String uid = driver.getAuthenticatedUid(appContext);
        return (uid != null && !uid.trim().isEmpty()) ? uid.trim() : LOCAL_FALLBACK_UID;
    }

    public synchronized long getCurrentBalance() {
        return readVerifiedBalanceForAccount(getActiveAccountKey());
    }

    public void addListener(@NonNull BalanceListener listener) {
        listeners.add(listener);
        long current = getCurrentBalance();
        listener.onBalanceUpdated(current);
        ensureRemoteObserverAttached();
        refreshBalance();
    }

    public void removeListener(@NonNull BalanceListener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
            detachRemoteObserver();
        }
    }

    public void refreshBalance() {
        final String accountKey = getActiveAccountKey();
        notifyListeners(readVerifiedBalanceForAccount(accountKey));

        CoinDatabaseDriver driver = getDriver();
        if (!driver.isAuthenticated(appContext)) {
            return;
        }
        String uid = driver.getAuthenticatedUid(appContext);
        if (uid == null || uid.trim().isEmpty()) {
            return;
        }
        ensureRemoteObserverAttached();
        driver.fetchBalanceOnce(appContext, uid, remoteBalance -> {
            if (remoteBalance >= 0L) {
                applyAuthoritativeBalance(uid, remoteBalance);
            }
        });
    }

    public void creditVerifiedEarning(
            @NonNull CoinEarningSource source,
            @NonNull String transactionId,
            int rewardCoins,
            boolean rewardVerified,
            @NonNull CreditCallback callback
    ) {
        if (!rewardVerified || rewardCoins <= 0 || transactionId.trim().isEmpty()) {
            callback.onResult(false, getCurrentBalance(), "Reward verification failed.");
            return;
        }
        if (source == CoinEarningSource.PRIME_SURVEY
                && rewardCoins != PrimeSurveyRepository.PRIME_SURVEY_REWARD_COINS) {
            callback.onResult(false, getCurrentBalance(), "Invalid Prime Survey reward amount.");
            return;
        }
        if (source == CoinEarningSource.SURVEY_REWARDS
                && rewardCoins != SurveyRewardsActivity.REWARD_COINS) {
            callback.onResult(false, getCurrentBalance(), "Invalid Survey Rewards amount.");
            return;
        }

        final String txId = transactionId.trim();
        final String accountKey = getActiveAccountKey();
        if (hasProcessedTransaction(accountKey, txId)) {
            callback.onResult(false, readVerifiedBalanceForAccount(accountKey), "Duplicate reward transaction rejected.");
            return;
        }

        CoinDatabaseDriver driver = getDriver();
        if (driver.isAuthenticated(appContext)) {
            String uid = driver.getAuthenticatedUid(appContext);
            if (uid == null || uid.trim().isEmpty()) {
                callback.onResult(false, getCurrentBalance(), "Authentication required.");
                return;
            }
            driver.commitVerifiedTransaction(appContext, uid, source, txId, rewardCoins, (success, updatedBalance, error) -> {
                if (!success) {
                    callback.onResult(
                            false,
                            readVerifiedBalanceForAccount(uid),
                            error != null ? error : "Transaction rejected."
                    );
                    return;
                }
                recordRemoteCommittedReward(uid, txId, updatedBalance);
                callback.onResult(true, updatedBalance, "Coins added to central balance.");
            });
            return;
        }

        boolean committed = commitVerifiedLocalReward(source, txId, rewardCoins);
        long current = getCurrentBalance();
        if (committed) {
            callback.onResult(true, current, "Coins added to central balance.");
        } else {
            callback.onResult(false, current, "Duplicate reward transaction rejected.");
        }
    }

    synchronized void recordRemoteCommittedReward(
            @NonNull String uid,
            @NonNull String transactionId,
            long committedBalance
    ) {
        if (committedBalance < 0L) {
            return;
        }
        Set<String> txSet = readVerifiedTransactionSet(uid);
        txSet.add(transactionId);
        persistSignedAccountLedger(uid, committedBalance, txSet);
        if (uid.equals(getActiveAccountKey())) {
            notifyListeners(committedBalance);
        }
    }

    synchronized boolean commitVerifiedLocalReward(
            @NonNull CoinEarningSource source,
            @NonNull String transactionId,
            int rewardCoins
    ) {
        if (rewardCoins <= 0 || transactionId.trim().isEmpty()) {
            return false;
        }
        if (source == CoinEarningSource.PRIME_SURVEY
                && rewardCoins != PrimeSurveyRepository.PRIME_SURVEY_REWARD_COINS) {
            return false;
        }
        if (source == CoinEarningSource.SURVEY_REWARDS
                && rewardCoins != SurveyRewardsActivity.REWARD_COINS) {
            return false;
        }

        String accountKey = getActiveAccountKey();
        String txId = transactionId.trim();
        Set<String> txSet = readVerifiedTransactionSet(accountKey);
        if (txSet.contains(txId)) {
            return false;
        }

        long current = readVerifiedBalanceForAccount(accountKey);
        long updated = current + rewardCoins;
        txSet.add(txId);
        persistSignedAccountLedger(accountKey, updated, txSet);
        notifyListeners(updated);
        return true;
    }

    synchronized boolean hasProcessedTransaction(@NonNull String accountKey, @NonNull String transactionId) {
        return readVerifiedTransactionSet(accountKey).contains(transactionId);
    }

    synchronized void applyAuthoritativeBalance(@NonNull String uid, long remoteBalance) {
        if (remoteBalance < 0L) {
            return;
        }
        Set<String> txSet = readVerifiedTransactionSet(uid);
        long existing = readVerifiedBalanceForAccount(uid);
        if (existing != remoteBalance || !prefs.contains(KEY_PREFIX_BALANCE + uid)) {
            persistSignedAccountLedger(uid, remoteBalance, txSet);
        }
        if (uid.equals(getActiveAccountKey())) {
            notifyListeners(remoteBalance);
        }
    }

    private synchronized void ensureRemoteObserverAttached() {
        CoinDatabaseDriver driver = getDriver();
        if (!driver.isAuthenticated(appContext)) {
            detachRemoteObserver();
            return;
        }
        String uid = driver.getAuthenticatedUid(appContext);
        if (uid == null || uid.trim().isEmpty()) {
            detachRemoteObserver();
            return;
        }
        if (uid.equals(observedRemoteUid)) {
            return;
        }
        detachRemoteObserver();
        observedRemoteUid = uid;
        driver.observeBalance(appContext, uid, remoteBalance -> {
            if (remoteBalance >= 0L) {
                applyAuthoritativeBalance(uid, remoteBalance);
            }
        });
    }

    private synchronized void detachRemoteObserver() {
        if (observedRemoteUid != null) {
            getDriver().stopObserving(observedRemoteUid);
            observedRemoteUid = null;
        }
    }

    private synchronized void resetListenerBindingsForTest() {
        detachRemoteObserver();
        if (!listeners.isEmpty()) {
            ensureRemoteObserverAttached();
            notifyListeners(getCurrentBalance());
        }
    }

    private void notifyListeners(long balance) {
        Runnable dispatch = () -> {
            for (BalanceListener listener : listeners) {
                listener.onBalanceUpdated(balance);
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dispatch.run();
        } else {
            mainHandler.post(dispatch);
        }
    }

    @NonNull
    public static String formatCoins(@NonNull Context context, long balance) {
        long safeBalance = Math.max(0L, balance);
        String formattedNumber = NumberFormat.getNumberInstance(Locale.US).format(safeBalance);
        return context.getString(R.string.home_coins_amount_format, formattedNumber);
    }

    private long readVerifiedBalanceForAccount(@NonNull String accountKey) {
        if (!verifyIntegrityForAccount(accountKey)) {
            return 0L;
        }
        return Math.max(0L, prefs.getLong(KEY_PREFIX_BALANCE + accountKey, 0L));
    }

    @NonNull
    private Set<String> readVerifiedTransactionSet(@NonNull String accountKey) {
        if (!verifyIntegrityForAccount(accountKey)) {
            return new HashSet<>();
        }
        Set<String> stored = prefs.getStringSet(KEY_PREFIX_TX_IDS + accountKey, null);
        return stored != null ? new HashSet<>(stored) : new HashSet<>();
    }

    private void persistSignedAccountLedger(
            @NonNull String accountKey,
            long balance,
            @NonNull Set<String> txSet
    ) {
        String mac = computeIntegrityMac(accountKey, balance, txSet);
        prefs.edit()
                .putLong(KEY_PREFIX_BALANCE + accountKey, balance)
                .putStringSet(KEY_PREFIX_TX_IDS + accountKey, new HashSet<>(txSet))
                .putString(KEY_PREFIX_MAC + accountKey, mac)
                .commit();
    }

    private boolean verifyIntegrityForAccount(@NonNull String accountKey) {
        String balanceKey = KEY_PREFIX_BALANCE + accountKey;
        String txKey = KEY_PREFIX_TX_IDS + accountKey;
        if (!prefs.contains(balanceKey) && !prefs.contains(txKey)) {
            return true;
        }
        String storedMac = prefs.getString(KEY_PREFIX_MAC + accountKey, "");
        if (storedMac == null || storedMac.isEmpty()) {
            return false;
        }
        long balance = prefs.getLong(balanceKey, 0L);
        Set<String> txSet = prefs.getStringSet(txKey, Collections.emptySet());
        String expected = computeIntegrityMac(accountKey, balance, txSet != null ? txSet : Collections.emptySet());
        return constantTimeEquals(storedMac, expected);
    }

    @NonNull
    private String computeIntegrityMac(
            @NonNull String accountKey,
            long balance,
            @NonNull Set<String> txSet
    ) {
        List<String> sortedTx = new ArrayList<>(txSet);
        Collections.sort(sortedTx);
        StringBuilder sb = new StringBuilder();
        sb.append(accountKey).append('|').append(balance);
        for (String tx : sortedTx) {
            sb.append('|').append(tx);
        }
        String payload = sb.toString();
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
            String seed = appContext.getPackageName() + ":TutorEarnCentralCoinsIntegrityV1";
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

    private static final class FirebaseCoinDatabaseDriver implements CoinDatabaseDriver {

        @Nullable
        private DatabaseReference activeBalanceRef;
        @Nullable
        private ValueEventListener activeBalanceListener;

        @Override
        public boolean isAuthenticated(@NonNull Context context) {
            try {
                TutorEarnApplication.ensureFirebaseInitialized(context);
                if (FirebaseApp.getApps(context).isEmpty()) {
                    return false;
                }
                return FirebaseAuth.getInstance().getCurrentUser() != null;
            } catch (Throwable t) {
                return false;
            }
        }

        @Nullable
        @Override
        public String getAuthenticatedUid(@NonNull Context context) {
            try {
                TutorEarnApplication.ensureFirebaseInitialized(context);
                if (FirebaseApp.getApps(context).isEmpty()) {
                    return null;
                }
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                return user != null ? user.getUid() : null;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void observeBalance(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull RemoteBalanceObserver observer
        ) {
            stopObserving(uid);
            try {
                String dbUrl = context.getString(R.string.tutor_firebase_database_url);
                DatabaseReference balanceRef = FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid)
                        .child("coins")
                        .child("balance");

                ValueEventListener listener = new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue() instanceof Number) {
                            long val = Math.max(0L, ((Number) snapshot.getValue()).longValue());
                            observer.onBalanceChanged(val);
                        } else {
                            fetchLegacyBalanceFallback(context, uid, observer);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Log.w(TAG, "Realtime balance observer cancelled: " + error.getMessage());
                    }
                };
                activeBalanceRef = balanceRef;
                activeBalanceListener = listener;
                balanceRef.addValueEventListener(listener);
            } catch (Throwable t) {
                Log.w(TAG, "Unable to attach Firebase balance listener: " + t.getMessage());
            }
        }

        @Override
        public void stopObserving(@NonNull String uid) {
            if (activeBalanceRef != null && activeBalanceListener != null) {
                try {
                    activeBalanceRef.removeEventListener(activeBalanceListener);
                } catch (Throwable ignored) {
                }
            }
            activeBalanceRef = null;
            activeBalanceListener = null;
        }

        @Override
        public void fetchBalanceOnce(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull RemoteBalanceObserver observer
        ) {
            try {
                String dbUrl = context.getString(R.string.tutor_firebase_database_url);
                DatabaseReference balanceRef = FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid)
                        .child("coins")
                        .child("balance");
                balanceRef.get().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        return;
                    }
                    DataSnapshot snap = task.getResult();
                    if (snap != null && snap.exists() && snap.getValue() instanceof Number) {
                        observer.onBalanceChanged(Math.max(0L, ((Number) snap.getValue()).longValue()));
                    } else {
                        fetchLegacyBalanceFallback(context, uid, observer);
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "One-shot balance fetch failed: " + t.getMessage());
            }
        }

        private void fetchLegacyBalanceFallback(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull RemoteBalanceObserver observer
        ) {
            try {
                String dbUrl = context.getString(R.string.tutor_firebase_database_url);
                FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid)
                        .child("coinBalance")
                        .get()
                        .addOnCompleteListener(task -> {
                            if (task.isSuccessful() && task.getResult() != null
                                    && task.getResult().exists()
                                    && task.getResult().getValue() instanceof Number) {
                                observer.onBalanceChanged(
                                        Math.max(0L, ((Number) task.getResult().getValue()).longValue())
                                );
                            } else if (task.isSuccessful()) {
                                observer.onBalanceChanged(0L);
                            }
                        });
            } catch (Throwable ignored) {
            }
        }

        @Override
        public void commitVerifiedTransaction(
                @NonNull Context context,
                @NonNull String uid,
                @NonNull CoinEarningSource source,
                @NonNull String transactionId,
                int rewardCoins,
                @NonNull RemoteTransactionCallback callback
        ) {
            try {
                String dbUrl = context.getString(R.string.tutor_firebase_database_url);
                DatabaseReference userRef = FirebaseDatabase.getInstance(dbUrl)
                        .getReference("users")
                        .child(uid);

                userRef.runTransaction(new Transaction.Handler() {
                    @NonNull
                    @Override
                    public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                        if (currentData.child("coinTransactions").child(transactionId).getValue() != null) {
                            return Transaction.abort();
                        }

                        long currentBalance = 0L;
                        Object rawCentralBalance = currentData.child("coins").child("balance").getValue();
                        if (rawCentralBalance instanceof Number) {
                            currentBalance = ((Number) rawCentralBalance).longValue();
                        } else {
                            Object legacyBalance = currentData.child("coinBalance").getValue();
                            if (legacyBalance instanceof Number) {
                                currentBalance = ((Number) legacyBalance).longValue();
                            }
                        }

                        long newBalance = Math.max(0L, currentBalance) + rewardCoins;

                        Map<String, Object> txRecord = new HashMap<>();
                        txRecord.put("transactionId", transactionId);
                        txRecord.put("source", source.getId());
                        txRecord.put("amount", rewardCoins);
                        txRecord.put("verified", true);
                        txRecord.put("balanceAfter", newBalance);
                        txRecord.put("createdAt", ServerValue.TIMESTAMP);

                        currentData.child("coins").child("balance").setValue(newBalance);
                        currentData.child("coinTransactions").child(transactionId).setValue(txRecord);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(
                            @Nullable DatabaseError error,
                            boolean committed,
                            @Nullable DataSnapshot currentData
                    ) {
                        if (!committed || error != null) {
                            callback.onComplete(
                                    false,
                                    -1L,
                                    error != null ? error.getMessage() : "Duplicate or invalid coin transaction."
                            );
                            return;
                        }
                        long updatedBalance = 0L;
                        if (currentData != null) {
                            Object rawBal = currentData.child("coins").child("balance").getValue();
                            if (rawBal instanceof Number) {
                                updatedBalance = Math.max(0L, ((Number) rawBal).longValue());
                            }
                        }
                        callback.onComplete(true, updatedBalance, null);
                    }
                });
            } catch (Throwable t) {
                callback.onComplete(false, -1L, t.getMessage());
            }
        }
    }
}
