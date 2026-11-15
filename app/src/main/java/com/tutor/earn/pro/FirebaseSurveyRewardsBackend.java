package com.tutor.earn.pro;

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
 * Firebase Authentication + Realtime Database backend connector for Survey Rewards.
 * Manages per-user 24-hour reward cooldown under users/{uid}/surveyRewards/
 * and deposits verified 500-coin rewards into users/{uid}/coins/balance and coinTransactions/.
 */
public class FirebaseSurveyRewardsBackend {

    private static final String TAG = "FirebaseSurveyRewards";

    public interface RemoteEligibilityCallback {
        void onResult(boolean configuredAndSignedIn, long lastClaimTimestampMs, long totalClaims, long totalCoinsEarned, long serverNowMs, @Nullable String error);
    }

    public interface RemoteClaimCallback {
        void onComplete(boolean success, long committedServerTimestampMs, long committedBalance, @Nullable String error);
    }

    private final Context appContext;

    public FirebaseSurveyRewardsBackend(@NonNull Context context) {
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
            callback.onResult(false, 0L, 0L, 0L, -1L, null);
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

                DatabaseReference surveyRewardsRef = database.getReference("users")
                        .child(uid)
                        .child("surveyRewards");

                surveyRewardsRef.get().addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        String msg = task.getException() != null
                                ? task.getException().getMessage()
                                : "Unable to reach Firebase database.";
                        callback.onResult(true, 0L, 0L, 0L, -1L, msg);
                        return;
                    }
                    long lastClaimMs = 0L;
                    long totalClaims = 0L;
                    long totalCoins = 0L;
                    com.google.firebase.database.DataSnapshot snapshot = task.getResult();
                    if (snapshot != null && snapshot.exists()) {
                        Object rawLast = snapshot.child("lastClaimAt").getValue();
                        if (rawLast instanceof Number) {
                            lastClaimMs = ((Number) rawLast).longValue();
                        }
                        Object rawClaims = snapshot.child("totalClaims").getValue();
                        if (rawClaims instanceof Number) {
                            totalClaims = ((Number) rawClaims).longValue();
                        }
                        Object rawCoins = snapshot.child("totalCoinsEarned").getValue();
                        if (rawCoins instanceof Number) {
                            totalCoins = ((Number) rawCoins).longValue();
                        }
                    }
                    callback.onResult(true, lastClaimMs, totalClaims, totalCoins, estimatedServerNow, null);
                });
            });
        } catch (Throwable t) {
            Log.w(TAG, "Firebase survey rewards status check failed: " + t.getMessage());
            callback.onResult(true, 0L, 0L, 0L, -1L, t.getMessage());
        }
    }

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

            userRef.runTransaction(new Transaction.Handler() {
                @NonNull
                @Override
                public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                    if (currentData.child("coinTransactions").child(transactionId).getValue() != null) {
                        return Transaction.abort();
                    }
                    if (currentData.child("surveyRewards").child("claims").child(transactionId).getValue() != null) {
                        return Transaction.abort();
                    }

                    long lastClaim = 0L;
                    Object rawLastClaim = currentData.child("surveyRewards")
                            .child("lastClaimAt")
                            .getValue();
                    if (rawLastClaim instanceof Number) {
                        lastClaim = ((Number) rawLastClaim).longValue();
                    }

                    if (lastClaim > 0L && (estimatedServerNowMs - lastClaim) < cooldownDurationMs) {
                        return Transaction.abort();
                    }

                    long currentClaims = 0L;
                    Object rawClaims = currentData.child("surveyRewards").child("totalClaims").getValue();
                    if (rawClaims instanceof Number) {
                        currentClaims = ((Number) rawClaims).longValue();
                    }

                    long currentCoinsEarned = 0L;
                    Object rawCoinsEarned = currentData.child("surveyRewards").child("totalCoinsEarned").getValue();
                    if (rawCoinsEarned instanceof Number) {
                        currentCoinsEarned = ((Number) rawCoinsEarned).longValue();
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
                    long newClaims = currentClaims + 1L;
                    long newCoinsEarned = currentCoinsEarned + rewardCoins;

                    Map<String, Object> txRecord = new HashMap<>();
                    txRecord.put("transactionId", transactionId);
                    txRecord.put("source", CoinEarningSource.SURVEY_REWARDS.getId());
                    txRecord.put("amount", rewardCoins);
                    txRecord.put("verified", true);
                    txRecord.put("balanceAfter", newBalance);
                    txRecord.put("createdAt", ServerValue.TIMESTAMP);

                    Map<String, Object> claimNode = new HashMap<>();
                    claimNode.put("transactionId", transactionId);
                    claimNode.put("amount", rewardCoins);
                    claimNode.put("claimedAt", ServerValue.TIMESTAMP);

                    currentData.child("coins").child("balance").setValue(newBalance);
                    currentData.child("coinTransactions").child(transactionId).setValue(txRecord);

                    MutableData surveyNode = currentData.child("surveyRewards");
                    surveyNode.child("lastClaimAt").setValue(ServerValue.TIMESTAMP);
                    surveyNode.child("nextClaimAt").setValue(estimatedServerNowMs + cooldownDurationMs);
                    surveyNode.child("totalClaims").setValue(newClaims);
                    surveyNode.child("totalCoinsEarned").setValue(newCoinsEarned);
                    surveyNode.child("claims").child(transactionId).setValue(claimNode);

                    return Transaction.success(currentData);
                }

                @Override
                public void onComplete(
                        @Nullable DatabaseError error,
                        boolean committed,
                        @Nullable DataSnapshot snapshot
                ) {
                    if (!committed) {
                        String errMsg = error != null ? error.getMessage() : "Cooldown active or concurrent transaction rejected.";
                        callback.onComplete(false, -1L, -1L, errMsg);
                        return;
                    }

                    long committedServerTs = estimatedServerNowMs;
                    long committedBalance = -1L;
                    if (snapshot != null && snapshot.exists()) {
                        Object rawBal = snapshot.child("coins").child("balance").getValue();
                        if (rawBal instanceof Number) {
                            committedBalance = ((Number) rawBal).longValue();
                        }
                        Object rawTs = snapshot.child("surveyRewards").child("lastClaimAt").getValue();
                        if (rawTs instanceof Number) {
                            committedServerTs = ((Number) rawTs).longValue();
                            userRef.child("surveyRewards")
                                    .child("nextClaimAt")
                                    .setValue(committedServerTs + cooldownDurationMs);
                        }
                    }
                    callback.onComplete(true, committedServerTs, committedBalance, null);
                }
            });
        } catch (Throwable t) {
            callback.onComplete(false, -1L, -1L, t.getMessage() != null ? t.getMessage() : "Firebase transaction error.");
        }
    }
}
