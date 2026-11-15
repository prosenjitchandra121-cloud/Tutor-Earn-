package com.example;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

import java.util.HashMap;
import java.util.Map;

/**
 * Optional Firebase Authentication + Firebase Realtime Database backend connector.
 * Automatically activates ONLY when an existing Firebase configuration (google-services.json)
 * is present and a Firebase user is authenticated. Uses ServerValue.TIMESTAMP and atomic
 * transactions for 24-hour claim enforcement and immutable transaction records.
 */
public class FirebasePrimeSurveyBackend {

    private static final String TAG = "FirebasePrimeSurvey";

    public interface RemoteEligibilityCallback {
        void onResult(boolean configuredAndSignedIn, long lastClaimTimestampMs, long serverNowMs, @Nullable String error);
    }

    public interface RemoteClaimCallback {
        void onComplete(boolean success, long committedServerTimestampMs, long committedBalance, @Nullable String error);
    }

    private final Context appContext;

    public FirebasePrimeSurveyBackend(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        TutorEarnApplication.ensureFirebaseInitialized(this.appContext);
    }

    public boolean isConfiguredAndAuthenticated() {
        try {
            TutorEarnApplication.ensureFirebaseInitialized(appContext);
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                return false;
            }
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            return currentUser != null;
        } catch (Throwable t) {
            return false;
        }
    }

    @Nullable
    public String getAuthenticatedUid() {
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                return null;
            }
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            return currentUser != null ? currentUser.getUid() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public void fetchRemoteClaimStatus(@NonNull RemoteEligibilityCallback callback) {
        String uid = getAuthenticatedUid();
        if (uid == null) {
            callback.onResult(false, 0L, -1L, null);
            return;
        }

        try {
            String databaseUrl = appContext.getString(R.string.tutor_firebase_database_url);
            FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
            DatabaseReference offsetRef = database.getReference(".info/serverTimeOffset");
            offsetRef.get().addOnCompleteListener(offsetTask -> {
                long offsetMs = 0L;
                if (offsetTask.isSuccessful() && offsetTask.getResult() != null) {
                    Long rawOffset = offsetTask.getResult().getValue(Long.class);
                    if (rawOffset != null) {
                        offsetMs = rawOffset;
                    }
                }
                final long estimatedServerNow = System.currentTimeMillis() + offsetMs;

                DatabaseReference lastClaimRef = database.getReference("users")
                        .child(uid)
                        .child("primeSurvey")
                        .child("lastClaimTimestamp");

                lastClaimRef.get().addOnCompleteListener(claimTask -> {
                    if (!claimTask.isSuccessful()) {
                        String msg = claimTask.getException() != null
                                ? claimTask.getException().getMessage()
                                : "Unable to reach Firebase database.";
                        callback.onResult(true, 0L, -1L, msg);
                        return;
                    }
                    long lastClaimMs = 0L;
                    DataSnapshot snapshot = claimTask.getResult();
                    if (snapshot != null && snapshot.exists()) {
                        Object rawVal = snapshot.getValue();
                        if (rawVal instanceof Number) {
                            lastClaimMs = ((Number) rawVal).longValue();
                        }
                    }
                    callback.onResult(true, lastClaimMs, estimatedServerNow, null);
                });
            });
        } catch (Throwable t) {
            Log.w(TAG, "Firebase status check failed: " + t.getMessage());
            callback.onResult(true, 0L, -1L, t.getMessage());
        }
    }

    /**
     * Records a verified Prime Survey claim transaction and updates the centralized
     * users/{uid}/coins/balance and users/{uid}/coinTransactions/{transactionId} atomically
     * on Firebase Realtime Database using ServerValue.TIMESTAMP.
     */
    public void recordVerifiedRewardTransaction(
            @NonNull String transactionId,
            int rewardCoins,
            long cooldownDurationMs,
            long estimatedServerNowMs,
            @NonNull RemoteClaimCallback callback
    ) {
        String uid = getAuthenticatedUid();
        if (uid == null) {
            callback.onComplete(false, -1L, -1L, "No authenticated Firebase user.");
            return;
        }

        try {
            String databaseUrl = appContext.getString(R.string.tutor_firebase_database_url);
            FirebaseDatabase database = FirebaseDatabase.getInstance(databaseUrl);
            DatabaseReference userRef = database.getReference("users").child(uid);

            Map<String, Object> claimRecord = new HashMap<>();
            claimRecord.put("transactionId", transactionId);
            claimRecord.put("surveyType", CoinEarningSource.PRIME_SURVEY.getId());
            claimRecord.put("amount", rewardCoins);
            claimRecord.put("claimedAt", ServerValue.TIMESTAMP);

            userRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    if (currentData.child("coinTransactions").child(transactionId).getValue() != null) {
                        return Transaction.abort();
                    }
                    if (currentData.child("primeSurvey").child("claims").child(transactionId).getValue() != null) {
                        return Transaction.abort();
                    }

                    long lastClaim = 0L;
                    Object rawLastClaim = currentData.child("primeSurvey")
                            .child("lastClaimTimestamp")
                            .getValue();
                    if (rawLastClaim instanceof Number) {
                        lastClaim = ((Number) rawLastClaim).longValue();
                    }

                    if (lastClaim > 0L && (estimatedServerNowMs - lastClaim) < cooldownDurationMs) {
                        return Transaction.abort();
                    }

                    long currentBalance = 0L;
                    Object rawCentralBalance = currentData.child("coins").child("balance").getValue();
                    if (rawCentralBalance instanceof Number) {
                        currentBalance = ((Number) rawCentralBalance).longValue();
                    } else {
                        Object rawLegacyBalance = currentData.child("coinBalance").getValue();
                        if (rawLegacyBalance instanceof Number) {
                            currentBalance = ((Number) rawLegacyBalance).longValue();
                        }
                    }

                    long newBalance = Math.max(0L, currentBalance) + rewardCoins;

                    Map<String, Object> txRecord = new HashMap<>();
                    txRecord.put("transactionId", transactionId);
                    txRecord.put("source", CoinEarningSource.PRIME_SURVEY.getId());
                    txRecord.put("amount", rewardCoins);
                    txRecord.put("verified", true);
                    txRecord.put("balanceAfter", newBalance);
                    txRecord.put("createdAt", ServerValue.TIMESTAMP);

                    currentData.child("coins").child("balance").setValue(newBalance);
                    currentData.child("coinTransactions").child(transactionId).setValue(txRecord);
                    currentData.child("primeSurvey").child("lastClaimTimestamp").setValue(ServerValue.TIMESTAMP);
                    currentData.child("primeSurvey").child("claims").child(transactionId).setValue(claimRecord);
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
                                -1L,
                                error != null ? error.getMessage() : "Claim rejected by 24-hour rule."
                        );
                        return;
                    }
                    long committedTimestamp = estimatedServerNowMs;
                    long committedBalance = -1L;
                    if (currentData != null) {
                        Object rawTs = currentData.child("primeSurvey")
                                .child("lastClaimTimestamp")
                                .getValue();
                        if (rawTs instanceof Number) {
                            committedTimestamp = ((Number) rawTs).longValue();
                        }
                        Object rawBalance = currentData.child("coins")
                                .child("balance")
                                .getValue();
                        if (rawBalance instanceof Number) {
                            committedBalance = Math.max(0L, ((Number) rawBalance).longValue());
                        }
                    }
                    callback.onComplete(true, committedTimestamp, committedBalance, null);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Firebase transaction failed: " + t.getMessage());
            callback.onComplete(false, -1L, -1L, t.getMessage());
        }
    }
}
